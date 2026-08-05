package chat.stoat.c2dm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import chat.stoat.BuildConfig
import chat.stoat.R
import chat.stoat.activities.MainActivity
import chat.stoat.api.internals.ULID
import chat.stoat.api.routes.channel.fetchSingleChannel
import chat.stoat.c2dm.ChannelRegistrator.Companion.CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.persistence.Database
import chat.stoat.persistence.KVStorage
import chat.stoat.persistence.SqlStorage
import com.bumptech.glide.Glide
import kotlinx.coroutines.runBlocking
import kotlin.math.abs

/**
 * A new message to notify about, independent of how the push reached the device.
 *
 * Both transports carry the same information under different names, so they are normalised
 * into this shape before anything is drawn.
 */
data class MessageAlert(
    val messageId: String,
    val channelId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String,
    val body: String,
)

object NotificationID {
    const val NEW_MESSAGE = 0
}

private val LETTER_ICON_COLORS = intArrayOf(
    0xFF1565C0.toInt(),
    0xFF2E7D32.toInt(),
    0xFF6A1B9A.toInt(),
    0xFFC62828.toInt(),
    0xFF00838F.toInt(),
    0xFFE65100.toInt(),
    0xFF4527A0.toInt(),
    0xFF283593.toInt(),
)

private fun generateLetterBitmap(name: String, sizePx: Int = 256): Bitmap {
    val letter = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val color = LETTER_ICON_COLORS[abs(name.hashCode()) % LETTER_ICON_COLORS.size]

    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = color
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

    paint.color = Color.WHITE
    paint.textSize = sizePx * 0.45f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    val bounds = Rect()
    paint.getTextBounds(letter, 0, 1, bounds)
    canvas.drawText(letter, sizePx / 2f, sizePx / 2f + bounds.height() / 2f - bounds.bottom, paint)

    return bitmap
}

/** Blocking image load; callers are already off the main thread. */
private fun Context.loadBitmap(url: String): Bitmap = Glide.with(this)
    .asBitmap()
    .load(url)
    .circleCrop()
    .submit()
    .get()

private fun Context.loadBitmapOr(url: String?, fallbackName: String): Bitmap =
    url?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { loadBitmap(it) }.getOrNull() }
        ?: generateLetterBitmap(fallbackName.ifEmpty { "?" })

/**
 * Human-readable name for the conversation, prefixed with the server name where there is one.
 *
 * Falls back to fetching the channel when it is not in the local database yet, and to the
 * author's name when even that fails — a notification with a plausible title beats none.
 */
private fun resolveChannelName(db: Database, alert: MessageAlert): String {
    fun format(type: String, name: String?, serverId: String?): String {
        val base = when (type) {
            "DirectMessage" -> return alert.authorName
            "TextChannel" -> "#${name}"
            else -> name ?: return alert.authorName
        }
        val prefix = serverId?.let {
            db.serverQueries.findById(it).executeAsOneOrNull()?.name
        } ?: return base
        return "$prefix · $base"
    }

    db.channelQueries.findById(alert.channelId).executeAsOneOrNull()?.let {
        return format(it.channelType, it.name, it.server)
    }

    return runBlocking {
        runCatching { fetchSingleChannel(alert.channelId) }.getOrNull()?.let {
            format(it.channelType?.value ?: "", it.name, it.server)
        } ?: alert.authorName
    }
}

/**
 * Icon representing the conversation as a whole: the server icon for server channels, the group
 * icon for groups, and the author's avatar for direct messages.
 */
private fun Context.conversationIcon(
    db: Database,
    channelType: String?,
    channelId: String,
    channelName: String,
    authorAvatar: Bitmap,
): Bitmap {
    val channel = db.channelQueries.findById(channelId).executeAsOneOrNull()
    return when (channelType) {
        "TextChannel", "VoiceChannel" -> {
            val server = channel?.server?.let { db.serverQueries.findById(it).executeAsOneOrNull() }
            loadBitmapOr(server?.iconId?.let { "$STOAT_FILES/icons/$it" }, server?.name ?: channelName)
        }

        "Group" -> loadBitmapOr(
            channel?.iconId?.let { "$STOAT_FILES/icons/$it" },
            channel?.name ?: channelName
        )

        else -> authorAvatar
    }
}

/** The signed-in user, as the notification's own participant. */
private fun Context.selfPerson(): Person {
    val kv = KVStorage(this)
    val selfId = runBlocking { kv.get("selfId") }.orEmpty()
    val selfName = runBlocking { kv.get("selfName") }.orEmpty()
    val selfAvatarUrl = runBlocking { kv.get("selfAvatarUrl") }

    return Person.Builder()
        .setBot(false)
        .setKey(selfId.ifEmpty { "self" })
        .setIcon(IconCompat.createWithBitmap(loadBitmapOr(selfAvatarUrl, selfName)))
        .setName(selfName.ifEmpty { "Me" })
        .build()
}

