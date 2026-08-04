package chat.stoat.instances

import chat.stoat.core.model.data.InstanceEndpoints
import kotlinx.serialization.Serializable

/**
 * A backend the app can be signed in to.
 *
 * Instances are independent of each other: each one keeps its own session, its own offline
 * database and its own set of endpoints. Switching between them never signs you out of the
 * other ones.
 */
@Serializable
data class Instance(
    /** Stable identifier. Also namespaces this instance's offline database. */
    val id: String,
    /** User-facing name. Defaults to the instance host when the user does not set one. */
    val label: String,
    /** Endpoints as discovered from the API root at the time the instance was added. */
    val endpoints: StoredEndpoints,
    /** True for the bundled stoat.chat entry, which cannot be deleted. */
    val builtIn: Boolean = false,
    /** The session for this instance, if the user is signed in to it. */
    val session: StoredSession? = null,
) {
    val isSignedIn: Boolean get() = session?.token?.isNotBlank() == true

    companion object {
        const val OFFICIAL_ID = "official"

        /** The bundled stoat.chat instance. Always present, always first in the list. */
        fun official(session: StoredSession? = null) = Instance(
            id = OFFICIAL_ID,
            label = "stoat.chat",
            endpoints = StoredEndpoints.from(InstanceEndpoints.Official),
            builtIn = true,
            session = session,
        )
    }
}

/**
 * Serializable mirror of [InstanceEndpoints].
 *
 * `core/model` deliberately has no serialization on its endpoint holder, so the persisted shape
 * lives here instead of leaking a storage concern into the model module.
 */
@Serializable
data class StoredEndpoints(
    val apiBase: String,
    val websocket: String,
    val files: String,
    val proxy: String,
    val webApp: String,
    val invites: String,
) {
    fun toEndpoints() = InstanceEndpoints(
        apiBase = apiBase,
        websocket = websocket,
        files = files,
        proxy = proxy,
        webApp = webApp,
        invites = invites,
    )

    companion object {
        fun from(endpoints: InstanceEndpoints) = StoredEndpoints(
            apiBase = endpoints.apiBase,
            websocket = endpoints.websocket,
            files = endpoints.files,
            proxy = endpoints.proxy,
            webApp = endpoints.webApp,
            invites = endpoints.invites,
        )
    }
}

/**
 * A signed-in session, parked here while another instance is active.
 *
 * The active instance's session additionally lives in [chat.stoat.persistence.KVStorage] under the
 * keys the rest of the app already reads; this is the copy that survives a switch.
 */
@Serializable
data class StoredSession(
    val token: String,
    val sessionId: String = "",
    val selfId: String? = null,
    val selfName: String? = null,
    val selfAvatarUrl: String? = null,
)
