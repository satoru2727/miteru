package io.github.satoru2727.miteru.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerScopeTest {

    @Test
    fun `stable and normalized across trailing slashes and whitespace`() {
        val a = serverScope("https://tv.home.satoru.fyi")
        assertEquals(a, serverScope("https://tv.home.satoru.fyi/"))
        assertEquals(a, serverScope("  https://tv.home.satoru.fyi  "))
    }

    @Test
    fun `different servers get different scopes`() {
        assertNotEquals(
            serverScope("https://a.example"),
            serverScope("https://b.example"),
        )
    }

    @Test
    fun `blank urls have no scope`() {
        assertNull(serverScope(null))
        assertNull(serverScope(""))
        assertNull(serverScope("   "))
    }

    @Test
    fun `scope is twelve hex characters`() {
        val scope = serverScope("http://192.168.1.10:7000")
        assertEquals(12, scope?.length)
        assertEquals(scope, scope?.lowercase())
    }
}
