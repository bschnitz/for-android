package chat.stoat.core.model.data

// Instance specific endpoints. These are properties rather than constants because the app can be
// pointed at a self-hosted instance at runtime; see [ActiveInstance]. Call sites are unaffected,
// they keep reading them as plain strings.

val STOAT_BASE: String get() = ActiveInstance.endpoints.apiBase
val STOAT_WEBSOCKET: String get() = ActiveInstance.endpoints.websocket
val STOAT_FILES: String get() = ActiveInstance.endpoints.files
val STOAT_PROXY: String get() = ActiveInstance.endpoints.proxy
val STOAT_WEB_APP: String get() = ActiveInstance.endpoints.webApp
val STOAT_INVITES: String get() = ActiveInstance.endpoints.invites

// Stoat's own services. These do not depend on which instance you are signed in to.

const val STOAT_SUPPORT = "https://support.stoat.chat"
const val STOAT_MARKETING = "https://stoat.chat"
const val STOAT_BETA_WEB_APP = "https://beta.stoat.chat"
const val STOAT_CHANGELOG = "https://changelog.stoat.chat"
