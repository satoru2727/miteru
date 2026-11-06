package io.github.satoru2727.miteru.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.satoru2727.miteru.core.model.JikkyoComment
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val DefaultTravelMillis = 7000
private const val FixedHoldMillis = 5000L
private const val LaneHeightDp = 30
private const val TopMarginDp = 10
private const val BottomMarginDp = 14
private const val MaxSpawnWaitMillis = 3000L

private data class Danmaku(
    val id: Long,
    val comment: JikkyoComment,
    val lane: Int,
)

/** レーンごとの直近コメントの状態（重なり回避に使う）。 */
private class LaneState {
    var lastSpawnMs: Long = Long.MIN_VALUE / 2
    var lastWidthPx: Float = 0f
}

/**
 * スクロールコメントが重ならないための最小発射間隔（ミリ秒）。
 *
 * コメントは幅に依らず同じ duration で流れるため、幅 W のコメントの速度は (viewport + W) / duration。
 * 先行コメント A と後続コメント B が交差しない条件は、どちらの幅基準でも
 * duration * W / (viewport + W)（W は A・B の広い方）の形になる。
 */
internal fun requiredSpawnGapMs(
    travelMillis: Int,
    existingWidthPx: Float,
    newWidthPx: Float,
    viewportWidthPx: Float,
): Long {
    if (viewportWidthPx <= 0f) return 0L
    val w = max(existingWidthPx, newWidthPx).coerceAtLeast(0f)
    return (travelMillis.toFloat() * w / (viewportWidthPx + w)).toLong()
}

/** テキスト幅のざっくり見積り（全角=1.0em、半角=0.55em、末尾に 1em の余白）。 */
internal fun estimateCommentWidthPx(text: String, fontSizePx: Float): Float {
    var width = 0f
    for (ch in text) {
        width += if (ch.code < 0x2E80) fontSizePx * 0.55f else fontSizePx
    }
    return width + fontSizePx
}

internal fun commentFontSizeSp(size: String, scale: Float): TextUnit = when (size) {
    "big" -> 28.sp * scale
    "small" -> 16.sp * scale
    else -> 22.sp * scale
}

/**
 * ニコニコ実況のコメントを動画上に重ねる。
 *
 * - 「流れるコメント」はレーンごとに最後のコメントとの間隔を計算し、重ならない
 *   タイミングで発射する（混雑時は最大 3 秒まで待ち、それ以上は多少重なっても表示を優先）。
 * - 「上固定 / 下固定」コメントはスロットに分けて縦積みする。
 */
@Composable
fun CommentOverlay(
    comments: Flow<JikkyoComment>,
    scale: Float = 1f,
    alpha: Float = 1f,
    speed: String = "normal",
    modifier: Modifier = Modifier,
) {
    val travelMillis = when (speed) {
        "fast" -> 5000
        "slow" -> 10000
        else -> DefaultTravelMillis
    }
    val holdMillis = travelMillis + 900L

    val active = remember { mutableStateListOf<Danmaku>() }
    var nextId by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val channel = remember { Channel<JikkyoComment>(Channel.UNLIMITED) }

    LaunchedEffect(comments) {
        comments.collect { channel.trySend(it) }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val clampedScale = scale.coerceIn(0.5f, 2.5f)
        val laneHeightPx = with(density) { LaneHeightDp.dp.toPx() } * clampedScale
        val topMarginPx = with(density) { TopMarginDp.dp.toPx() }
        val bottomMarginPx = with(density) { BottomMarginDp.dp.toPx() }
        val laneCount = (((heightPx - topMarginPx) / laneHeightPx).toInt()).coerceIn(2, 10)
        val fixedSlots = laneCount.coerceAtMost(4)

        val lanes = remember(laneCount) { List(laneCount) { LaneState() } }
        val topSlotFreeAt = remember(fixedSlots) { MutableList(fixedSlots) { 0L } }
        val bottomSlotFreeAt = remember(fixedSlots) { MutableList(fixedSlots) { 0L } }

        LaunchedEffect(channel, widthPx, heightPx, travelMillis, clampedScale) {
            for (comment in channel) {
                val fontSizePx = with(density) { commentFontSizeSp(comment.size, clampedScale).toPx() }
                when (comment.type) {
                    "top", "bottom" -> {
                        val slots = if (comment.type == "top") topSlotFreeAt else bottomSlotFreeAt
                        val slot = pickFreeSlot(slots)
                        slots[slot] = SystemClock.elapsedRealtime() + FixedHoldMillis
                        val item = Danmaku(nextId++, comment, slot)
                        active.add(item)
                        scope.launch {
                            delay(FixedHoldMillis)
                            active.remove(item)
                        }
                    }

                    else -> {
                        val estWidthPx = estimateCommentWidthPx(comment.text, fontSizePx)
                        val lane = pickFreeLane(lanes, estWidthPx, widthPx, travelMillis)
                        lanes[lane].lastSpawnMs = SystemClock.elapsedRealtime()
                        lanes[lane].lastWidthPx = estWidthPx
                        val item = Danmaku(nextId++, comment, lane)
                        active.add(item)
                        scope.launch {
                            delay(holdMillis)
                            active.remove(item)
                        }
                    }
                }
            }
        }

        active.forEach { item ->
            key(item.id) {
                DanmakuText(
                    item = item,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    scale = clampedScale,
                    alpha = alpha,
                    travelMillis = travelMillis,
                    laneHeightPx = laneHeightPx,
                    topMarginPx = topMarginPx,
                    bottomMarginPx = bottomMarginPx,
                )
            }
        }
    }
}

