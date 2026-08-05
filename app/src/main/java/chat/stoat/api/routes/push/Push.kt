package chat.stoat.api.routes.push

import chat.stoat.api.StoatHttp
import chat.stoat.api.routes.account.WebPushData
import chat.stoat.api.api
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Registers a Web Push subscription with the active instance.
 *
 * The three values are the standard subscription triple of RFC 8291: where to deliver, and the
 * two keys the server needs to encrypt for this device.
 */
suspend fun subscribePush(
    endpoint: String,
    p256diffieHellman: String,
    auth: String,
) {
    val data = WebPushData(
        endpoint = endpoint,
        p256diffieHellman = p256diffieHellman,
        auth = auth
    )

    StoatHttp.post("/push/subscribe".api()) {
        setBody(data)
        contentType(ContentType.Application.Json)
    }
}

suspend fun unsubscribePush() {
    StoatHttp.post("/push/unsubscribe".api())
}