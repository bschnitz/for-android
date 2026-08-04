package chat.stoat.instances

import chat.stoat.api.StoatJson
import chat.stoat.api.buildUserAgent
import chat.stoat.api.routes.misc.Root
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import logcat.logcat
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import kotlin.time.Duration.Companion.seconds

/** Outcome of pointing [probeInstance] at a URL the user typed. */
sealed interface ProbeResult {
    /**
     * The URL (or one of its conventional variants) answered with a valid API root.
     *
     * @param apiBase the variant that actually worked — this is what gets stored, not the input.
     */
    data class Success(val apiBase: String, val root: Root) : ProbeResult

    data class Failure(val message: String) : ProbeResult
}

private val PROBE_CONNECT_TIMEOUT = 10.seconds
private val PROBE_REQUEST_TIMEOUT = 15.seconds

// These are top-level functions, so there is no receiver for logcat to derive a tag from.
private const val LOG_TAG = "InstanceProbe"

/**
 * A deliberately bare client, used instead of [chat.stoat.api.StoatHttp] for two reasons.
 *
 * `StoatHttp` attaches the active session token to every request that does not already carry one.
 * A probe goes to an address the user just typed, so borrowing that client would hand the token for
 * the instance we are signed in to over to a stranger's server.
 *
 * Its retry policy — five attempts with exponential backoff — is also wrong here. Retrying makes
 * sense against the API of an instance we trust; while checking whether an address answers at all
 * it only multiplies the wait before an unreachable host can be reported as unreachable.
 *
 * Built on first use: the surrounding functions are pure and get exercised without an Android
 * runtime, which constructing this client at class-load time would break.
 */
private val probeHttp: HttpClient by lazy {
    HttpClient(OkHttp) {
        install(ContentNegotiation) { json(StoatJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = PROBE_CONNECT_TIMEOUT.inWholeMilliseconds
            requestTimeoutMillis = PROBE_REQUEST_TIMEOUT.inWholeMilliseconds
        }
        defaultRequest { header("User-Agent", buildUserAgent()) }
    }
}

/**
 * Checks whether a URL speaks the Stoat/Revolt API and reports back what it found.
 *
 * Users tend to type the address they know — the web client, or the reverse-proxy root — rather
 * than the versioned API base. Instead of rejecting those, each conventional variant is tried in
 * turn and the one that answers is the one that gets stored.
 */
suspend fun probeInstance(input: String): ProbeResult {
    val candidates = candidateApiBases(input)
    if (candidates.isEmpty()) return ProbeResult.Failure("Enter a server address, e.g. chat.example.com")

    var lastError: String? = null

    for (candidate in candidates) {
        try {
            val root = probeHttp.get("$candidate/").body<Root>()
            logcat(tag = LOG_TAG) { "Instance probe succeeded at $candidate (revolt ${root.revolt})" }
            return ProbeResult.Success(candidate, root)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // Transport-level failures are about the host, not the path. Every remaining candidate
            // points at the same host and port, so trying them would just repeat the same wait.
            logcat(tag = LOG_TAG) { "Instance probe could not reach $candidate: ${e.message}" }
            return ProbeResult.Failure(describeFailure(e))
        } catch (e: Exception) {
            logcat(tag = LOG_TAG) { "Instance probe failed for $candidate: ${e.message}" }
            lastError = describeFailure(e)
        }
    }

    return ProbeResult.Failure(lastError ?: "Could not reach that server")
}

/** Builds the endpoint set for a probed instance, filling gaps from the API base's own origin. */
fun endpointsFrom(apiBase: String, root: Root): StoredEndpoints {
    val origin = originOf(apiBase) ?: apiBase
    val webApp = root.app.trimEnd('/').ifBlank { origin }

    return StoredEndpoints(
        apiBase = apiBase,
        websocket = root.ws.trimEnd('/'),
        // An instance with no file server configured reports a blank URL. Pointing at the origin
        // keeps URL building harmless instead of producing "/attachments/..." style paths.
        files = root.features.autumn.url.trimEnd('/').ifBlank { origin },
        proxy = root.features.january.url.trimEnd('/').ifBlank { origin },
        webApp = webApp,
        // Self-hosted instances have no short invite domain of their own, so invites are built
        // from the web client instead.
        invites = webApp,
    )
}

/** A sensible default label for a freshly added instance. */
fun defaultLabelFor(apiBase: String): String =
    runCatching { URI(apiBase).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: apiBase

/**
 * Turns a probe exception into something worth showing the user.
 *
 * The exception's own message is not usable here: a server that answers the root route with an HTML
 * page — a plain reverse-proxy misconfiguration, and the most common way this fails — produces a
 * deserialization error carrying the whole page as its message. The detail stays in logcat.
 */
private fun describeFailure(e: Exception): String = when (e) {
    is UnknownHostException -> "Could not find that server"
    is HttpRequestTimeoutException, is SocketTimeoutException -> "That server took too long to answer"
    is ConnectException -> "Could not connect to that server"
    is IOException -> "Could not reach that server"
    else -> "That address did not answer like a Stoat server"
}

internal fun candidateApiBases(input: String): List<String> {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isBlank()) return emptyList()

    val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    if (originOf(withScheme) == null) return emptyList()

    // Ordered most-specific first: if the user already gave us the API base, we should not end up
    // storing a longer variant that happens to also answer.
    return listOf(
        withScheme,
        "$withScheme/0.8",
        "$withScheme/api",
        "$withScheme/api/0.8",
    ).distinct()
}

private fun originOf(url: String): String? = runCatching {
    val uri = URI(url)
    val host = uri.host ?: return@runCatching null
    val scheme = uri.scheme ?: return@runCatching null
    if (uri.port == -1) "$scheme://$host" else "$scheme://$host:${uri.port}"
}.getOrNull()
