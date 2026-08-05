package chat.stoat.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import chat.stoat.R
import chat.stoat.api.settings.NotificationLevel
import chat.stoat.composables.generic.RadioItem

/** Label and description shown for a notification level */
private fun labelsFor(level: NotificationLevel): Pair<Int, Int> = when (level) {
    NotificationLevel.All ->
        R.string.settings_notifications_level_all to
            R.string.settings_notifications_level_all_description

    NotificationLevel.Participating ->
        R.string.settings_notifications_level_participating to
            R.string.settings_notifications_level_participating_description

    NotificationLevel.Mention ->
        R.string.settings_notifications_level_mention to
            R.string.settings_notifications_level_mention_description

    NotificationLevel.Muted ->
        R.string.settings_notifications_level_muted to
            R.string.settings_notifications_level_muted_description
}

/** The label of a notification level, for use outside this dialogue */
@Composable
fun notificationLevelLabel(level: NotificationLevel): String =
    stringResource(labelsFor(level).first)

@Composable
fun NotificationLevelDialog(
    serverName: String,
    selected: NotificationLevel,
    onSelected: (NotificationLevel) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.settings_notifications_level_title, serverName))
        },
        text = {
            Column(Modifier.selectableGroup()) {
                for (level in NotificationLevel.entries) {
                    val (label, description) = labelsFor(level)

                    RadioItem(
                        selected = level == selected,
                        onClick = {
                            onSelected(level)
                            onDismiss()
                        },
                        label = { Text(stringResource(label)) },
                        description = { Text(stringResource(description)) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
