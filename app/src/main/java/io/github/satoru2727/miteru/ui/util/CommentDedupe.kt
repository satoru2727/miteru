package io.github.satoru2727.miteru.ui.util

import io.github.satoru2727.miteru.core.model.JikkyoComment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 連投・同文コメントの抑止。
 *
 * 同じユーザー（匿名はユーザー ID なしとして扱う）が同じ文言を [windowMs] 以内に
 * 再投稿したら表示しない。抑止した場合も直近時刻を更新するので、連投が続く間は
 * 出続けない。テストしやすいよう時刻は [clock] で注入できる。
 */
internal class CommentBurstFilter(
    private val windowMs: Long = 8_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lastShown = HashMap<String, Long>()

    fun shouldShow(userId: String?, text: String): Boolean {
        val key = "${userId.orEmpty()}\u0000$text"
        val now = clock()
        val previous = lastShown[key]
        lastShown[key] = now
        if (lastShown.size > 512) {
            lastShown.entries.removeIf { now - it.value > 60_000L }
        }
        return previous == null || now - previous > windowMs
    }
}

/** [CommentBurstFilter] を Flow に適用する。再生ごとに新しいフィルタを作る。 */
internal fun Flow<JikkyoComment>.dedupeComments(
    windowMs: Long = 8_000L,
): Flow<JikkyoComment> = flow {
    val filter = CommentBurstFilter(windowMs)
    this@dedupeComments.collect { comment ->
        if (filter.shouldShow(comment.userId, comment.text)) emit(comment)
    }
}
