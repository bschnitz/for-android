package chat.stoat.core.model.data

/**
 * The set of endpoints that belong to a single instance.
 *
 * Everything in here is instance specific: pointing the app at a self-hosted backend means
 * replacing all of these, not just the API base. Stoat's own corporate URLs (marketing site,
 * support portal, changelog) are not part of this and stay constant — see [Constants.kt].
 */
data class InstanceEndpoints(
    /** Base URL of the REST API, including any version segment. No trailing slash. */
    val apiBase: String,
    /** WebSocket URL used for the realtime event stream. */
    val websocket: String,
    /** Autumn (file server) base URL, used for both uploads and downloads. */
    val files: String,
    /** January (media proxy) base URL. */
    val proxy: String,
    /** Web client base URL, used to build shareable links. */
    val webApp: String,
    /** Base URL invite links are built from. Falls back to [webApp] on custom instances. */
    val invites: String,
) {
    companion object {
        /** The official stoat.chat instance — also the fallback whenever nothing is selected. */
        val Official = InstanceEndpoints(
            apiBase = "https://api.stoat.chat/0.8",
            websocket = "wss://events.stoat.chat",
            files = "https://cdn.stoatusercontent.com",
            proxy = "https://proxy.stoatusercontent.com",
            webApp = "https://stoat.chat",
            invites = "https://stt.gg",
        )
    }
}

/**
 * Holds the endpoints of the instance the app is currently talking to.
 *
 * This is deliberately a plain, dependency-free holder: it is read on every request (via the
 * `STOAT_*` accessors in [Constants.kt]) and written exactly once per process, very early during
 * startup, before anything touches the network or the database. The app module owns persistence
 * and is responsible for calling [select] — see `chat.stoat.instances.InstanceStore`.
 */
object ActiveInstance {
    @Volatile
    var endpoints: InstanceEndpoints = InstanceEndpoints.Official
        private set

    /**
     * Identifier of the selected instance. Used to namespace per-instance state such as the
     * offline database. Empty until the app module has bootstrapped the selection.
     */
    @Volatile
    var id: String = ""
        private set

    fun select(id: String, endpoints: InstanceEndpoints) {
        this.id = id
        this.endpoints = endpoints
    }
}
