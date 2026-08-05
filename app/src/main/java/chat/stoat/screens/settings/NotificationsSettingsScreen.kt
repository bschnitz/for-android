package chat.stoat.screens.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.settings.NotificationLevel
import chat.stoat.api.settings.NotificationSettingsProvider
import chat.stoat.api.settings.SyncedSettings
import chat.stoat.core.model.schemas.Server
import chat.stoat.push.PushRegistrar
import chat.stoat.composables.generic.CenteredListItem
import chat.stoat.dialogs.NotificationLevelDialog
import chat.stoat.dialogs.NotificationRationaleDialog
import chat.stoat.dialogs.notificationLevelLabel
import chat.stoat.persistence.KVStorage
import chat.stoat.settings.dsl.SettingsPage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@SuppressLint("StaticFieldLeak")
class NotificationsSettingsScreenViewModel(
    private val kvStorage: KVStorage,
    private val context: Context
) : ViewModel() {
    var showRationale by mutableStateOf(false)
    var isPushEnabled by mutableStateOf(false)
        private set
    var isUpdating by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            isPushEnabled = checkPushEnabled()
        }
    }

    private suspend fun checkPushEnabled(): Boolean {
        val hasPermission = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return hasPermission &&
            PushRegistrar.hasDistributor(context) &&
            PushRegistrar.isRegistered(context)
    }

    fun onEnableRequested() {
        showRationale = true
    }

    /**
     * Registers for push. The endpoint arrives asynchronously in the messaging receiver, so the
     * enabled state is re-read rather than assumed.
     */
    fun subscribeIfNeeded() {
        if (isUpdating) return
        isUpdating = true
        viewModelScope.launch {
            try {
                if (PushRegistrar.ensureRegistered(context)) {
                    kvStorage.remove("pushNotificationsRejected")
                    isPushEnabled = checkPushEnabled()
                }
            } catch (e: Exception) {
                // registration failed, leave state unchanged
            } finally {
                isUpdating = false
            }
        }
    }

    fun disablePush() {
        if (isUpdating) return
        isUpdating = true
        viewModelScope.launch {
            try {
                PushRegistrar.unregister(context)
                kvStorage.set("pushNotificationsRejected", true)
                isPushEnabled = false
            } finally {
                isUpdating = false
            }
        }
    }

    /**
     * The servers the user is in, in the order the drawer shows them: the ones they arranged
     * first, then the rest by creation order.
     */
    fun serverList(): List<Server> {
        val arranged = SyncedSettings.ordering.servers

        return StoatAPI.serverCache.values
            .filter { arranged.contains(it.id) }
            .sortedBy { arranged.indexOf(it.id) } +
            StoatAPI.serverCache.values
                .filter { !arranged.contains(it.id) }
                .sortedBy { it.id }
    }

    fun setServerLevel(serverId: String, level: NotificationLevel) {
        viewModelScope.launch {
            NotificationSettingsProvider.setLevelForServer(serverId, level)
        }
    }
}

@Composable
fun NotificationsSettingsScreen(
    navController: NavController,
    viewModel: NotificationsSettingsScreenViewModel = koinViewModel()
) {
    val context = LocalContext.current

    // The server whose notification level is being picked, if any
    var levelTarget by remember { mutableStateOf<String?>(null) }

    levelTarget?.let { serverId ->
        NotificationLevelDialog(
            serverName = StoatAPI.serverCache[serverId]?.name ?: serverId,
            selected = NotificationSettingsProvider.levelForServer(serverId),
            onSelected = { viewModel.setServerLevel(serverId, it) },
            onDismiss = { levelTarget = null }
        )
    }

    val askNotificationsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.subscribeIfNeeded()
    }

    if (viewModel.showRationale) {
        NotificationRationaleDialog(
            onSelected = { accepted ->
                if (accepted) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askNotificationsPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.subscribeIfNeeded()
                    }
                }
            },
            onDismiss = { viewModel.showRationale = false }
        )
    }

    SettingsPage(
        navController = navController,
        title = { Text(stringResource(R.string.settings_notifications)) }
    ) {
        CenteredListItem(
            headlineContent = { Text(stringResource(R.string.settings_notifications_push)) },
            supportingContent = { Text(stringResource(R.string.settings_notifications_push_description)) },
            trailingContent = {
                Switch(
                    checked = viewModel.isPushEnabled,
                    onCheckedChange = null,
                    enabled = !viewModel.isUpdating
                )
            },
            modifier = Modifier
                .semantics { role = Role.Switch }
                .clickable(enabled = !viewModel.isUpdating) {
                    if (viewModel.isPushEnabled) viewModel.disablePush()
                    else viewModel.onEnableRequested()
                }
        )
        CenteredListItem(
            headlineContent = { Text(stringResource(R.string.settings_notifications_system)) },
            supportingContent = { Text(stringResource(R.string.settings_notifications_system_description)) },
            trailingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward_24dp),
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            },
            modifier = Modifier.clickable {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                context.startActivity(intent)
            }
        )

        val servers = viewModel.serverList()

        if (servers.isNotEmpty()) {
            Subcategory(
                title = { Text(stringResource(R.string.settings_notifications_servers)) }
            ) {
                Text(
                    text = stringResource(R.string.settings_notifications_servers_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                for (server in servers) {
                    val serverId = server.id ?: continue
                    val level = NotificationSettingsProvider.levelForServer(serverId)

                    CenteredListItem(
                        headlineContent = { Text(server.name ?: serverId) },
                        supportingContent = { Text(notificationLevelLabel(level)) },
                        modifier = Modifier.clickable { levelTarget = serverId }
                    )
                }
            }
        }
    }
}
