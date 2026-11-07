package io.github.satoru2727.miteru.ui.player

import io.github.satoru2727.miteru.core.model.CmSection

/**
 * positionMs が CM 区間の中にあるとき、その区間の終端 (ms) を返す。CM 中でなければ null。
 * cm_sections は録画先頭からの相対秒（録画マージン込み）で、KonomiTV が CM 検出済みの録画にのみ入る。
 */
internal fun cmSkipTargetMs(positionMs: Long, sections: List<CmSection>): Long? {
    if (sections.isEmpty() || positionMs < 0) return null
    val positionSeconds = positionMs / 1000.0
    val section = sections.firstOrNull {
        positionSeconds >= it.startSeconds && positionSeconds < it.endSeconds
    } ?: return null
    return (section.endSeconds * 1000.0).toLong()
}

/** CM の残り秒数（スキップボタンの表示用）。CM 中でなければ null。 */
internal fun cmRemainingSeconds(positionMs: Long, sections: List<CmSection>): Int? {
    val target = cmSkipTargetMs(positionMs, sections) ?: return null
    return ((target - positionMs) / 1000L).coerceAtLeast(0L).toInt()
}
