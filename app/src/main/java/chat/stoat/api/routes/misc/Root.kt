package chat.stoat.api.routes.misc

import chat.stoat.api.StoatHttp
import chat.stoat.api.api
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Defaults are deliberate: self-hosted deployments do not always populate every field (a missing
// vapid key or an unconfigured file server is normal), and the root route is what identifies an
// instance in the first place. Failing to parse it would make such a server look unreachable.

@Serializable
data class Root(
    val revolt: String,
    val features: Features,
    val ws: String,
    val app: String = "",
    val vapid: String = ""
)

@Serializable
data class Features(
    val captcha: CAPTCHAFeature = CAPTCHAFeature(),
    val email: Boolean = false,
    @SerialName("invite_only") val inviteOnly: Boolean = false,
    val autumn: AutumnJanuaryFeature = AutumnJanuaryFeature(),
    val january: AutumnJanuaryFeature = AutumnJanuaryFeature(),
    val voso: LegacyVoiceFeature? = null,
    val livekit: LiveKitFeature? = null,
)

@Serializable
data class AutumnJanuaryFeature(
    val enabled: Boolean = false,
    val url: String = ""
)

@Serializable
data class CAPTCHAFeature(
    val enabled: Boolean = false,
    val key: String = ""
)

@Serializable
data class LegacyVoiceFeature(
    val enabled: Boolean,
    val url: String,
    val ws: String
)

@Serializable
data class LiveKitFeature(
    val enabled: Boolean,
    val nodes: List<LiveKitNode>
)

@Serializable
data class LiveKitNode(
    val name: String,
    val lat: Double,
    val lon: Double,
    @SerialName("public_url") val publicUrl: String,
)

suspend fun getRootRoute(): Root {
    return StoatHttp.get("/".api()).body()
}
