package chat.stoat.instances

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import chat.stoat.activities.MainActivity
import chat.stoat.persistence.KVStorage
import logcat.logcat
import kotlin.system.exitProcess

/**
 * Moves the app from one instance to another.
 *
 * The live session lives in [KVStorage] under the keys the rest of the app reads; each instance's
 * session is parked in [InstanceStore] while that instance is not active. Switching swaps the two
 * and then restarts the process.
 *
 * The restart is deliberate. Endpoints, the SQLDelight database, `StoatAPI`'s caches, the realtime
 * socket, loaded settings and the voice manager are all process-wide singletons initialised from
 * the active instance; tearing each of them down in place would be both more code and easier to
 * get subtly wrong than starting clean. A switch is a rare, explicit user action, so paying a cold
 * start for it is a good trade.
 */
class InstanceSwitcher(
    private val store: InstanceStore,
    private val kvStorage: KVStorage,
) {
    /** Id of the instance the app is currently talking to. */
    val activeInstanceId: String get() = store.selectedId.value

    /**
     * Copies the live session into the given instance's record so it survives a switch.
     *
     * Safe to call at any time; a blank token stores `null` rather than an empty session.
     */
    suspend fun captureActiveSession(
        instanceId: String = store.selectedId.value,
        durability: Durability = Durability.ASYNC,
    ) {
        val token = kvStorage.get("sessionToken")

        if (token.isNullOrBlank()) {
            store.updateSession(instanceId, null, durability)
            return
        }

        store.updateSession(
            instanceId,
            StoredSession(
                token = token,
                sessionId = kvStorage.get("sessionId") ?: "",
                selfId = kvStorage.get("selfId"),
                selfName = kvStorage.get("selfName"),
                selfAvatarUrl = kvStorage.get("selfAvatarUrl"),
            ),
            durability,
        )
    }

    /** Forgets the stored session for an instance, e.g. after signing out of it. */
    suspend fun forgetSession(instanceId: String) {
        store.updateSession(instanceId, null)
    }

    /**
     * Selects [instanceId] and restarts into it. Does not return — the process exits.
     */
    suspend fun switchTo(instanceId: String, context: Context) {
        val target = store.byId(instanceId) ?: return
        if (target.id == store.selectedId.value) return

        // These two writes have to be on disk before restart() kills the process; a queued write
        // would be dropped, and the app would come back up signed in to the wrong instance.
        captureActiveSession(durability = Durability.SYNC)
        store.select(target.id, Durability.SYNC)

        restoreSession(target.session)

        logcat { "Switching to instance ${target.label}, restarting" }
        restart(context)
    }

    private suspend fun restoreSession(session: StoredSession?) {
        if (session == null) {
            kvStorage.remove("sessionToken")
            kvStorage.remove("sessionId")
            kvStorage.remove("selfId")
            kvStorage.remove("selfName")
            kvStorage.remove("selfAvatarUrl")
            return
        }

        kvStorage.set("sessionToken", session.token)
        kvStorage.set("sessionId", session.sessionId)
        session.selfId?.let { kvStorage.set("selfId", it) }
        session.selfName?.let { kvStorage.set("selfName", it) }
        session.selfAvatarUrl?.let { kvStorage.set("selfAvatarUrl", it) }
    }

    /**
     * Kills the process and has the system bring [MainActivity] back.
     *
     * The relaunch goes through the alarm manager rather than a plain `startActivity` followed by
     * an immediate exit: that race is lost often enough in practice that the app would just
     * disappear. A short inexact alarm from a foreground app fires promptly.
     */
    private fun restart(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pending = PendingIntent.getActivity(
            context,
            RESTART_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + RESTART_DELAY_MS,
            pending
        )

        exitProcess(0)
    }

    private companion object {
        const val RESTART_REQUEST_CODE = 8021
        const val RESTART_DELAY_MS = 150L
    }
}
