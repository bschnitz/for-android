package chat.stoat.push

import chat.stoat.c2dm.MessageAlert
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The decrypted body of a Web Push message.
 *
 * The server serialises a different shape per notification kind and does not tag them, so the
 * kind has to be inferred: only a new-message notification carries [message]. Friend requests,
 * calls and generic alerts arrive as a bare body, which this client does not display yet.
 *
 * Every field is optional so that an unexpected payload is skipped rather than crashing the
 * receiver, and unknown fields are ignored because the payload also carries the full message
 * and channel objects that are not needed here.
 */
@Serializable
data class WebPushPayload(
    val author: String = "",
    val icon: String = "",
    val body: String = "",
    val message: WebPushMessage? = null,
)

@Serializable
data class WebPushMessage(
    @SerialName("_id") val id: String = "",
    val channel: String = "",
    val author: String = "",
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Reads a decrypted Web Push body, or returns `null` when it is not a new-message notification
 * this client can display.
 */
fun parseWebPushAlert(payload: String): MessageAlert? {
    val parsed = runCatching { json.decodeFromString<WebPushPayload>(payload) }.getOrNull()
        ?: return null
    val message = parsed.message ?: return null

    if (message.id.isEmpty() || message.channel.isEmpty()) return null

    return MessageAlert(
        messageId = message.id,
        channelId = message.channel,
        authorId = message.author,
        authorName = parsed.author,
        // The server calls the author's avatar `icon`; `image` is a message attachment.
        authorAvatarUrl = parsed.icon,
        body = parsed.body,
    )
}
