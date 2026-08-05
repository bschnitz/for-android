package chat.stoat.push

import android.content.Context
import chat.stoat.c2dm.postMessageNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Receives Web Push messages from whichever UnifiedPush distributor the user has.
 *
 * The connector calls these methods on the main thread inside a ten-second wake lock, so
 * anything that touches the network or decodes images is moved off it with [goAsync], which
 * keeps the receiver alive until the work is finished.
 */
class StoatMessagingReceiver : MessagingReceiver() {
    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        val keys = endpoint.pubKeySet ?: run {
            // Without keys the server could only send unencrypted pushes, which it does not do.
            logcat(LogPriority.ERROR) { "Endpoint for $instance has no key set, ignoring" }
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PushRegistrar.onEndpointReceived(context, endpoint.url, keys.pubKey, keys.auth)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to hand endpoint to the server: $e" }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        if (!message.decrypted) {
            logcat(LogPriority.ERROR) { "Received an undecrypted push for $instance, ignoring" }
            return
        }

        val alert = parseWebPushAlert(message.content.decodeToString()) ?: run {
            logcat(LogPriority.WARN) { "Push for $instance is not a message notification, ignoring" }
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.postMessageNotification(alert)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to post notification: $e" }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onRegistrationFailed(context: Context, reason: FailedReason, instance: String) {
        logcat(LogPriority.ERROR) { "Push registration failed for $instance: $reason" }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PushRegistrar.onRegistrationLost(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onUnregistered(context: Context, instance: String) {
        logcat(LogPriority.INFO) { "Distributor unregistered $instance" }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PushRegistrar.onRegistrationLost(context)
            } finally {
                pending.finish()
            }
        }
    }
}