/**
 * Posts a conversation notification for [alert], extending the existing one for the same channel
 * so a conversation stays a single grouped notification.
 *
 * Blocking: loads images and reads the database. Call it off the main thread.
 */
fun Context.postMessageNotification(alert: MessageAlert) {
    val db = Database(SqlStorage.driver)
    val channelName = resolveChannelName(db, alert)
    val channelType = db.channelQueries.findById(alert.channelId).executeAsOneOrNull()?.channelType

    val authorAvatar = loadBitmapOr(alert.authorAvatarUrl, alert.authorName)
    val author = Person.Builder()
        .setBot(false)
        .setKey(alert.authorId)
        .setIcon(IconCompat.createWithBitmap(authorAvatar))
        .setName(alert.authorName)
        .build()

    val conversationIcon =
        conversationIcon(db, channelType, alert.channelId, channelName, authorAvatar)

    val conversationIntent = Intent(this, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra("channelId", alert.channelId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    val shortcutId = "${BuildConfig.APPLICATION_ID}.channel.${alert.channelId}"
    ShortcutManagerCompat.pushDynamicShortcut(
        this,
        ShortcutInfoCompat.Builder(this, shortcutId)
            .setShortLabel(channelName)
            .setLongLabel(channelName)
            .setIcon(IconCompat.createWithBitmap(conversationIcon))
            .setIntent(conversationIntent)
            .setLongLived(true)
            .setPerson(author)
            .build()
    )

    val builder = NotificationCompat.Builder(this, CHANNEL_ID_GROUP_CONVERSATIONS_MESSAGES)
        .setSmallIcon(R.drawable.ic_stoat_24dp)
        .setContentTitle(alert.authorName)
        .setContentText(alert.body)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                alert.channelId.hashCode(),
                conversationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setStyle(messagingStyle(alert, channelName, channelType, author))
        .addAction(replyAction(alert))
        .addAction(markAsReadAction(alert))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)

    // Android 11 bubbles
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setShortcutId(shortcutId)
        builder.setLocusId(LocusIdCompat(shortcutId))

        val bubbleIntent = PendingIntent.getActivity(
            this,
            alert.channelId.hashCode(),
            conversationIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        builder.setBubbleMetadata(
            NotificationCompat.BubbleMetadata.Builder(
                bubbleIntent,
                IconCompat.createWithBitmap(conversationIcon)
            )
                .setDesiredHeight(600)
                .setAutoExpandBubble(false)
                .setSuppressNotification(false)
                .build()
        )
    }

    if (ActivityCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    NotificationManagerCompat.from(this)
        .notify(alert.channelId, NotificationID.NEW_MESSAGE, builder.build())
}

private fun Context.messagingStyle(
    alert: MessageAlert,
    channelName: String,
    channelType: String?,
    author: Person,
): NotificationCompat.MessagingStyle {
    val existing = NotificationManagerCompat.from(this)
        .activeNotifications
        .firstOrNull { it.tag == alert.channelId && it.id == NotificationID.NEW_MESSAGE }
        ?.notification
        ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }

    return (existing ?: NotificationCompat.MessagingStyle(selfPerson()))
        .setGroupConversation(channelType != "DirectMessage")
        .setConversationTitle(channelName)
        .addMessage(alert.body, ULID.asTimestamp(alert.messageId), author)
}

private fun Context.replyAction(alert: MessageAlert): NotificationCompat.Action {
    val remoteInput = RemoteInput.Builder("content")
        .setLabel(getString(R.string.message_context_sheet_actions_reply))
        .build()

    val intent = Intent(this, ReplyReceiver::class.java).apply {
        putExtra("channelId", alert.channelId)
    }

    return NotificationCompat.Action.Builder(
        R.drawable.ic_reply_24dp,
        getString(R.string.message_context_sheet_actions_reply),
        PendingIntent.getBroadcast(
            this,
            alert.channelId.hashCode(),
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    )
        .addRemoteInput(remoteInput)
        .build()
}

private fun Context.markAsReadAction(alert: MessageAlert): NotificationCompat.Action {
    val intent = Intent(this, MarkAsReadReceiver::class.java).apply {
        putExtra("channelId", alert.channelId)
        putExtra("messageId", alert.messageId)
    }

    return NotificationCompat.Action.Builder(
        R.drawable.ic_mark_chat_read_24dp,
        getString(R.string.channel_context_sheet_actions_mark_read),
        PendingIntent.getBroadcast(
            this,
            alert.channelId.hashCode() xor 1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    )
        .build()
}
