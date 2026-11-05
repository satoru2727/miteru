package io.github.satoru2727.miteru

import io.github.satoru2727.miteru.ui.util.formatClock
import io.github.satoru2727.miteru.ui.util.formatDurationHuman
import io.github.satoru2727.miteru.ui.util.formatFileSize
import io.github.satoru2727.miteru.ui.util.minutesOfDay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class FormatTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.JAPAN)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `clock shows hours only when needed`() {
        assertEquals("0:00", formatClock(0))
        assertEquals("0:09", formatClock(9_000))
        assertEquals("23:59", formatClock(23 * 60_000L + 59_000L))
        assertEquals("1:02:03", formatClock(3_723_000))
    }

    @Test
    fun `file size uses binary units`() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("1.5 KB", formatFileSize(1536))
        assertEquals("1.0 GB", formatFileSize(1024L * 1024L * 1024L))
    }

    @Test
    fun `duration is human readable in japanese`() {
        assertEquals("45分", formatDurationHuman(45 * 60))
        assertEquals("1時間30分", formatDurationHuman(90 * 60))
        assertEquals("2時間0分", formatDurationHuman(120 * 60))
    }

    @Test
    fun `minutes of day uses the given timezone`() {
        val instant = Instant.parse("2026-11-05T12:34:00Z")
        assertEquals(21 * 60f + 34f, minutesOfDay(instant, ZoneId.of("Asia/Tokyo")), 0.01f)
        assertEquals(12 * 60f + 34f, minutesOfDay(instant, ZoneId.of("UTC")), 0.01f)
    }
}
