package chat.stoat.instances

import android.content.Context
import android.content.SharedPreferences
import chat.stoat.core.model.data.ActiveInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import logcat.logcat
import java.util.UUID

/**
 * Whether a write has to have reached disk by the time the call returns.
 *
 * The distinction matters because [SharedPreferences.Editor.commit] is blocking file I/O, and most
 * of these writes happen on the main thread in response to a tap.
 */
enum class Durability {
    /**
     * Update memory now and let Android flush the file. Correct for anything driven by the UI: the
     * in-memory [StateFlow]s are what the app reads, and the platform flushes pending writes when
     * the process is backgrounded.
     */
    ASYNC,

    /**
     * Block until the file has been written. Needed only when the process is about to be killed
     * deliberately, where a queued write would never run — see [InstanceSwitcher].
     */
    SYNC,
}

/**
 * Persists the list of known instances and which one is selected.
 *
 * Backed by [SharedPreferences] rather than the app's usual DataStore-based
 * [chat.stoat.persistence.KVStorage] for one reason: the selected instance has to be resolved
 * *synchronously* at process start, before Koin is up and before anything opens the database or
 * fires a request. That includes cold starts into the FCM handler service, where there is no
 * coroutine scope to suspend in.
 *
 * Session tokens stored here are not encrypted. That matches how the app already persists the
 * active session in DataStore, so it is not a new exposure — but it is worth knowing.
 */
class InstanceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _instances = MutableStateFlow(readInstances())
    val instances: StateFlow<List<Instance>> = _instances.asStateFlow()

    private val _selectedId = MutableStateFlow(readSelectedId())
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    val selected: Instance
        get() = _instances.value.firstOrNull { it.id == _selectedId.value }
            // StoredInstances.read guarantees the built-in entry exists, so these are formalities
            // that keep a corrupt selection from being able to crash startup.
            ?: _instances.value.firstOrNull { it.id == Instance.OFFICIAL_ID }
            ?: Instance.official()

    /**
     * Publishes the selected instance's endpoints to [ActiveInstance].
     *
     * Must run before the first network request and before the database is opened — see
     * [chat.stoat.StoatApplication.onCreate].
     */
    fun bootstrap() {
        val instance = selected
        ActiveInstance.select(instance.id, instance.endpoints.toEndpoints())
        logcat { "Active instance: ${instance.label} (${instance.endpoints.apiBase})" }
    }

    fun byId(id: String): Instance? = _instances.value.firstOrNull { it.id == id }

    /** Adds a new instance, or replaces an existing one with the same id. */
    fun save(instance: Instance, durability: Durability = Durability.ASYNC) {
        val updated = _instances.value.toMutableList()
        val index = updated.indexOfFirst { it.id == instance.id }
        if (index >= 0) updated[index] = instance else updated.add(instance)
        writeInstances(updated, durability)
    }

    /**
     * Removes an instance. Built-in instances and the selected instance are kept; callers should
     * switch away first.
     */
    fun delete(id: String): Boolean {
        val instance = byId(id) ?: return false
        if (instance.builtIn || id == _selectedId.value) return false
        writeInstances(_instances.value.filterNot { it.id == id }, Durability.ASYNC)
        return true
    }

    fun select(id: String, durability: Durability = Durability.ASYNC) {
        if (byId(id) == null) return
        prefs.write(durability) { putString(KEY_SELECTED, id) }
        _selectedId.value = id
    }

    fun updateSession(
        id: String,
        session: StoredSession?,
        durability: Durability = Durability.ASYNC,
    ) {
        val instance = byId(id) ?: return
        save(instance.copy(session = session), durability)
    }

    fun newInstanceId(): String = UUID.randomUUID().toString()

    private fun writeInstances(instances: List<Instance>, durability: Durability) {
        val ordered = StoredInstances.order(instances)
        prefs.write(durability) { putString(KEY_INSTANCES, StoredInstances.encode(ordered)) }
        _instances.value = ordered
    }

    private fun readInstances(): List<Instance> {
        val read = StoredInstances.read(prefs.getString(KEY_INSTANCES, null))
        if (read.repaired) {
            // Runs in the constructor, i.e. on the main thread during Application.onCreate. If the
            // process dies before the flush lands, the next start simply repairs it again.
            prefs.write(Durability.ASYNC) {
                putString(KEY_INSTANCES, StoredInstances.encode(read.instances))
            }
        }
        return read.instances
    }

    private fun readSelectedId(): String =
        prefs.getString(KEY_SELECTED, null) ?: Instance.OFFICIAL_ID

    private fun SharedPreferences.write(
        durability: Durability,
        edits: SharedPreferences.Editor.() -> Unit,
    ) {
        val editor = edit().apply(edits)
        when (durability) {
            Durability.ASYNC -> editor.apply()
            Durability.SYNC -> editor.commit()
        }
    }

    private companion object {
        const val PREFS_NAME = "stoat_instances"
        const val KEY_INSTANCES = "instances"
        const val KEY_SELECTED = "selected"
    }
}
