package chat.stoat.push

import android.content.Context
import chat.stoat.api.routes.misc.getRootRoute
import chat.stoat.api.routes.push.subscribePush
import chat.stoat.api.routes.push.unsubscribePush
import chat.stoat.persistence.KVStorage
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import logcat.logcat
import org.unifiedpush.android.connector.UnifiedPush
import kotlin.coroutines.resume

/**
 * Drives the Web Push registration for the active instance.
 *
 * The instance's VAPID public key is what the device registers with: the distributor passes it on
 * as the sender, so the push service only accepts messages signed with the matching private key.
 * That is what makes a self-hosted server able to push at all — it needs no credentials of the
 * distributor's own, only its own key pair.
 */
object PushRegistrar {
    /** Endpoint currently handed to the server; absent when push is off. */
    const val KEY_ENDPOINT = "pushEndpoint"

    /** A VAPID public key is 65 raw bytes in base64url without padding. */
    private const val VAPID_KEY_LENGTH = 87

    class MissingVapidKeyException : Exception("instance does not advertise a VAPID key")

    /**
     * Asks the distributor for an endpoint for the active instance. The endpoint arrives
     * asynchronously in [StoatMessagingReceiver.onNewEndpoint], not from this call.
     *
     * @throws MissingVapidKeyException when the instance cannot do Web Push, so callers can say
     * so instead of leaving the user waiting for a notification that will never come.
     */
    suspend fun register(context: Context) {
        val vapid = getRootRoute().vapid
        if (vapid.length != VAPID_KEY_LENGTH) {
            logcat(LogPriority.ERROR) { "Instance advertises a ${vapid.length}-char VAPID key" }
            throw MissingVapidKeyException()
        }

        logcat { "Asking the distributor for an endpoint" }
        UnifiedPush.register(context, vapid = vapid)
    }

    /** Whether a distributor has been picked and accepted. */
    fun hasDistributor(context: Context): Boolean =
        UnifiedPush.getAckDistributor(context) != null

    /**
     * Makes sure this device is registered for push, picking a distributor first if none has been
     * settled on yet.
     *
     * Safe to call on every start: the distributor answers a repeated registration with the
     * endpoint it already issued, so nothing is churned.
     *
     * @return whether a distributor was available at all.
     */
    suspend fun ensureRegistered(context: Context): Boolean {
        if (!chooseDistributor(context)) {
            logcat(LogPriority.WARN) { "No UnifiedPush distributor available" }
            return false
        }
        register(context)
        return true
    }

    /**
     * Settles on a distributor: the one already in use where there is one, otherwise the default.
     *
     * Bridges the connector's callback into the coroutine world, because registration has to wait
     * for the answer.
     */
    private suspend fun chooseDistributor(context: Context): Boolean =
        suspendCancellableCoroutine { continuation ->
            UnifiedPush.tryUseCurrentOrDefaultDistributor(context) { chosen ->
                if (continuation.isActive) continuation.resume(chosen)
            }
        }

    /** Hands a freshly issued endpoint to the server and remembers it. */
    suspend fun onEndpointReceived(
        context: Context,
        url: String,
        p256dh: String,
        auth: String,
    ) {
        subscribePush(endpoint = url, p256diffieHellman = p256dh, auth = auth)
        KVStorage(context).set(KEY_ENDPOINT, url)
        logcat { "Endpoint accepted by the instance" }
    }

    /** The registration is gone; forget the endpoint so the UI stops claiming push is on. */
    suspend fun onRegistrationLost(context: Context) {
        KVStorage(context).remove(KEY_ENDPOINT)
    }

    /**
     * Turns push off: tells the server first, because after unregistering the distributor the
     * endpoint is no longer ours to withdraw.
     */
    suspend fun unregister(context: Context) {
        runCatching { unsubscribePush() }
            .onFailure { logcat(LogPriority.WARN) { "Could not unsubscribe on the server: $it" } }
        UnifiedPush.unregister(context)
        KVStorage(context).remove(KEY_ENDPOINT)
    }

    /** Whether the server currently holds an endpoint for this device. */
    suspend fun isRegistered(context: Context): Boolean =
        KVStorage(context).get(KEY_ENDPOINT) != null
}
