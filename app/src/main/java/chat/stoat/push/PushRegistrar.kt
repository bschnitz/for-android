package chat.stoat.push

import android.app.Activity
import android.content.Context
import chat.stoat.api.routes.misc.getRootRoute
import chat.stoat.api.routes.push.subscribePush
import chat.stoat.api.routes.push.unsubscribePush
import chat.stoat.persistence.KVStorage
import logcat.LogPriority
import logcat.logcat
import org.unifiedpush.android.connector.UnifiedPush

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

        UnifiedPush.register(context, vapid = vapid)
    }

    /** Whether a distributor has been picked and accepted. */
    fun hasDistributor(context: Context): Boolean =
        UnifiedPush.getAckDistributor(context) != null

    /**
     * Picks a distributor without asking the user where there is only one sensible choice, and
     * reports back whether one was settled on.
     */
    fun chooseDistributor(activity: Activity, onResult: (Boolean) -> Unit) {
        if (hasDistributor(activity)) {
            onResult(true)
            return
        }
        UnifiedPush.tryUseDefaultDistributor(activity, onResult)
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
