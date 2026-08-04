package chat.stoat.instances

import chat.stoat.api.routes.misc.AutumnJanuaryFeature
import chat.stoat.api.routes.misc.Features
import chat.stoat.api.routes.misc.Root
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateApiBasesTest {
    @Test
    fun `assumes https when no scheme is given`() {
        assertEquals("https://chat.example.com", candidateApiBases("chat.example.com").first())
    }

    @Test
    fun `keeps an explicit scheme`() {
        assertEquals("http://192.168.1.10:5000", candidateApiBases("http://192.168.1.10:5000").first())
    }

    @Test
    fun `tries the entered address before any longer variant`() {
        // A reverse proxy can answer on both the root and /api. Storing the longer variant when the
        // user already handed us the API base would leave a working but wrong base configured.
        assertEquals(
            listOf(
                "https://chat.example.com",
                "https://chat.example.com/0.8",
                "https://chat.example.com/api",
                "https://chat.example.com/api/0.8",
            ),
            candidateApiBases("chat.example.com"),
        )
    }

    @Test
    fun `ignores surrounding whitespace and a trailing slash`() {
        assertEquals(candidateApiBases("chat.example.com"), candidateApiBases("  chat.example.com/  "))
    }

    @Test
    fun `rejects input that is not an address`() {
        assertTrue(candidateApiBases("").isEmpty())
        assertTrue(candidateApiBases("   ").isEmpty())
        assertTrue(candidateApiBases("not a host").isEmpty())
    }
}

class EndpointsFromTest {
    private fun root(
        ws: String = "wss://chat.example.com/ws",
        app: String = "https://app.example.com",
        autumn: String = "https://autumn.example.com",
        january: String = "https://january.example.com",
    ) = Root(
        revolt = "0.8.6",
        ws = ws,
        app = app,
        features = Features(
            autumn = AutumnJanuaryFeature(enabled = true, url = autumn),
            january = AutumnJanuaryFeature(enabled = true, url = january),
        ),
    )

    @Test
    fun `takes the endpoints the instance advertises`() {
        val endpoints = endpointsFrom("https://chat.example.com/api", root())

        assertEquals("https://chat.example.com/api", endpoints.apiBase)
        assertEquals("wss://chat.example.com/ws", endpoints.websocket)
        assertEquals("https://autumn.example.com", endpoints.files)
        assertEquals("https://january.example.com", endpoints.proxy)
        assertEquals("https://app.example.com", endpoints.webApp)
    }

    @Test
    fun `falls back to the api origin when a service is not configured`() {
        // A self-hosted instance without a file server reports blank URLs. Keeping them blank would
        // build "/attachments/..." style paths that resolve against nothing.
        val endpoints = endpointsFrom("https://chat.example.com/api", root(app = "", autumn = "", january = ""))

        assertEquals("https://chat.example.com", endpoints.files)
        assertEquals("https://chat.example.com", endpoints.proxy)
        assertEquals("https://chat.example.com", endpoints.webApp)
    }

    @Test
    fun `keeps the port in the fallback origin`() {
        val endpoints = endpointsFrom("http://192.168.1.10:5000/api", root(app = "", autumn = ""))

        assertEquals("http://192.168.1.10:5000", endpoints.files)
        assertEquals("http://192.168.1.10:5000", endpoints.webApp)
    }

    @Test
    fun `builds invites from the web client`() {
        // Self-hosted instances have no short invite domain of their own.
        assertEquals("https://app.example.com", endpointsFrom("https://chat.example.com/api", root()).invites)
    }

    @Test
    fun `strips trailing slashes from advertised endpoints`() {
        val endpoints = endpointsFrom(
            "https://chat.example.com/api",
            root(
                ws = "wss://chat.example.com/ws/",
                app = "https://app.example.com/",
                autumn = "https://autumn.example.com/",
            ),
        )

        assertEquals("wss://chat.example.com/ws", endpoints.websocket)
        assertEquals("https://app.example.com", endpoints.webApp)
        assertEquals("https://autumn.example.com", endpoints.files)
    }
}

class DefaultLabelForTest {
    @Test
    fun `labels an instance by its host`() {
        assertEquals("chat.example.com", defaultLabelFor("https://chat.example.com/api"))
    }

    @Test
    fun `falls back to the address when there is no host to take`() {
        assertEquals("nonsense", defaultLabelFor("nonsense"))
    }
}
