package io.github.satoru2727.miteru.ui.player

import io.github.satoru2727.miteru.core.model.CmSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CmSkipTest {

    private val sections = listOf(
        CmSection(120.0, 150.0),
        CmSection(600.0, 660.0),
    )

    @Test
    fun cmSkipWithinSectionReturnsEnd() {
        assertEquals(150_000L, cmSkipTargetMs(130_000L, sections))
        assertEquals(660_000L, cmSkipTargetMs(610_000L, sections))
    }

    @Test
    fun cmSkipSectionStartIsInside() {
        assertEquals(150_000L, cmSkipTargetMs(120_000L, sections))
    }

    @Test
    fun cmSkipOutsideSectionReturnsNull() {
        assertNull(cmSkipTargetMs(0L, sections))
        assertNull(cmSkipTargetMs(100_000L, sections))
        // 終端ちょうどは区間外（すでに CM が終わっている）
        assertNull(cmSkipTargetMs(150_000L, sections))
        assertNull(cmSkipTargetMs(120_000L, emptyList()))
    }

    @Test
    fun cmSkipRemainingSeconds() {
        assertEquals(20, cmRemainingSeconds(130_000L, sections))
        assertEquals(60, cmRemainingSeconds(600_000L, sections))
        assertNull(cmRemainingSeconds(100_000L, sections))
    }
}
