package io.github.satoru2727.miteru

import io.github.satoru2727.miteru.core.model.ChannelType
import io.github.satoru2727.miteru.core.model.Quality
import io.github.satoru2727.miteru.core.net.LiveChannelsDto
import io.github.satoru2727.miteru.core.net.RecordedProgramsDto
import io.github.satoru2727.miteru.core.net.TimeTableDto
import io.github.satoru2727.miteru.core.net.toDomain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ApiMappingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val programJson = """
        {
          "id": "NID32736-SID1024-EID1",
          "channel_id": "NID32736-SID1024",
          "network_id": 32736,
          "service_id": 1024,
          "event_id": 1,
          "title": "ニュース7",
          "description": "今日のニュース",
          "detail": {"出演者": "アナウンサー"},
          "start_time": "2026-11-05T21:00:00+09:00",
          "end_time": "2026-11-05T21:30:00+09:00",
          "duration": 1800.0,
          "is_free": true,
          "genres": [{"major": "ニュース", "middle": "報道"}],
          "primary_audio_type": "2/0モード(ステレオ)",
          "primary_audio_language": "日本語",
          "primary_audio_sampling_rate": "48kHz"
        }
    """.trimIndent()

    private val channelJson = """
        {
          "id": "NID32736-SID1024",
          "display_channel_id": "gr011",
          "network_id": 32736,
          "service_id": 1024,
          "transport_stream_id": 32736,
          "remocon_id": 1,
          "channel_number": "011",
          "type": "GR",
          "name": "NHK総合1・東京",
          "is_subchannel": false,
          "is_radiochannel": false,
          "is_watchable": true,
          "is_display": true,
          "viewer_count": 3,
          "program_present": $programJson,
          "program_following": null
        }
    """.trimIndent()

    @Test
    fun `channels decode with current program`() {
        val payload = """
            {"GR":[$channelJson],"BS":[],"CS":[],"CATV":[],"SKY":[],"BS4K":[]}
        """.trimIndent()
        val channels = json.decodeFromString(LiveChannelsDto.serializer(), payload).toDomain()
        assertEquals(1, channels.size)
        val channel = channels.first()
        assertEquals("NHK総合1・東京", channel.name)
        assertEquals(ChannelType.GR, channel.type)
        assertEquals("ニュース7", channel.current?.title)
        assertEquals(3, channel.viewerCount)
        assertEquals(Instant.parse("2026-11-05T12:00:00Z"), channel.current?.start)
        assertEquals(1800L, channel.current?.durationSeconds)
    }

    @Test
    fun `timetable keeps programs and subchannels`() {
        val payload = """
            {
              "channels": [
                {
                  "channel": $channelJson,
                  "programs": [$programJson],
                  "subchannels": [
                    {"channel": $channelJson, "programs": [$programJson]}
                  ]
                }
              ],
              "date_range": {
                "earliest": "2026-11-05T00:00:00+09:00",
                "latest": "2026-11-07T23:59:59+09:00"
              }
            }
        """.trimIndent()
        val timetable = json.decodeFromString(TimeTableDto.serializer(), payload).toDomain()
        assertEquals(1, timetable.channels.size)
        assertEquals(1, timetable.channels.first().programs.size)
        assertEquals(1, timetable.channels.first().subchannels.size)
        assertEquals(Instant.parse("2026-11-04T15:00:00Z"), timetable.earliest)
    }

    @Test
    fun `recordings decode channel and video metadata`() {
        val payload = """
            {
              "total": 1,
              "recorded_programs": [
                {
                  "id": 42,
                  "recorded_video": {
                    "id": 42,
                    "status": "Recorded",
                    "file_path": "/recorded/foo.ts",
                    "file_hash": "abc",
                    "file_size": 123456789,
                    "file_created_at": "2026-11-05T22:00:00+09:00",
                    "file_modified_at": "2026-11-05T22:00:00+09:00",
                    "duration": 3600.0,
                    "container_format": "MPEG-TS",
                    "video_codec": "H.264",
                    "video_codec_profile": "High",
                    "video_scan_type": "Progressive",
                    "video_frame_rate": 29.97,
                    "video_resolution_width": 1920,
                    "video_resolution_height": 1080,
                    "primary_audio_codec": "AAC-LC",
                    "primary_audio_channel": "Stereo",
                    "primary_audio_sampling_rate": 48000
                  },
                  "title": "ドキュメント",
                  "subtitle": "第1回",
                  "description": "",
                  "start_time": "2026-11-05T21:00:00+09:00",
                  "end_time": "2026-11-05T22:00:00+09:00",
                  "duration": 3600.0,
                  "channel": $channelJson,
                  "genres": []
                }
              ]
            }
        """.trimIndent()
        val page = json.decodeFromString(RecordedProgramsDto.serializer(), payload).toDomain()
        assertEquals(1, page.total)
        val recording = page.items.first()
        assertEquals(42, recording.id)
        assertEquals(3600L, recording.durationSeconds)
        assertEquals("NHK総合1・東京", recording.channelName)
        assertEquals(1920, recording.video.width)
        assertTrue(recording.video.container == "MPEG-TS")
        assertEquals("ドキュメント — 第1回", recording.displayTitle)
    }

    @Test
    fun `quality mapping rejects unknown values`() {
        assertEquals(Quality.Q1080P, Quality.fromApiName("1080p"))
        assertEquals(Quality.Q1080P60, Quality.fromApiName("1080p-60fps"))
        assertNull(Quality.fromApiName("original"))
        assertNull(Quality.fromApiName(null))
    }
}
