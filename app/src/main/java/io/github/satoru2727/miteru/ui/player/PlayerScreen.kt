package io.github.satoru2727.miteru.ui.player

import android.app.PictureInPictureParams
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.satoru2727.miteru.R
import io.github.satoru2727.miteru.core.model.Quality
import io.github.satoru2727.miteru.ui.AppViewModel
import io.github.satoru2727.miteru.ui.util.findActivity
import io.github.satoru2727.miteru.ui.util.formatClock
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(vm: AppViewModel) {
    val item by vm.nowPlaying.collectAsStateWithLifecycle()
    val playerState by vm.playerState.collectAsStateWithLifecycle()
    val controller by vm.controller.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val nowPlaying = item ?: return

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current

    var controlsVisible by remember { mutableStateOf(true) }
    var showQuality by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableStateOf<Long?>(null) }

    BackHandler { vm.closePlayer() }

    DisposableEffect(activity, view) {
        val window = activity?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insets = WindowCompat.getInsetsController(window, view)
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val inPip by produceState(false, activity) {
        while (true) {
            value = activity?.isInPictureInPictureMode == true
            delay(400)
        }
    }
    LaunchedEffect(inPip) {
        if (inPip) controlsVisible = false
    }

    LaunchedEffect(controlsVisible, playerState.isPlaying) {
        if (controlsVisible && playerState.isPlaying) {
            delay(4_000)
            controlsVisible = false
        }
    }

    val position by produceState(0L, controller) {
        while (true) {
            value = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            delay(500)
        }
    }
    val duration by produceState(0L, controller) {
        while (true) {
            val raw = controller?.duration ?: 0L
            value = if (raw > 0) raw else 0L
            delay(1_000)
        }
    }

    val audioGroups = controller?.currentTracks?.groups
        ?.filter { it.type == C.TRACK_TYPE_AUDIO }
        .orEmpty()

    val commentFlow = vm.commentFlow

    // 動画の実表示サイズ（レターボックスを除いた描画矩形の計算に使う）
    var videoSize by remember { mutableStateOf<VideoSize?>(null) }
    DisposableEffect(controller) {
        val c = controller
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }
        }
        c?.addListener(listener)
        videoSize = c?.videoSize?.takeIf { it.width > 0 && it.height > 0 }
        onDispose { c?.removeListener(listener) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()
        val videoRect = remember(videoSize, containerWidthPx, containerHeightPx) {
            computeFittedVideoRect(videoSize, containerWidthPx, containerHeightPx)
        }

        if (controller != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = controller },
                modifier = Modifier.fillMaxSize(),
            )
        }

        commentFlow?.let { flow ->
            // PiP 中はコメントを描画しない。通常時は動画の描画領域にだけ重ねる。
            if (!inPip) {
                CommentOverlay(
                    comments = flow,
                    scale = settings?.commentScale ?: 1f,
                    alpha = settings?.commentAlpha ?: 1f,
                    speed = settings?.commentSpeed ?: "normal",
                    modifier = videoRect?.let { rect ->
                        with(density) {
                            Modifier
                                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                                .size(rect.width.toDp(), rect.height.toDp())
                        }
                    } ?: Modifier.fillMaxSize(),
                )
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(widthPx, nowPlaying.isLive) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                if (!nowPlaying.isLive) {
                                    when {
                                        offset.x < widthPx * 0.35f -> vm.seekBy(-10_000)
                                        offset.x > widthPx * 0.65f -> vm.seekBy(10_000)
                                        else -> vm.togglePlayPause()
                                    }
                                }
                            },
                        )
                    },
            )
        }

        if (playerState.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(42.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                            ),
                        )
                        .statusBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.closePlayer() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.player_close), tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = nowPlaying.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!nowPlaying.subtitle.isNullOrBlank()) {
                            Text(
                                text = nowPlaying.subtitle.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TextButton(onClick = { showQuality = true }) {
                        Icon(Icons.Rounded.HighQuality, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(nowPlaying.quality.label, color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    if (audioGroups.size > 1) {
                        IconButton(onClick = { showAudio = true }) {
                            Icon(Icons.Rounded.Audiotrack, contentDescription = stringResource(R.string.player_audio), tint = Color.White)
                        }
                    }
                    if (activity != null) {
                        IconButton(
                            onClick = {
                                runCatching {
                                    activity.enterPictureInPictureMode(
                                        PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .build(),
                                    )
                                }
                            },
                        ) {
                            Icon(
                                Icons.Rounded.PictureInPictureAlt,
                                contentDescription = stringResource(R.string.player_pip),
                                tint = Color.White,
                            )
                        }
                    }
                }

                Surface(
                    onClick = vm::togglePlayPause,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.Center).size(68.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    if (!nowPlaying.isLive && duration > 0) {
                        val shown = scrubPosition ?: position
                        Slider(
                            value = shown.coerceIn(0L, duration).toFloat(),
                            onValueChange = { scrubPosition = it.toLong() },
                            onValueChangeFinished = {
                                scrubPosition?.let(vm::seekTo)
                                scrubPosition = null
                            },
                            valueRange = 0f..duration.toFloat(),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatClock(shown), color = Color.White, style = MaterialTheme.typography.labelSmall)
                            Text(formatClock(duration), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFFE53935),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.player_live_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { vm.zap(-1) }) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.player_channel_down), tint = Color.White)
                            }
                            Text(
                                text = "チャンネル",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                            IconButton(onClick = { vm.zap(1) }) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_channel_up), tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        if (playerState.error != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.player_error),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.player_error_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { vm.retryPlayback() }) {
                        Text(stringResource(R.string.player_retry))
                    }
                    TextButton(onClick = { vm.stopPlayback() }) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.player_close), color = Color.White)
                    }
                }
            }
        }
    }

    if (showQuality) {
        ModalBottomSheet(
            onDismissRequest = { showQuality = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                Text(
                    text = stringResource(R.string.player_quality),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
                Quality.entries.forEach { quality ->
                    val selected = quality == nowPlaying.quality
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.setQuality(quality)
                                showQuality = false
                            }
                            .background(
                                if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                else Color.Transparent,
                            )
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = quality.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.player_quality_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (showAudio) {
        ModalBottomSheet(
            onDismissRequest = { showAudio = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                Text(
                    text = stringResource(R.string.player_audio),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
                audioGroups.forEachIndexed { index, group ->
                    val label = group.mediaTrackGroup.getFormat(0).label
                        ?: if (index == 0) stringResource(R.string.player_audio_primary) else stringResource(R.string.player_audio_secondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            vm.selectAudioGroup(index)
                            showAudio = false
                        }) {
                            Text(stringResource(R.string.common_ok))
                        }
                    }
                }
            }
        }
    }
}

/**
 * レターボックス表示（RESIZE_MODE_FIT）時の動画描画矩形を計算する。
 * 動画サイズが不明な間は null を返し、呼び出し側は全画面表示にフォールバックする。
 */
internal fun computeFittedVideoRect(
    videoSize: VideoSize?,
    containerWidthPx: Float,
    containerHeightPx: Float,
): Rect? {
    val size = videoSize ?: return null
    if (size.width <= 0 || size.height <= 0) return null
    if (containerWidthPx <= 0f || containerHeightPx <= 0f) return null
    val aspect = size.width * size.pixelWidthHeightRatio / size.height
    if (aspect <= 0f) return null
    val containerAspect = containerWidthPx / containerHeightPx
    return if (aspect > containerAspect) {
        val height = containerWidthPx / aspect
        val top = (containerHeightPx - height) / 2f
        Rect(0f, top, containerWidthPx, top + height)
    } else {
        val width = containerHeightPx * aspect
        val left = (containerWidthPx - width) / 2f
        Rect(left, 0f, left + width, containerHeightPx)
    }
}
