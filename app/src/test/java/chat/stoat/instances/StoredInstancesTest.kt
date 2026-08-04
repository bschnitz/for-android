package chat.stoat.instances

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredInstancesTest {
    private fun instance(id: String, label: String = id) = Instance(
        id = id,
        label = label,
        endpoints = StoredEndpoints(
            apiBase = "https://$label/api",
            websocket = "wss://$label/ws",
            files = "https://$label",
            proxy = "https://$label",
            webApp = "https://$label",
            invites = "https://$label",
        ),
    )

    @Test
    fun `starts out with the built-in instance`() {
        val read = StoredInstances.read(null)

        assertEquals(listOf(Instance.OFFICIAL_ID), read.instances.map { it.id })
        assertTrue("nothing stored yet has to be written back", read.repaired)
    }

    @Test
    fun `reads back what it wrote`() {
        val written = listOf(Instance.official(), instance("a", "one"), instance("b", "two"))

        val read = StoredInstances.read(StoredInstances.encode(written))

        assertEquals(written, read.instances)
        assertFalse("a list that already follows the rules needs no write", read.repaired)
    }

    @Test
    fun `keeps a stored session across a round trip`() {
        val session = StoredSession(token = "t", sessionId = "s", selfId = "u", selfName = "name")
        val written = listOf(Instance.official(), instance("a").copy(session = session))

        val read = StoredInstances.read(StoredInstances.encode(written))

        assertEquals(session, read.instances.single { it.id == "a" }.session)
        assertTrue(read.instances.single { it.id == "a" }.isSignedIn)
    }

    @Test
    fun `resets to the built-in instance when the stored list is unreadable`() {
        val read = StoredInstances.read("{not json")

        assertEquals(listOf(Instance.OFFICIAL_ID), read.instances.map { it.id })
        assertTrue(read.repaired)
    }

    @Test
    fun `restores the built-in instance if it is missing`() {
        // Could be a list written before the built-in entry existed, or a truncated write.
        val read = StoredInstances.read(StoredInstances.encode(listOf(instance("a"))))

        assertEquals(listOf(Instance.OFFICIAL_ID, "a"), read.instances.map { it.id })
        assertTrue(read.repaired)
    }

    @Test
    fun `pins the built-in instance to the top`() {
        val read = StoredInstances.read(
            StoredInstances.encode(listOf(instance("a"), Instance.official(), instance("b")))
        )

        assertEquals(Instance.OFFICIAL_ID, read.instances.first().id)
    }

    @Test
    fun `leaves the order of added instances alone`() {
        val ordered = StoredInstances.order(
            listOf(instance("c"), instance("a"), Instance.official(), instance("b"))
        )

        assertEquals(listOf(Instance.OFFICIAL_ID, "c", "a", "b"), ordered.map { it.id })
    }

    @Test
    fun `never yields an empty list, so callers always have something to select`() {
        // InstanceStore.selected and bootstrap() both lean on this.
        listOf(null, "", "[]", "garbage").forEach { raw ->
            val read = StoredInstances.read(raw)
            assertTrue(
                "read($raw) must contain the built-in instance",
                read.instances.any { it.id == Instance.OFFICIAL_ID },
            )
        }
    }

    @Test
    fun `tolerates unknown fields so a downgrade does not wipe the list`() {
        val raw = """[{"id":"official","label":"stoat.chat","builtIn":true,"somethingNew":42,
            "endpoints":{"apiBase":"https://a/api","websocket":"wss://a/ws","files":"https://a",
            "proxy":"https://a","webApp":"https://a","invites":"https://a"}}]"""

        val read = StoredInstances.read(raw)

        assertEquals(listOf(Instance.OFFICIAL_ID), read.instances.map { it.id })
        assertFalse(read.repaired)
    }
}
