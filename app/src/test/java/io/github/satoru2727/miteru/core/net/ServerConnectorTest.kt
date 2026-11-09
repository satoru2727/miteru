package io.github.satoru2727.miteru.core.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerConnectorTest {

    @Test
    fun `candidate order puts last good address first`() {
        val order = candidateOrderFor(
            listOf("http://lan:7000", "https://remote.example.com"),
            "https://remote.example.com",
        )
        assertEquals(listOf("https://remote.example.com", "http://lan:7000"), order)
    }

    @Test
    fun `candidate order ignores last good address that is no longer registered`() {
        val order = candidateOrderFor(listOf("http://lan:7000"), "http://old:7000")
        assertEquals(listOf("http://lan:7000"), order)
    }

    @Test
    fun `first reachable skips addresses that do not respond`() = runBlocking {
        val probed = mutableListOf<String>()
        val result = firstReachable(
            listOf("http://lan:7000", "https://remote.example.com"),
        ) { url ->
            probed.add(url)
            url == "https://remote.example.com"
        }
        assertEquals("https://remote.example.com", result)
        assertEquals(listOf("http://lan:7000", "https://remote.example.com"), probed)
    }

    @Test
    fun `first reachable returns null when nothing responds`() = runBlocking {
        val result = firstReachable(
            listOf("http://lan:7000", "https://remote.example.com"),
        ) { false }
        assertNull(result)
    }
}
