package io.github.satoru2727.miteru.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.github.satoru2727.miteru.core.model.Channel
import io.github.satoru2727.miteru.core.model.CmSection
import io.github.satoru2727.miteru.core.model.Quality
import io.github.satoru2727.miteru.core.model.Recording
import io.github.satoru2727.miteru.core.model.TileInfo
import io.github.satoru2727.miteru.core.log.MiteruLog
import io.github.satoru2727.miteru.core.net.ApiException
import io.github.satoru2727.miteru.core.net.KonomiApi
import io.github.satoru2727.miteru.core.settings.SettingsStore
import io.github.satoru2727.miteru.data.KonomiRepository
import io.github.satoru2727.miteru.data.OfflineItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

private const val WATCHED_THRESHOLD_MS = 30_000L

data class NowPlaying(
    val isLive: Boolean,
    val title: String,
    val subtitle: String?,
    val channelId: String?,
    val videoId: Int?,
    val logoUrl: String?,
    val quality: Quality,
    val cmSections: List<CmSection> = emptyList(),
    val tileInfo: TileInfo? = null,
    val isOffline: Boolean = false,
)

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
)

/**
 * UI から MediaController を介して PlaybackService の ExoPlayer を操作する。
 * 録画再生では HLS セッションの keep-alive と視聴位置の保存もここで面倒を見る。
 */
