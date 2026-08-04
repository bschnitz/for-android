package chat.stoat.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.composables.generic.ListHeader
import chat.stoat.instances.Instance
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstanceSettingsScreen(
    navController: NavController,
    viewModel: InstanceSettingsScreenViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val instances = viewModel.instances.collectAsState().value
    val selectedId = viewModel.selectedId.collectAsState().value

    viewModel.notice?.let { resolved ->
        LaunchedEffect(resolved) {
            Toast.makeText(
                context,
                context.getString(R.string.instances_editor_resolved, resolved),
                Toast.LENGTH_SHORT
            ).show()
            viewModel.noticeShown()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.instances_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
            )
        },
    ) { pv ->
        Box(Modifier.padding(pv)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = stringResource(R.string.instances_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ListHeader {
                    Text(stringResource(R.string.instances_title))
                }

                instances.forEachIndexed { index, instance ->
                    if (index > 0) Spacer(Modifier.height(2.dp))
                    InstanceRow(
                        instance = instance,
                        isActive = instance.id == selectedId,
                        first = index == 0,
                        last = index == instances.lastIndex,
                        onSwitch = { viewModel.requestSwitch(instance) },
                        onEdit = { viewModel.openEdit(instance) },
                        onRefresh = {
                            viewModel.refreshEndpoints(instance) { endpoints ->
                                Toast.makeText(
                                    context,
                                    if (endpoints != null) {
                                        context.getString(R.string.instances_refresh_done)
                                    } else {
                                        context.getString(R.string.service_health_alert_body_default)
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onDelete = { viewModel.requestDelete(instance) },
                    )
                }

                Spacer(Modifier.height(2.dp))

                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    headlineContent = { Text(stringResource(R.string.instances_add)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_add_24dp),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(MaterialTheme.shapes.large)
                        .testTag("instances_add")
                        .clickable { viewModel.openAdd() }
                )
            }
        }
    }

    viewModel.editor?.let { state ->
        InstanceEditorDialog(
            state = state,
            onAddressChange = viewModel::setEditorAddress,
            onLabelChange = viewModel::setEditorLabel,
            onDismiss = viewModel::closeEditor,
            onSubmit = viewModel::submitEditor,
        )
    }

    viewModel.pendingSwitch?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelSwitch,
            title = { Text(stringResource(R.string.instances_switch_title, target.label)) },
            text = { Text(stringResource(R.string.instances_switch_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSwitch(context) }) {
                    Text(stringResource(R.string.instances_switch_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelSwitch) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    viewModel.pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.instances_delete_title, target.label)) },
            text = { Text(stringResource(R.string.instances_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete(context) }) {
                    Text(stringResource(R.string.instances_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun InstanceRow(
    instance: Instance,
    isActive: Boolean,
    first: Boolean,
    last: Boolean,
    onSwitch: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        headlineContent = { Text(instance.label) },
        supportingContent = {
            val signedInAs = instance.session?.selfName
            Column {
                Text(instance.endpoints.apiBase)
                Text(
                    text = when {
                        signedInAs != null ->
                            stringResource(R.string.instances_signed_in, signedInAs)

                        instance.isSignedIn -> stringResource(R.string.instances_signed_in_unknown)
                        else -> stringResource(R.string.instances_signed_out)
                    }
                )
            }
        },
        leadingContent = {
            Icon(
                painter = painterResource(
                    if (isActive) R.drawable.ic_check_24dp else R.drawable.ic_cloud_24dp
                ),
                contentDescription = if (isActive) stringResource(R.string.instances_active) else null,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert_24dp),
                        contentDescription = stringResource(
                            R.string.instances_more_actions,
                            instance.label
                        )
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.instances_edit)) },
                        onClick = { menuOpen = false; onEdit() },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_edit_24dp),
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.instances_refresh)) },
                        onClick = { menuOpen = false; onRefresh() },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_cloud_24dp),
                                contentDescription = null
                            )
                        }
                    )
                    if (!instance.builtIn && !isActive) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.instances_delete)) },
                            onClick = { menuOpen = false; onDelete() },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete_24dp),
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(
                when {
                    first && last -> MaterialTheme.shapes.large
                    first -> MaterialTheme.shapes.extraSmall.copy(
                        topStart = MaterialTheme.shapes.large.topStart,
                        topEnd = MaterialTheme.shapes.large.topEnd
                    )

                    last -> MaterialTheme.shapes.extraSmall.copy(
                        bottomStart = MaterialTheme.shapes.large.bottomStart,
                        bottomEnd = MaterialTheme.shapes.large.bottomEnd
                    )

                    else -> MaterialTheme.shapes.extraSmall
                }
            )
            .testTag("instance_row_${instance.id}")
            .clickable(enabled = !isActive, onClick = onSwitch)
    )
}
