package io.github.satoru2727.miteru.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentDedupeTest {

    @Test
    fun `same user repeating within window is suppressed`() {
        var now = 0L
        val filter = CommentBurstFilter(windowMs = 8_000L, clock = { now })
        assertTrue(filter.shouldShow("a:user1", "草"))
        now = 3_000L
        assertFalse(filter.shouldShow("a:user1", "草"))
        // 最後の送信 (3_000) から 8 秒以上空いたので再表示される
        now = 11_001L
        assertTrue(filter.shouldShow("a:user1", "草"))
    }

    @Test
    fun `different users are independent`() {
        var now = 0L
        val filter = CommentBurstFilter(windowMs = 8_000L, clock = { now })
        assertTrue(filter.shouldShow("a:user1", "草"))
        now = 1_000L
        assertTrue(filter.shouldShow("a:user2", "草"))
    }

    @Test
    fun `different text is not suppressed`() {
        var now = 0L
        val filter = CommentBurstFilter(windowMs = 8_000L, clock = { now })
        assertTrue(filter.shouldShow("a:user1", "草"))
        now = 1_000L
        assertTrue(filter.shouldShow("a:user1", "www"))
    }

    @Test
    fun `anonymous repeats are suppressed by text`() {
        var now = 0L
        val filter = CommentBurstFilter(windowMs = 8_000L, clock = { now })
        assertTrue(filter.shouldShow(null, "連投"))
        now = 2_000L
        assertFalse(filter.shouldShow(null, "連投"))
    }

    @Test
    fun `continued spam stays suppressed while it repeats`() {
        var now = 0L
        val filter = CommentBurstFilter(windowMs = 8_000L, clock = { now })
        assertTrue(filter.shouldShow("a:x", "spam"))
        // 8 秒未満の間隔で連投し続ける間はずっと抑止される
        now = 6_000L
        assertFalse(filter.shouldShow("a:x", "spam"))
        now = 12_000L
        assertFalse(filter.shouldShow("a:x", "spam"))
        // 9 秒空いたら再び表示
        now = 21_000L
        assertTrue(filter.shouldShow("a:x", "spam"))
    }
}
