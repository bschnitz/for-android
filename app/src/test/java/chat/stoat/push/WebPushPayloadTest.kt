package chat.stoat.push

import chat.stoat.c2dm.MessageAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseWebPushAlertTest {
    @Test
    fun `reads a new-message notification`() {
        val alert = parseWebPushAlert(
            """
            {
              "author": "Alice",
              "icon": "https://files.example.com/avatars/01AAAA",
              "body": "see you at six",
              "tag": "01CHANNEL",
              "timestamp": 1700000000,
              "url": "/channel/01CHANNEL",
              "message": {
                "_id": "01MESSAGE",
                "channel": "01CHANNEL",
                "author": "01AUTHOR",
                "content": "see you at six"
              }
            }
            """.trimIndent()
        )

        assertEquals(
            MessageAlert(
                messageId = "01MESSAGE",
                channelId = "01CHANNEL",
                authorId = "01AUTHOR",
                authorName = "Alice",
                authorAvatarUrl = "https://files.example.com/avatars/01AAAA",
                body = "see you at six",
            ),
            alert,
        )
    }

    @Test
    fun `ignores fields it does not need`() {
        // The payload carries the whole message and channel objects; an unknown or extended field
        // must not cost a notification.
        val alert = parseWebPushAlert(
            """
            {
              "author": "Alice",
              "body": "hi",
              "image": "https://files.example.com/attachments/01FILE",
              "something_new": {"nested": [1, 2, 3]},
              "message": {"_id": "01MESSAGE", "channel": "01CHANNEL", "author": "01AUTHOR"}
            }
            """.trimIndent()
        )

        assertEquals("01MESSAGE", alert?.messageId)
        // `image` is a message attachment, not the author's avatar, so it must not end up as one.
        assertEquals("", alert?.authorAvatarUrl)
    }

    @Test
    fun `skips a notification that is not about a message`() {
        // Friend requests, calls and generic alerts arrive without a message object.
        assertNull(
            parseWebPushAlert("""{"author": "Alice", "body": "sent you a friend request"}""")
        )
    }

    @Test
    fun `skips a message without an id or channel`() {
        // Both are needed: the id for the timestamp, the channel to group and route the notification.
        assertNull(parseWebPushAlert("""{"body": "hi", "message": {"channel": "01CHANNEL"}}"""))
        assertNull(parseWebPushAlert("""{"body": "hi", "message": {"_id": "01MESSAGE"}}"""))
    }

    @Test
    fun `survives a payload that is not the expected shape`() {
        // A decrypted body is not necessarily one of ours, and a crash here would take down the
        // receiver rather than just lose one notification.
        assertNull(parseWebPushAlert(""))
        assertNull(parseWebPushAlert("not json at all"))
        assertNull(parseWebPushAlert("""{"message": "a string, not an object"}"""))
        assertNull(parseWebPushAlert("""{"author": 42}"""))
    }
}
