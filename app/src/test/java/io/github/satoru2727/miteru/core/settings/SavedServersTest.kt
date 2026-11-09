package io.github.satoru2727.miteru.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedServersTest {

    @Test
    fun `decodes legacy format with derived id`() {
        val servers = decodeServers("自宅\u001Fhttp://10.0.0.5:7000")
        assertEquals(1, servers.size)
        assertEquals("自宅", servers[0].name)
        assertEquals(listOf("http://10.0.0.5:7000"), servers[0].addresses)
        assertEquals(serverScope("http://10.0.0.5:7000"), servers[0].id)
    }

    @Test
    fun `round trips servers with multiple addresses`() {
        val servers = listOf(
            SavedServer("sid1", "自宅", listOf("http://10.0.0.5:7000", "https://tv.example.com")),
            SavedServer("sid2", "実家", listOf("http://192.168.1.2:7000")),
        )
        assertEquals(servers, decodeServers(encodeServers(servers)))
    }

    @Test
    fun `decodes empty or malformed input to empty list`() {
        assertEquals(emptyList<SavedServer>(), decodeServers(null))
        assertEquals(emptyList<SavedServer>(), decodeServers(""))
        assertEquals(emptyList<SavedServer>(), decodeServers("壊れたデータ"))
    }

    @Test
    fun `findServerForUrl matches any address and ignores trailing slash`() {
        val servers = listOf(
            SavedServer("sid1", "自宅", listOf("http://10.0.0.5:7000", "https://tv.example.com")),
        )
        assertEquals("sid1", findServerForUrl(servers, "http://10.0.0.5:7000")?.id)
        assertEquals("sid1", findServerForUrl(servers, "https://tv.example.com/")?.id)
        assertNull(findServerForUrl(servers, "http://unknown:7000"))
        assertNull(findServerForUrl(servers, null))
    }

    @Test
    fun `scope stays the same across addresses of one server`() {
        val servers = listOf(
            SavedServer("sid1", "自宅", listOf("http://10.0.0.5:7000", "https://tv.example.com")),
        )
        val scopeA = scopeFor(servers, "http://10.0.0.5:7000")
        val scopeB = scopeFor(servers, "https://tv.example.com")
        assertEquals("sid1", scopeA)
        assertEquals(scopeA, scopeB)
    }

    @Test
    fun `scope falls back to url hash for unregistered url`() {
        val scope = scopeFor(emptyList(), "http://10.0.0.5:7000")
        assertEquals(serverScope("http://10.0.0.5:7000"), scope)
    }

    @Test
    fun `newSavedServer derives id from first address`() {
        val server = newSavedServer("自宅", "http://10.0.0.5:7000")
        assertEquals(serverScope("http://10.0.0.5:7000"), server.id)
        assertEquals("http://10.0.0.5:7000", server.primaryUrl)
        assertEquals("http://10.0.0.5:7000", newSavedServer("", "http://10.0.0.5:7000").name)
    }
}
