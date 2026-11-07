package io.github.satoru2727.miteru.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueWatchingTest {

    @Test
    fun `only recordings started beyond the threshold appear, newest first`() {
        val positions = mapOf(1 to 10_000L, 2 to 60_000L, 3 to 5_000L, 4 to 120_000L)
        assertEquals(listOf(4, 2), continueWatchingIds(positions))
    }

    @Test
    fun `sorts by id descending and limits`() {
        val positions = (1..30).associateWith { 60_000L }
        val ids = continueWatchingIds(positions, limit = 5)
        assertEquals(listOf(30, 29, 28, 27, 26), ids)
    }

    @Test
    fun `positions below the minimum are ignored`() {
        val positions = mapOf(1 to 29_999L, 2 to 30_000L)
        assertEquals(listOf(2), continueWatchingIds(positions))
    }

    @Test
    fun `empty positions yield an empty shelf`() {
        assertEquals(emptyList<Int>(), continueWatchingIds(emptyMap()))
    }
}
