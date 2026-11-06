package io.github.satoru2727.miteru.ui.player

import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class CommentLayoutTest {

    // --- requiredSpawnGapMs ---

    @Test
    fun gapIsShorterForNarrowComments() {
        val viewport = 1080f
        val narrow = requiredSpawnGapMs(7000, 300f, 300f, viewport)
        val wide = requiredSpawnGapMs(7000, 900f, 900f, viewport)
        assertTrue("narrow=$narrow wide=$wide", narrow < wide)
    }

    @Test
    fun gapDependsOnWiderOfTheTwoComments() {
        val viewport = 1080f
        val a = requiredSpawnGapMs(7000, 200f, 800f, viewport)
        val b = requiredSpawnGapMs(7000, 800f, 200f, viewport)
        assertEquals(a, b)
    }

    @Test
    fun gapNeverReachesTravelTimeEvenForHugeComments() {
        val viewport = 1080f
        val gap = requiredSpawnGapMs(7000, 1_000_000f, 1_000_000f, viewport)
        assertTrue("gap=$gap", gap < 7000)
    }

    @Test
    fun gapScalesWithTravelTime() {
        val viewport = 1080f
        val normal = requiredSpawnGapMs(7000, 600f, 600f, viewport)
        val fast = requiredSpawnGapMs(5000, 600f, 600f, viewport)
        assertTrue("fast=$fast normal=$normal", fast < normal)
    }

    @Test
    fun gapIsZeroForDegenerateViewport() {
        assertEquals(0L, requiredSpawnGapMs(7000, 500f, 500f, 0f))
        assertEquals(0L, requiredSpawnGapMs(7000, 500f, 500f, -10f))
    }

    // --- estimateCommentWidthPx ---

    @Test
    fun estimateCountsFullWidthCharactersMore() {
        val font = 50f
        val ascii = estimateCommentWidthPx("hello", font)
        val japanese = estimateCommentWidthPx("こんにちは", font)
        assertTrue("ascii=$ascii japanese=$japanese", japanese > ascii)
    }

    @Test
    fun estimateGrowsWithTextLength() {
        val font = 50f
        val short = estimateCommentWidthPx("あ", font)
        val long = estimateCommentWidthPx("あああああ", font)
        assertTrue(long > short)
    }

    // --- computeFittedVideoRect ---

    @Test
    fun fittedRectLetterboxesWidescreenInPortrait() {
        // 16:9 video in a 1080x2400 portrait container -> full width, centered vertically
        val rect = computeFittedVideoRect(VideoSize(1920, 1080), 1080f, 2400f)
        assertNotNull(rect)
        rect!!
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(1080f, rect.right, 0.01f)
        assertEquals(1080f * 9f / 16f, rect.height, 0.5f)
        assertEquals((2400f - 1080f * 9f / 16f) / 2f, rect.top, 0.5f)
    }

    @Test
    fun fittedRectPillarboxesNarrowVideoInLandscape() {
        // 9:16 video in a 2400x1080 landscape container -> full height, centered horizontally
        val rect = computeFittedVideoRect(VideoSize(1080, 1920), 2400f, 1080f)
        assertNotNull(rect)
        rect!!
        assertEquals(0f, rect.top, 0.01f)
        assertEquals(1080f, rect.bottom, 0.01f)
        assertEquals(1080f * 9f / 16f, rect.width, 0.5f)
        assertEquals((2400f - 1080f * 9f / 16f) / 2f, rect.left, 0.5f)
    }

    @Test
    fun fittedRectHandlesAnamorphicPixelRatio() {
        // 1440x1080 with SAR 4:3 behaves like 16:9
        val rect = computeFittedVideoRect(VideoSize(1440, 1080, 4f / 3f), 1080f, 2400f)
        assertNotNull(rect)
        assertEquals(1080f * 9f / 16f, rect!!.height, 0.5f)
    }

    @Test
    fun fittedRectIsNullWhenSizeUnknown() {
        assertNull(computeFittedVideoRect(null, 1080f, 2400f))
        assertNull(computeFittedVideoRect(VideoSize(0, 0), 1080f, 2400f))
        assertNull(computeFittedVideoRect(VideoSize(1920, 1080), 0f, 2400f))
    }
}