class PlaybackLauncher(
    private val context: Context,
    private val repository: KonomiRepository,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var currentLiveChannel: Channel? = null

    // 録画再生フォールバック用の状態
    private data class FallbackStream(
        val url: String,
        val quality: Quality,
        val sessionId: String,
        val mimeType: String,
    )
    private var fallbackStreams: List<FallbackStream> = emptyList()
    private var fallbackIndex: Int = 0
    private var fallbackRecording: Recording? = null
    private var fallbackStartMs: Long = 0

    /** プレイヤー画面が前面に出ているか（ホーム遷移時の自動 PiP 判定に使う）。 */
    @Volatile
    var playerVisible: Boolean = false

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controllerState: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    private var keepAliveJob: Job? = null
    private var progressJob: Job? = null
    private var activeVideoId: Int? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            _playerState.value = _playerState.value.copy(
                isBuffering = buffering,
                durationMs = controller?.duration?.coerceAtLeast(0L) ?: 0L,
            )
            if (playbackState == Player.STATE_ENDED) {
                activeVideoId?.let { videoId ->
                    scope.launch { settings.clearPosition(videoId) }
                }
                scope.launch { settings.setPlaybackActive(false) }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val mediaUrl = controller?.currentMediaItem?.localConfiguration?.uri?.toString()
            MiteruLog.e(
                "Player",
                "onPlayerError ${error.errorCodeName}(${error.errorCode}) media=${mediaUrl?.let { MiteruLog.mask(it) }} message=${error.message}",
                error.cause ?: error,
            )

            // 録画再生中のフォールバック: 次の候補（低い品質）を試す
            if (fallbackStreams.isNotEmpty() && fallbackIndex < fallbackStreams.size - 1) {
                fallbackIndex++
                val next = fallbackStreams[fallbackIndex]
                MiteruLog.d("Player", "fallback: trying next stream (q=${next.quality.apiName})")
                scope.launch {
                    val recording = fallbackRecording ?: return@launch
                    tryPlayUrl(next, recording)
                }
                return
            }

            val userMessage = when (error.errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "ネットワーク接続に失敗しました"
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "サーバーからエラーが返されました"
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "動画形式がサポートされていません"
                androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "動画をデコードできません"
                else -> error.message ?: error.errorCodeName
            }
            _playerState.value = _playerState.value.copy(
                error = userMessage,
                isBuffering = false,
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _playerState.value = _playerState.value.copy(error = null)
        }
    }

    private suspend fun awaitController(): MediaController? {
        controller?.let { return it }
        return withContext(Dispatchers.Main.immediate) {
            val future = controllerFuture ?: run {
                val token = SessionToken(
                    context,
                    ComponentName(context, PlaybackService::class.java),
                )
                MediaController.Builder(context, token).buildAsync().also { controllerFuture = it }
            }
            suspendCancellableCoroutine { continuation ->
                future.addListener(
                    {
                        val result = runCatching { future.get() }.getOrNull()
                        if (result != null) {
                            controller = result
                            _controller.value = result
                            result.addListener(listener)
                        }
                        if (continuation.isActive) continuation.resume(result)
                    },
                    MoreExecutors.directExecutor(),
                )
            }
        }
    }

    suspend fun playLive(channel: Channel, quality: Quality): Boolean {
        MiteruLog.d("Player", "playLive channel=${channel.id} quality=$quality")
        // 録画のフォールバック状態をクリア
        fallbackStreams = emptyList()
        fallbackIndex = 0
        fallbackRecording = null
        val api = repository.api() ?: return false
        val mediaController = awaitController() ?: return false
        stopSessionJobs()

        val url = api.liveStreamUrl(channel, quality)
        val metadata = MediaMetadata.Builder()
            .setTitle(channel.name)
            .setArtist(channel.current?.title ?: "ライブ")
            .setArtworkUri(api.logoUrl(channel.displayId).let { android.net.Uri.parse(it) })
            .build()
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaId("live:${channel.displayId}")
            .setMediaMetadata(metadata)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(2_000L)
                    .build(),
            )
            .build()

        currentLiveChannel = channel
        activeVideoId = null
        _nowPlaying.value = NowPlaying(
            isLive = true,
            title = channel.name,
            subtitle = channel.current?.title,
            channelId = channel.displayId,
            videoId = null,
            logoUrl = api.logoUrl(channel.displayId),
            quality = quality,
        )
        _playerState.value = PlayerUiState()
        mediaController.clearMediaItems()
        mediaController.stop()
        // v1.3.3 の書き換えで誤って setMediaItem/prepare/play が release() に置き換わり、
        // ライブが再生できなくなっていた（release はコントローラを切断してしまう）
        mediaController.setMediaItem(item)
        mediaController.prepare()
        mediaController.play()
        fallbackStreams = emptyList()
        fallbackIndex = 0
        fallbackRecording = null
        settings.setPlaybackActive(true)
        return true
    }

    // 録画再生用の品質フォールバックリスト（高 → 低）
    private val recordingQualityFallback: List<Quality> = listOf(
        Quality.Q1080P, Quality.Q810P, Quality.Q720P, Quality.Q540P,
        Quality.Q480P, Quality.Q360P, Quality.Q240P,
    )

    suspend fun playRecording(recording: Recording, quality: Quality, startPositionMs: Long = 0L): Boolean {
        MiteruLog.d("Player", "playRecording id=${recording.id} quality=$quality")
        // 録画が再生可能かをチェック（Missing/Recording 状態は再生できない）
        if (recording.video.isMissing || recording.video.isRecording) {
            MiteruLog.w("Player", "recording not playable: status=${recording.video.status}")
            _playerState.value = _playerState.value.copy(error = "録画がまだ準備中です")
            return false
        }
        val api = repository.api() ?: return false

        // フォールバック用のストリームリスト：要求品質 → 低い品質へ（HLS のみ、MPEG-TS は録画には存在しない）
        val qualitiesToTry = listOf(quality) + recordingQualityFallback.filter { it != quality }
        fallbackStreams = qualitiesToTry.map { q ->
            val sessionId = UUID.randomUUID().toString().substringBefore('-')
            // KonomiTV: /api/streams/video/{id}/{quality}/playlist?session_id={sid}
            // ExoPlayer は URL 末尾に .m3u8 拡張子が無いと HLS と認識できないため MIME タイプを明示
            FallbackStream(
                url = api.videoStreamUrl(recording.id, q, sessionId),
                quality = q,
                sessionId = sessionId,
                mimeType = MimeTypes.APPLICATION_M3U8,
            )
        }
        fallbackIndex = 0
        fallbackRecording = recording
        fallbackStartMs = startPositionMs

        return tryPlayUrl(fallbackStreams[0], recording)
    }

    /** 指定したストリームを再生する（内部用） */
    private suspend fun tryPlayUrl(stream: FallbackStream, recording: Recording): Boolean {
        MiteruLog.d(
            "Player",
            "tryPlayUrl q=${stream.quality.apiName} mime=${stream.mimeType} ${MiteruLog.mask(stream.url)}",
        )
        val mediaController = awaitController() ?: return false
        stopSessionJobs()

        val metadata = MediaMetadata.Builder()
            .setTitle(recording.title)
            .setArtist(recording.channelName ?: recording.subtitle ?: "")
            .build()
        val item = MediaItem.Builder()
            .setUri(stream.url)
            .setMimeType(stream.mimeType)
            .setMediaId("video:${recording.id}")
            .setMediaMetadata(metadata)
            .build()

        currentLiveChannel = null
        activeVideoId = recording.id
        _nowPlaying.value = NowPlaying(
            isLive = false,
            title = recording.title,
            subtitle = recording.subtitle ?: recording.channelName,
            channelId = recording.channelId,
            videoId = recording.id,
            logoUrl = null,
            quality = stream.quality,
            cmSections = recording.cmSections,
            tileInfo = recording.tileInfo,
        )
        _playerState.value = PlayerUiState()

        return try {
            mediaController.setMediaItem(item, fallbackStartMs.coerceAtLeast(0L))
            mediaController.prepare()
            mediaController.play()
            startKeepAlive(recording.id, stream.quality, stream.sessionId)
            startProgressSaver(recording.id)
            settings.setPlaybackActive(true)
            true
        } catch (e: Exception) {
            MiteruLog.w("Player", "failed to start playback: ${MiteruLog.mask(stream.url)} ${e.message}")
            false
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    /** ローカルに保存した HLS（オフライン保存）を再生する。 */
    suspend fun playOffline(item: OfflineItem, startPositionMs: Long = 0L): Boolean {
        val playlist = java.io.File(context.filesDir, "offline/${item.videoId}/playlist.m3u8")
        if (!playlist.exists()) return false
        val mediaController = awaitController() ?: return false
        stopSessionJobs()
        fallbackStreams = emptyList()
        fallbackIndex = 0
        fallbackRecording = null

        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.channelName ?: item.subtitle ?: "")
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(android.net.Uri.fromFile(playlist))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setMediaId("video:${item.videoId}")
            .setMediaMetadata(metadata)
            .build()

        currentLiveChannel = null
        activeVideoId = item.videoId
        _nowPlaying.value = NowPlaying(
            isLive = false,
            title = item.title,
            subtitle = item.channelName ?: item.subtitle,
            channelId = null,
            videoId = item.videoId,
            logoUrl = null,
            quality = Quality.fromApiName(item.quality) ?: Quality.Q720P,
            cmSections = item.cmSections,
            tileInfo = item.tileInfo,
            isOffline = true,
        )
        _playerState.value = PlayerUiState()

        return try {
            mediaController.setMediaItem(mediaItem, startPositionMs.coerceAtLeast(0L))
            mediaController.prepare()
            mediaController.play()
            startProgressSaver(item.videoId)
            settings.setPlaybackActive(true)
            true
        } catch (e: Exception) {
            MiteruLog.w("Player", "failed to start offline playback: ${e.message}")
            false
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun retry() {
        controller?.prepare()
    }

    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    fun setVolume(volume: Float) {
        controller?.volume = volume.coerceIn(0f, 1f)
    }

    fun audioGroups(): List<Tracks.Group> =
        controller?.currentTracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO }.orEmpty()

    fun selectAudioGroup(index: Int) {
        val player = controller ?: return
        val group = audioGroups().getOrNull(index) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()
    }

    /** 現在の再生位置（コメントの同期に使う）。 */
    fun positionMs(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun stop() {
        val player = controller
        val videoId = activeVideoId
        val position = player?.currentPosition ?: 0L
        if (videoId != null && position > 0) {
            scope.launch {
                settings.savePosition(videoId, position)
                if (position >= WATCHED_THRESHOLD_MS) settings.markWatched(videoId)
            }
        }
        player?.stop()
        player?.clearMediaItems()
        stopSessionJobs()
        _nowPlaying.value = null
        _playerState.value = PlayerUiState()
        scope.launch { settings.setPlaybackActive(false) }
    }

    /** アプリを開き直したとき、サービス側で再生が続いていれば UI 状態を復元する。 */
    suspend fun restoreIfPlaying() {
        val mediaController = awaitController() ?: return
        val mediaItem = mediaController.currentMediaItem
        if (mediaItem == null || mediaController.playbackState == Player.STATE_IDLE) {
            settings.setPlaybackActive(false)
            return
        }
        val mediaId = mediaItem.mediaId
        val isLive = mediaId.startsWith("live:")
        val id = mediaId.substringAfter(':', "")
        val title = mediaItem.mediaMetadata.title?.toString() ?: return
        val isOffline = !isLive && mediaItem.localConfiguration?.uri?.scheme == "file"
        _nowPlaying.value = NowPlaying(
            isLive = isLive,
            title = title,
            subtitle = mediaItem.mediaMetadata.artist?.toString(),
            channelId = if (isLive) id else null,
            videoId = if (!isLive) id.toIntOrNull() else null,
            logoUrl = if (isLive) settings.snapshot().serverUrl?.let { "$it/api/channels/$id/logo" } else null,
            quality = Quality.Q1080P,
            isOffline = isOffline,
        )
        _playerState.value = _playerState.value.copy(
            isPlaying = mediaController.isPlaying,
            durationMs = mediaController.duration.coerceAtLeast(0L),
        )

        // コールドスタート復元時は録画情報が無いので、CM 区間とタイル情報を後から補う
        if (!isLive && !isOffline) {
            val restoredVideoId = id.toIntOrNull()
            if (restoredVideoId != null) {
                scope.launch {
                    repository.loadRecording(restoredVideoId).onSuccess { recording ->
                        val current = _nowPlaying.value
                        if (current != null && current.videoId == restoredVideoId) {
                            _nowPlaying.value = current.copy(
                                cmSections = recording.cmSections,
                                tileInfo = recording.tileInfo,
                            )
                        }
                    }
                }
            }
        }
    }

    /** 画質変更など、同じコンテンツを別 URL で開き直すときに使う。 */
    suspend fun switchQuality(quality: Quality) {
        val current = _nowPlaying.value ?: return
        if (current.isOffline) return
        val position = controller?.currentPosition ?: 0L
        if (current.isLive) {
            val channel = currentLiveChannel ?: return
            playLive(channel, quality)
        } else {
            val videoId = current.videoId ?: return
            val recording = repository.loadRecording(videoId).getOrNull() ?: return
            playRecording(recording, quality, position)
        }
    }

    private fun startKeepAlive(videoId: Int, quality: Quality, sessionId: String) {
        keepAliveJob = scope.launch {
            while (true) {
                // サーバーの SESSION_TIMEOUT は 10 秒。公式クライアントと同じ 5 秒間隔で送る
                // （10 秒間隔だとサーバーのタイマーに毎回負けて 422 になる）
                delay(5_000L)
                val api = repository.api() ?: continue
                // スタックした 1 本が次の送信を遅らせないよう、1 回の送信に上限を設ける
                val result = withTimeoutOrNull(4_000L) {
                    runCatching { api.keepAlive(videoId, quality, sessionId) }
                }
                val failure = result?.exceptionOrNull() as? ApiException
                if (failure?.statusCode == 422) {
                    // セッションが消えていたら、プレイリスト API を叩いて同じ session_id で作り直す
                    runCatching { api.touchVideoSession(videoId, quality, sessionId) }
                } else if (failure?.isNetworkError == true) {
                    // 接続先が使えなくなっていたら、別の接続先への切り替えを試す
                    runCatching { repository.failoverIfNeeded() }
                }
            }
        }
    }

    private fun startProgressSaver(videoId: Int) {
        progressJob = scope.launch {
            var watchedMarked = false
            while (true) {
                delay(5_000L)
                val player = controller ?: continue
                if (player.isPlaying && player.currentPosition > 0) {
                    settings.savePosition(videoId, player.currentPosition)
                    // 30 秒以上見たら「視聴済み」として記録する（未視聴フィルタ用）
                    if (!watchedMarked && player.currentPosition >= WATCHED_THRESHOLD_MS) {
                        watchedMarked = true
                        settings.markWatched(videoId)
                    }
                }
            }
        }
    }

    private fun stopSessionJobs() {
        keepAliveJob?.cancel()
        keepAliveJob = null
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopSessionJobs()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }
}
