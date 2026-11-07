package io.github.satoru2727.miteru.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileThumbnailTest {

    @Test
    fun tileIndexIsPositionDividedByInterval() {
        assertEquals(0, tileIndexFor(0.0, 5.0, 100))
        assertEquals(1, tileIndexFor(5.0, 5.0, 100))
        assertEquals(1, tileIndexFor(9.9, 5.0, 100))
        assertEquals(2, tileIndexFor(10.0, 5.0, 100))
    }

    @Test
    fun tileIndexClampsToRange() {
        assertEquals(99, tileIndexFor(1000.0, 5.0, 100))
        assertEquals(0, tileIndexFor(-1.0, 5.0, 100))
    }

    @Test
    fun tileIndexInvalidInputReturnsNull() {
        assertNull(tileIndexFor(0.0, 0.0, 100))
        assertNull(tileIndexFor(0.0, 5.0, 0))
        assertNull(tileIndexFor(Double.NaN, 5.0, 100))
        assertNull(tileIndexFor(Double.POSITIVE_INFINITY, 5.0, 100))
    }
}
