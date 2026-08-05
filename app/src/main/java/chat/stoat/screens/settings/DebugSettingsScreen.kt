package chat.stoat.screens.settings

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.push.PushRegistrar
import org.unifiedpush.android.connector.UnifiedPush
import chat.stoat.api.settings.SyncedSettings
import chat.stoat.dialogs.NotificationRationaleDialog
import chat.stoat.persistence.Database
import chat.stoat.persistence.KVStorage
import chat.stoat.persistence.SqlStorage
import chat.stoat.ui.theme.FragmentMono
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class DebugSettingsScreenViewModel(
    private val kvStorage: KVStorage
) : ViewModel() {
    fun forgetAllSparks() {
        forgetPhysicalKeyboardSpark()
        forgetEarlyAccessSpark()
        forgetSwipeToReplySpark()
    }

    fun forgetPhysicalKeyboardSpark() {
        viewModelScope.launch {
            kvStorage.remove("spark/physicalKeyboard/dismissed")
        }
    }

    fun forgetEarlyAccessSpark() {
        viewModelScope.launch {
            kvStorage.remove("spark/earlyAccess/dismissed")
        }
    }

    fun forgetSwipeToReplySpark() {
        viewModelScope.launch {
            kvStorage.remove("spark/swipeToReply/dismissed")
        }
    }

    fun forgetLatestChangelog() {
        viewModelScope.launch {
            SyncedSettings.resetReleaseNotes()
        }
    }

    val serverQueries = Database(SqlStorage.driver).serverQueries.selectAll().executeAsList()
    val channelQueries = Database(SqlStorage.driver).channelQueries.selectAll().executeAsList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsScreen(
    navController: NavController,
    viewModel: DebugSettingsScreenViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showNotificationsRationaleDialogue by remember { mutableStateOf(false) }
    val askNotificationsPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                scope.launch {
                    runCatching { PushRegistrar.ensureRegistered(context) }.onFailure {
                        Log.e("DebugSettingsScreen", "Push registration failed", it)
                    }
                }
            }
        }
    var showC2dmDataDialogue by remember { mutableStateOf(false) }
    var pushEndpoint by remember { mutableStateOf("") }
    var distributors by remember { mutableStateOf(emptyList<String>()) }

    if (showNotificationsRationaleDialogue) {
        NotificationRationaleDialog(
            onSelected = { accepted ->
                if (accepted) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askNotificationsPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Toast.makeText(
                            context,
                            "Will not ask for permission on pre-Tiramisu devices!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDismiss = {
                showNotificationsRationaleDialogue = false
            }
        )
    }

    if (showC2dmDataDialogue) {
        AlertDialog(
            onDismissRequest = {
                showC2dmDataDialogue = false
            },
            title = { Text("Notification Properties") },
            text = {
                Column {
                    Text("Push endpoint", style = MaterialTheme.typography.headlineSmall)
                    SelectionContainer {
                        Text(pushEndpoint.ifEmpty { "not registered" }, fontFamily = FragmentMono)
                    }
                    Text(
                        "Available distributors",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(distributors.joinToString().ifEmpty { "none" })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showC2dmDataDialogue = false
                }) {
                    Text("OK")
                }
            })
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = "Debug",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
            )
        },
    ) { pv ->
        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = "Sparks",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    ElevatedButton(onClick = { viewModel.forgetPhysicalKeyboardSpark() }) {
                        Text("Forget physical keyboard spark")
                    }
                    ElevatedButton(onClick = { viewModel.forgetEarlyAccessSpark() }) {
                        Text("Forget early access spark")
                    }
                    ElevatedButton(onClick = { viewModel.forgetSwipeToReplySpark() }) {
                        Text("Forget swipe to reply spark")
                    }
                    Button(onClick = { viewModel.forgetAllSparks() }) {
                        Text("Forget all sparks")
                    }
                }

                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    ElevatedButton(onClick = { showNotificationsRationaleDialogue = true }) {
                        Text("Show notification rationale dialog")
                    }

                    ElevatedButton(onClick = {
                        distributors = UnifiedPush.getDistributors(context)
                        scope.launch {
                            pushEndpoint = KVStorage(context)
                                .get(PushRegistrar.KEY_ENDPOINT)
                                .orEmpty()
                            showC2dmDataDialogue = true
                        }
                    }) {
                        Text("Show Notification Properties")
                    }
                }

                Text(
                    text = "Release Notes (Gazette)",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    ElevatedButton(onClick = { viewModel.forgetLatestChangelog() }) {
                        Text("Mark latest changelog as unread")
                    }
                }

                Text(
                    text = "Database",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Text(
                    text = "Servers: ${viewModel.serverQueries.size}",
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(viewModel.serverQueries.size) { index ->
                        Text(
                            text = viewModel.serverQueries[index].toString(),
                            style = LocalTextStyle.current.copy(
                                fontFamily = FragmentMono
                            )
                        )
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    }
                }
                Text(
                    text = "Channels: ${viewModel.channelQueries.size}",
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(viewModel.channelQueries.size) { index ->
                        Text(
                            text = viewModel.channelQueries[index].toString(),
                            style = LocalTextStyle.current.copy(
                                fontFamily = FragmentMono
                            )
                        )
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    }
                }
            }
        }
    }
}
