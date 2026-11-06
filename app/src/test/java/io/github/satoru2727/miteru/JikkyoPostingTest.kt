package io.github.satoru2727.miteru

import io.github.satoru2727.miteru.data.buildPostCommentPayload
import io.github.satoru2727.miteru.data.computeVpos
import io.github.satoru2727.miteru.data.parseVposBaseTime
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JikkyoPostingTest {

    @Test
    fun vposCountsTenthsOfMillisecondsSinceBase() {
        assertEquals(123L, computeVpos(1_000_230L, 999_000L))
    }

    @Test
    fun vposNeverGoesNegative() {
        assertEquals(0L, computeVpos(1_000L, 5_000L))
    }

    @Test
    fun payloadCarriesPostCommentShape() {
        val payload = buildPostCommentPayload("テスト", "red", "ue", "big", 42L)
        assertEquals("postComment", payload["type"]?.jsonPrimitive?.contentOrNull)
        val data = payload["data"]!!.jsonObject
        assertEquals("テスト", data["text"]?.jsonPrimitive?.contentOrNull)
        assertEquals("red", data["color"]?.jsonPrimitive?.contentOrNull)
        assertEquals("ue", data["position"]?.jsonPrimitive?.contentOrNull)
        assertEquals("big", data["size"]?.jsonPrimitive?.contentOrNull)
        assertEquals(42L, data["vpos"]?.jsonPrimitive?.long)
        assertEquals(true, data["isAnonymous"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun vposBaseTimeParsesIsoWithOffset() {
        // 2026-11-07T21:00:00+09:00 == 2026-11-07T12:00:00Z
        val ms = parseVposBaseTime("2026-11-07T21:00:00+09:00")
        assertNotNull(ms)
        assertEquals(1_794_052_800_000L, ms)
    }

    @Test
    fun vposBaseTimeTreatsNaiveTimeAsJst() {
        val ms = parseVposBaseTime("2026-11-07T21:00:00")
        assertNotNull(ms)
        assertEquals(1_794_052_800_000L, ms)
    }

    @Test
    fun vposBaseTimeRejectsGarbage() {
        assertNull(parseVposBaseTime("not-a-date"))
    }
}
