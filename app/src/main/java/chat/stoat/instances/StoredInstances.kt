package chat.stoat.instances

import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

/**
 * The rules for the persisted instance list, kept free of Android so they can be tested directly.
 *
 * [InstanceStore] owns *where* the list lives; this owns *what* a valid list looks like. The one
 * invariant everything else leans on: the built-in entry is always present, so the list is never
 * empty and there is always something to fall back to.
 */
internal object StoredInstances {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Result of reading the stored list.
     *
     * @param repaired the stored form did not match the rules and the caller should write [instances]
     *   back — either nothing was stored yet, or it was unreadable, or it predates the built-in entry.
     */
    data class Read(val instances: List<Instance>, val repaired: Boolean)

    fun read(raw: String?): Read {
        if (raw == null) return Read(listOf(Instance.official()), repaired = true)

        val parsed = try {
            json.decodeFromString<List<Instance>>(raw)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Could not read stored instances, resetting:\n${e.asLog()}" }
            return Read(listOf(Instance.official()), repaired = true)
        }

        // The built-in instance must always exist, even if the stored list was written by an older
        // version or got truncated.
        return if (parsed.none { it.id == Instance.OFFICIAL_ID }) {
            Read(order(listOf(Instance.official()) + parsed), repaired = true)
        } else {
            Read(order(parsed), repaired = false)
        }
    }

    fun encode(instances: List<Instance>): String = json.encodeToString(order(instances))

    /**
     * Pins the built-in entry to the top. Sorting is stable, so instances the user added keep the
     * order they were added in.
     */
    fun order(instances: List<Instance>): List<Instance> = instances.sortedByDescending { it.builtIn }
}
