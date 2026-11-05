package io.github.satoru2727.miteru.ui.player

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.satoru2727.miteru.core.model.JikkyoComment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val LaneCount = 6
private const val TravelMillis = 7000
private const val VisibleMillis = 7500L

private data class Danmaku(
    val id: Long,
    val comment: JikkyoComment,
    val lane: Int,
)

/**
 * ニコニコ実況のコメントを動画上に重ねる。right は右から左へ流し、
 * top / bottom は画面の上端・下端に一定時間表示する。
 */
@Composable
fun CommentOverlay(
    comments: Flow<JikkyoComment>,
    modifier: Modifier = Modifier,
) {
    val active = remember { mutableStateListOf<Danmaku>() }
    var nextId by remember { mutableLongStateOf(0L) }
    var nextLane by remember { mutableIntStateOf(0) }

    LaunchedEffect(comments) {
        comments.collect { comment ->
            val lane = nextLane % LaneCount
            nextLane += 1
            val item = Danmaku(nextId++, comment, lane)
            active.add(item)
            launch {
                delay(VisibleMillis)
                active.remove(item)
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        active.forEach { item ->
            key(item.id) {
                DanmakuText(
                    item = item,
                    widthPx = widthPx,
                    heightPx = heightPx,
                )
            }
        }
    }
}

@Composable
private fun DanmakuText(
    item: Danmaku,
    widthPx: Float,
    heightPx: Float,
) {
    val density = LocalDensity.current
    var textWidth by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) }
    val comment = item.comment

    LaunchedEffect(item.id) {
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = TravelMillis, easing = LinearEasing),
        )
    }

    val fontSize = when (comment.size) {
        "big" -> 28.sp
        "small" -> 16.sp
        else -> 22.sp
    }
    val laneHeightPx = with(density) { 34.dp.toPx() }
    val topPx = with(density) { 14.dp.toPx() }
    val bottomPx = with(density) { 48.dp.toPx() }

    val x: Float
    val y: Float
    when (comment.type) {
        "top" -> {
            x = (widthPx - textWidth) / 2f
            y = topPx
        }

        "bottom" -> {
            x = (widthPx - textWidth) / 2f
            y = heightPx - bottomPx - laneHeightPx
        }

        else -> {
            x = widthPx * progress.value - textWidth
            y = topPx + laneHeightPx * item.lane
        }
    }

    Text(
        text = comment.text,
        color = parseCommentColor(comment.color),
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
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .onGloballyPositioned { coordinates -> textWidth = coordinates.size.width },
    )
}

private fun parseCommentColor(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color.White)
