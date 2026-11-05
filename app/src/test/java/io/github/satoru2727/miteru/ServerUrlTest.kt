package io.github.satoru2727.miteru

import io.github.satoru2727.miteru.core.net.KonomiApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `adds http scheme when missing`() {
        assertEquals(
            "http://192.168.1.10:7000",
            KonomiApi.normalizeServerUrl("192.168.1.10:7000"),
        )
    }

    @Test
    fun `keeps https and strips trailing slash`() {
        assertEquals(
            "https://konomi.example.com",
            KonomiApi.normalizeServerUrl("https://konomi.example.com/"),
        )
    }

    @Test
    fun `strips api suffix`() {
        assertEquals(
            "http://192.168.1.10:7000",
            KonomiApi.normalizeServerUrl("http://192.168.1.10:7000/api/"),
        )
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(
            "http://host:7000",
            KonomiApi.normalizeServerUrl("  http://host:7000  "),
        )
    }

    @Test
    fun `rejects other schemes`() {
        assertNull(KonomiApi.normalizeServerUrl("ftp://host:7000"))
        assertNull(KonomiApi.normalizeServerUrl("javascript:alert(1)"))
    }

    @Test
    fun `rejects blank`() {
        assertNull(KonomiApi.normalizeServerUrl("   "))
    }
}
