package io.github.satoru2727.miteru.ui.util

/** 実況勢いの段階。KonomiTV 本体（ChannelUtils）と同じ閾値を使う。 */
enum class JikkyoForceTier { NORMAL, MANY, SO_MANY, FESTIVAL }

/** 実況勢い（ニコニコ実況のコメント数指標）を段階に変換する。 */
fun jikkyoForceTier(force: Int?): JikkyoForceTier = when {
    force == null -> JikkyoForceTier.NORMAL
    force >= 500 -> JikkyoForceTier.FESTIVAL
    force >= 200 -> JikkyoForceTier.SO_MANY
    force >= 100 -> JikkyoForceTier.MANY
    else -> JikkyoForceTier.NORMAL
}