/** いちばん空いているスロットを選ぶ。空きがなければ少し待つ。 */
private suspend fun pickFreeSlot(slots: MutableList<Long>): Int {
    val now = SystemClock.elapsedRealtime()
    var best = 0
    var wait = Long.MAX_VALUE
    for (i in slots.indices) {
        val w = (slots[i] - now).coerceAtLeast(0L)
        if (w < wait) {
            wait = w
            best = i
        }
    }
    if (wait > 0L) delay(wait.coerceAtMost(MaxSpawnWaitMillis))
    return best
}

/** いちばん空いているレーンを選ぶ。空きがなければ少し待つ。 */
private suspend fun pickFreeLane(
    lanes: List<LaneState>,
    newWidthPx: Float,
    viewportWidthPx: Float,
    travelMillis: Int,
): Int {
    val now = SystemClock.elapsedRealtime()
    var best = 0
    var wait = Long.MAX_VALUE
    for (i in lanes.indices) {
        val gap = requiredSpawnGapMs(travelMillis, lanes[i].lastWidthPx, newWidthPx, viewportWidthPx)
        val w = (lanes[i].lastSpawnMs + gap - now).coerceAtLeast(0L)
        if (w < wait) {
            wait = w
            best = i
        }
    }
    if (wait > 0L) delay(wait.coerceAtMost(MaxSpawnWaitMillis))
    return best
}

@Composable
private fun DanmakuText(
    item: Danmaku,
    widthPx: Float,
    heightPx: Float,
    scale: Float,
    alpha: Float,
    travelMillis: Int,
    laneHeightPx: Float,
    topMarginPx: Float,
    bottomMarginPx: Float,
) {
    val density = LocalDensity.current
    val comment = item.comment
    val fontSize = commentFontSizeSp(comment.size, scale)
    val estimatedWidthPx = with(density) { estimateCommentWidthPx(comment.text, fontSize.toPx()) }.roundToInt()
    var textWidth by remember { mutableIntStateOf(estimatedWidthPx) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(item.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = travelMillis, easing = LinearEasing),
        )
    }

    val x: Float
    val y: Float
    when (comment.type) {
        "top" -> {
            x = (widthPx - textWidth) / 2f
            y = topMarginPx + laneHeightPx * item.lane
        }

        "bottom" -> {
            x = (widthPx - textWidth) / 2f
            y = heightPx - bottomMarginPx - laneHeightPx * (item.lane + 1)
        }

        else -> {
            // 右端の外から左端の外へ等速で流す
            x = widthPx - (widthPx + textWidth) * progress.value
            y = topMarginPx + laneHeightPx * item.lane
        }
    }

    Text(
        text = comment.text,
        color = parseCommentColor(comment.color).copy(alpha = alpha),
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        style = TextStyle(
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                offset = Offset(1.5f, 1.5f),
                blurRadius = 4f,
            ),
        ),
        onTextLayout = { result: TextLayoutResult ->
            if (result.size.width > 0) textWidth = result.size.width
        },
        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
    )
}

private fun parseCommentColor(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color.White)
