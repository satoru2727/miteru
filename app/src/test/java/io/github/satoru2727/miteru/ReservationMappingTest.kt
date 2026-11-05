package io.github.satoru2727.miteru

import io.github.satoru2727.miteru.core.net.JikkyoCommentsDto
import io.github.satoru2727.miteru.core.net.ReservationsDto
import io.github.satoru2727.miteru.core.net.toDomain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservationMappingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val programJson = """
        {
          "id": "NID32736-SID1024-EID1",
          "channel_id": "NID32736-SID1024",
          "title": "ニュース7",
          "start_time": "2026-11-05T21:00:00+09:00",
          "end_time": "2026-11-05T21:30:00+09:00",
          "duration": 1800.0
        }
    """.trimIndent()

    private val channelJson = """
        {
          "id": "NID32736-SID1024",
          "display_channel_id": "gr011",
          "name": "NHK総合1・東京",
          "type": "GR",
          "channel_number": "011"
        }
    """.trimIndent()

    @Test
    fun `reservations decode program channel and settings`() {
        val payload = """
            {
              "total": 1,
              "reservations": [
                {
                  "id": 7,
                  "channel": $channelJson,
                  "program": $programJson,
                  "is_recording_in_progress": false,
                  "recording_availability": "Partial",
                  "record_settings": {"is_enabled": false, "priority": 4}
                }
              ]
            }
        """.trimIndent()
        val page = json.decodeFromString(ReservationsDto.serializer(), payload).toDomain()
        assertEquals(1, page.total)
        val item = page.items.first()
        assertEquals(7, item.id)
        assertEquals("NHK総合1・東京", item.channel?.name)
        assertEquals("ニュース7", item.program?.title)
        assertFalse(item.isEnabled)
        assertEquals(4, item.priority)
        assertTrue(item.isPartial)
    }

    @Test
    fun `jikkyo comments map named colors and keep hex`() {
        val payload = """
            {
              "is_success": true,
              "comments": [
                {"time": 1.5, "type": "right", "size": "medium", "color": "red", "text": "わこつ"},
                {"time": 2.0, "type": "top", "size": "big", "color": "#123456", "text": "きた"}
              ]
            }
        """.trimIndent()
        val comments = json.decodeFromString(JikkyoCommentsDto.serializer(), payload)
            .comments.map { it.toDomain() }
        assertEquals(2, comments.size)
        assertEquals("#FF4B4B", comments[0].color)
        assertEquals("#123456", comments[1].color)
        assertEquals("top", comments[1].type)
        assertEquals(2.0, comments[1].timeSeconds, 0.001)
    }
}
