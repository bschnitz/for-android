package chat.stoat.api.settings

/**
 * How much of a server or channel the user wants to be notified about.
 *
 * The instance only pushes messages that mention you unless a higher level is configured, so
 * [All] and [Participating] need a server that resolves these levels. Other clients write [All],
 * [Mention] and [Muted] as well, [Participating] is specific to this client.
 */
enum class NotificationLevel(val setting: String) {
    /** Every channel of the server */
    All("all"),

    /** Only the channels the user has already written in */
    Participating("participating"),

    /** Only messages mentioning the user; the default */
    Mention("mention"),

    /** Nothing at all */
    Muted("muted");

    companion object {
        /** Read a level from its stored representation, or null if it is not one we know */
        fun fromSetting(value: String?): NotificationLevel? = when (value) {
            // "none" is what other clients write for muting a whole server
            "none" -> Muted
            else -> entries.firstOrNull { it.setting == value }
        }
    }
}

object NotificationSettingsProvider {
    /**
     * The level configured for a channel, where an entry for the channel overrides the entry for
     * its server.
     */
    fun levelForChannel(channelId: String, serverId: String?): NotificationLevel {
        NotificationLevel.fromSetting(SyncedSettings.notifications.channel[channelId])
            ?.let { return it }

        if (serverId != null) {
            NotificationLevel.fromSetting(SyncedSettings.notifications.server[serverId])
                ?.let { return it }
        }

        return NotificationLevel.Mention
    }

    /** The level configured for a whole server */
    fun levelForServer(serverId: String): NotificationLevel =
        NotificationLevel.fromSetting(SyncedSettings.notifications.server[serverId])
            ?: NotificationLevel.Mention

    fun isChannelMuted(channelId: String, serverId: String?): Boolean =
        levelForChannel(channelId, serverId) == NotificationLevel.Muted

    /** Store the level for a whole server and sync it to the instance */
    suspend fun setLevelForServer(serverId: String, level: NotificationLevel) {
        val current = SyncedSettings.notifications
        SyncedSettings.updateNotifications(
            current.copy(server = current.server + (serverId to level.setting))
        )
    }
}
