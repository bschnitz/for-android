package chat.stoat.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.composables.generic.FormTextField

/** Add or edit a single instance. The address is validated against the API root on save. */
@Composable
fun InstanceEditorDialog(
    state: InstanceEditorState,
    onAddressChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (state.isEditing) R.string.instances_editor_edit_title
                    else R.string.instances_editor_add_title
                )
            )
        },
        text = {
            Column {
                FormTextField(
                    value = state.address,
                    label = stringResource(R.string.instances_editor_address),
                    onChange = onAddressChange,
                    type = KeyboardType.Uri,
                    action = ImeAction.Next,
                    enabled = !state.busy,
                    supportingText = {
                        Text(stringResource(R.string.instances_editor_address_hint))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("instance_editor_address")
                )

                Spacer(Modifier.height(8.dp))

                FormTextField(
                    value = state.label,
                    label = stringResource(R.string.instances_editor_label),
                    onChange = onLabelChange,
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("instance_editor_label")
                )

                if (state.busy) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.instances_editor_checking),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                state.error?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = !state.busy && state.address.isNotBlank(),
                modifier = Modifier.testTag("instance_editor_save")
            ) {
                Text(stringResource(R.string.instances_editor_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
