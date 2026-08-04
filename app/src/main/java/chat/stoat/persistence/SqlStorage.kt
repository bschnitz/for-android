package chat.stoat.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import chat.stoat.StoatApplication
import chat.stoat.core.model.data.ActiveInstance
import chat.stoat.instances.Instance

object SqlStorage {
    /**
     * The offline cache is per instance — servers, channels and messages from one backend must
     * never show up while another one is active.
     *
     * The official instance keeps the original file name so existing installs keep their cache.
     * [ActiveInstance] is bootstrapped before anything touches this, see
     * [chat.stoat.StoatApplication.onCreate].
     */
    private val databaseName: String
        get() = when (val id = ActiveInstance.id) {
            "", Instance.OFFICIAL_ID -> "revolt.db"
            else -> "revolt-$id.db"
        }

    val driver: SqlDriver = AndroidSqliteDriver(
        Database.Schema,
        StoatApplication.instance.applicationContext,
        databaseName
    )
}
