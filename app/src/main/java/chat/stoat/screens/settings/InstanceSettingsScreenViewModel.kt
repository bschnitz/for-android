package chat.stoat.screens.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chat.stoat.instances.Instance
import chat.stoat.instances.InstanceStore
import chat.stoat.instances.InstanceSwitcher
import chat.stoat.instances.ProbeResult
import chat.stoat.instances.StoredEndpoints
import chat.stoat.instances.defaultLabelFor
import chat.stoat.instances.endpointsFrom
import chat.stoat.instances.probeInstance
import kotlinx.coroutines.launch

/** State of the add/edit sheet. `instanceId == null` means we are adding a new instance. */
data class InstanceEditorState(
    val instanceId: String? = null,
    val address: String = "",
    val label: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    val isEditing: Boolean get() = instanceId != null
}

class InstanceSettingsScreenViewModel(
    private val store: InstanceStore,
    private val switcher: InstanceSwitcher,
) : ViewModel() {
    val instances = store.instances
    val selectedId = store.selectedId

    var editor by mutableStateOf<InstanceEditorState?>(null)
        private set

    var pendingDelete by mutableStateOf<Instance?>(null)
        private set

    var pendingSwitch by mutableStateOf<Instance?>(null)
        private set

    /** One-shot message for the caller to surface, e.g. as a toast. */
    var notice by mutableStateOf<String?>(null)
        private set

    fun openAdd() {
        editor = InstanceEditorState()
    }

    fun openEdit(instance: Instance) {
        editor = InstanceEditorState(
            instanceId = instance.id,
            address = instance.endpoints.apiBase,
            label = instance.label,
        )
    }

    fun closeEditor() {
        editor = null
    }

    fun setEditorAddress(value: String) {
        editor = editor?.copy(address = value, error = null)
    }

    fun setEditorLabel(value: String) {
        editor = editor?.copy(label = value, error = null)
    }

    /**
     * Validates the entered address against the API root and stores the instance.
     *
     * Renaming without touching the address skips the probe so it works offline.
     */
    fun submitEditor() {
        val state = editor ?: return
        val existing = state.instanceId?.let { store.byId(it) }

        if (existing != null && state.address.trim().trimEnd('/') == existing.endpoints.apiBase) {
            store.save(existing.copy(label = state.label.ifBlank { existing.label }))
            editor = null
            return
        }

        editor = state.copy(busy = true, error = null)

        viewModelScope.launch {
            when (val result = probeInstance(state.address)) {
                is ProbeResult.Failure -> {
                    editor = editor?.copy(busy = false, error = result.message)
                }

                is ProbeResult.Success -> {
                    val endpoints = endpointsFrom(result.apiBase, result.root)
                    val label = state.label.ifBlank { defaultLabelFor(result.apiBase) }

                    store.save(
                        existing?.copy(label = label, endpoints = endpoints)
                            ?: Instance(
                                id = store.newInstanceId(),
                                label = label,
                                endpoints = endpoints,
                            )
                    )

                    editor = null
                    notice = result.apiBase
                }
            }
        }
    }

    /** Re-reads the API root so changed file/websocket endpoints are picked up. */
    fun refreshEndpoints(instance: Instance, onDone: (StoredEndpoints?) -> Unit) {
        viewModelScope.launch {
            when (val result = probeInstance(instance.endpoints.apiBase)) {
                is ProbeResult.Failure -> onDone(null)
                is ProbeResult.Success -> {
                    val endpoints = endpointsFrom(result.apiBase, result.root)
                    store.save(instance.copy(endpoints = endpoints))
                    onDone(endpoints)
                }
            }
        }
    }

    fun requestSwitch(instance: Instance) {
        if (instance.id == selectedId.value) return
        pendingSwitch = instance
    }

    fun cancelSwitch() {
        pendingSwitch = null
    }

    fun confirmSwitch(context: Context) {
        val target = pendingSwitch ?: return
        pendingSwitch = null
        viewModelScope.launch {
            switcher.switchTo(target.id, context)
        }
    }

    fun requestDelete(instance: Instance) {
        if (instance.builtIn || instance.id == selectedId.value) return
        pendingDelete = instance
    }

    fun cancelDelete() {
        pendingDelete = null
    }

    fun confirmDelete(context: Context) {
        val target = pendingDelete ?: return
        pendingDelete = null
        if (store.delete(target.id)) {
            // The offline cache is per instance, so it goes with it.
            context.deleteDatabase("revolt-${target.id}.db")
        }
    }

    fun noticeShown() {
        notice = null
    }
}
