package io.github.satoru2727.miteru.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.satoru2727.miteru.AppContainer
import io.github.satoru2727.miteru.core.model.Channel
import io.github.satoru2727.miteru.core.model.JikkyoComment
import io.github.satoru2727.miteru.core.model.Program
import io.github.satoru2727.miteru.core.model.Quality
import io.github.satoru2727.miteru.core.model.Recording
import io.github.satoru2727.miteru.core.model.ReservationItem
import io.github.satoru2727.miteru.core.model.ServerInfo
import io.github.satoru2727.miteru.core.model.Timetable
import io.github.satoru2727.miteru.core.net.KonomiApi
import io.github.satoru2727.miteru.core.settings.Settings
import io.github.satoru2727.miteru.core.settings.ThemeMode
import io.github.satoru2727.miteru.core.util.isMeteredNetwork
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

enum class Tab { LIVE, GUIDE, RESERVATIONS, RECORDINGS, SETTINGS }

enum class ChannelFilter { ALL, GR, BS, CS, OTHER }

enum class GuideFilter { ALL, GR, BS, CS }

enum class RecordingFilter { ALL, TS, MP4 }

data class PendingResume(val recording: Recording, val positionMs: Long)

sealed interface PendingPlay {
    data class Live(val channel: Channel) : PendingPlay
    data class Video(val recording: Recording) : PendingPlay
}

class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository
    private val settingsStore = container.settings
    val playback = container.playback

    val settings: StateFlow<Settings?> = settingsStore.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null,
    )
    val serverInfo: StateFlow<ServerInfo?> = repository.serverInfo
    val lastChannelRefresh = repository.lastChannelRefresh
    val nowPlaying = playback.nowPlaying
    val playerState = playback.playerState
    val controller = playback.controllerState

    var tab by mutableStateOf(Tab.LIVE)
        private set
    var showPlayer by mutableStateOf(false)
        private set

    /** 再生中のコメント購読。null ならコメント無効。 */
    var commentFlow by mutableStateOf<Flow<JikkyoComment>?>(null)
        private set

    fun openPlayer() {
        showPlayer = true
        playback.playerVisible = true
    }

    // ライブ
    var channelsState by mutableStateOf<LoadState<List<Channel>>>(LoadState.Idle)
        private set
    var channelQuery by mutableStateOf("")
        private set
    var channelFilter by mutableStateOf(ChannelFilter.ALL)
        private set

    // 番組表
    var guideState by mutableStateOf<LoadState<Timetable>>(LoadState.Idle)
        private set
    var guideDayOffset by mutableStateOf(0)
        private set
    var guideFilter by mutableStateOf(GuideFilter.GR)
        private set
    private val guideCache = mutableMapOf<LocalDate, Timetable>()

    // 録画予約
    var reservationsState by mutableStateOf<LoadState<List<ReservationItem>>>(LoadState.Idle)
        private set

    // 録画
    var recordingsState by mutableStateOf<LoadState<List<Recording>>>(LoadState.Idle)
        private set
    var recordingsQuery by mutableStateOf("")
        private set
    var recordingsFilter by mutableStateOf(RecordingFilter.ALL)
        private set
    var recordingsOrder by mutableStateOf("desc")
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var resumePositions by mutableStateOf<Map<Int, Long>>(emptyMap())
        private set
    var pendingResume by mutableStateOf<PendingResume?>(null)
        private set
    var pendingMeteredPlay by mutableStateOf<PendingPlay?>(null)
        private set

    private var recordingsPage = 1
    private var recordingsTotal = 0
    private var searchJob: Job? = null

    private val currentSettings: Settings get() = settings.value ?: Settings()

    init {
        viewModelScope.launch {
            val loaded = settings.filterNotNull().first()
            if (loaded.isConfigured) {
                refreshAll()
                if (loaded.playbackActive) {
                    viewModelScope.launch { playback.restoreIfPlaying() }
                }
            }
        }
        viewModelScope.launch {
            resumePositions = settingsStore.positions()
        }
    }

    fun refreshAll() {
        viewModelScope.launch { repository.refreshServerInfo() }
        loadChannels()
        loadRecordings(refresh = true)
        viewModelScope.launch { resumePositions = settingsStore.positions() }
    }

    fun selectTab(next: Tab) {
        tab = next
        when (next) {
            Tab.GUIDE -> if (guideState is LoadState.Idle) loadGuide()
            Tab.RESERVATIONS -> if (reservationsState is LoadState.Idle) loadReservations()
            Tab.RECORDINGS -> {
                if (recordingsState is LoadState.Idle) loadRecordings()
                viewModelScope.launch { resumePositions = settingsStore.positions() }
            }
            else -> Unit
        }
    }

    // ---- ライブ ----

    fun loadChannels() {
        viewModelScope.launch {
            if (channelsState !is LoadState.Success) channelsState = LoadState.Loading
            repository.loadChannels()
                .onSuccess { channelsState = LoadState.Success(it.value, it.fromCache, it.cachedAt) }
                .onFailure { error ->
                    if (channelsState !is LoadState.Success) channelsState = LoadState.Failure(error)
                }
        }
    }

    fun onChannelQueryChange(value: String) {
        channelQuery = value
    }

    fun onChannelFilterChange(value: ChannelFilter) {
        channelFilter = value
    }

    fun logoUrl(channel: Channel): String? {
        val base = settings.value?.serverUrl ?: return null
        return "$base/api/channels/${channel.displayId}/logo"
    }

    fun thumbnailUrl(videoId: Int): String? {
        val base = settings.value?.serverUrl ?: return null
        return "$base/api/videos/$videoId/thumbnail"
    }

    // ---- 番組表 ----

    fun loadGuide(force: Boolean = false) {
        val date = LocalDate.now().plusDays(guideDayOffset.toLong())
        if (!force) {
            guideCache[date]?.let {
                guideState = LoadState.Success(it)
                return
            }
        }
        viewModelScope.launch {
            guideState = LoadState.Loading
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            repository.loadTimetable(start, end)
                .onSuccess {
                    guideCache[date] = it
                    guideState = LoadState.Success(it)
                }
                .onFailure { guideState = LoadState.Failure(it) }
        }
    }

    fun selectGuideDay(offset: Int) {
        if (offset == guideDayOffset) return
        guideDayOffset = offset
        loadGuide()
    }

    fun changeGuideFilter(filter: GuideFilter) {
        guideFilter = filter
    }

    // ---- 録画予約 ----

    fun loadReservations(force: Boolean = false) {
        viewModelScope.launch {
            if (force || reservationsState !is LoadState.Success) {
                reservationsState = LoadState.Loading
            }
            repository.loadReservations()
                .onSuccess { reservationsState = LoadState.Success(it.items) }
                .onFailure { error ->
                    if (reservationsState !is LoadState.Success) {
                        reservationsState = LoadState.Failure(error)
                    }
                }
        }
    }

    /** 番組表の番組を録画予約する。EDCB バックエンドでのみ成功する。 */
    suspend fun reserve(program: Program): Result<Unit> =
        repository.addReservation(program.id).onSuccess {
            loadReservations(force = true)
            loadGuide(force = true)
        }

    suspend fun cancelReservation(id: Int): Result<Unit> =
        repository.deleteReservation(id).onSuccess {
            loadReservations(force = true)
            loadGuide(force = true)
        }

    suspend fun setReservationEnabled(id: Int, enabled: Boolean): Result<Unit> =
        repository.setReservationEnabled(id, enabled).onSuccess {
            loadReservations(force = true)
        }

    // ---- 録画 ----

    fun loadRecordings(refresh: Boolean = true) {
        if (refresh) recordingsPage = 1
        viewModelScope.launch {
            if (refresh) recordingsState = LoadState.Loading
            val query = recordingsQuery.takeIf { it.isNotBlank() }
            repository.loadRecordings(page = recordingsPage, order = recordingsOrder, query = query)
                .onSuccess { page ->
                    recordingsTotal = page.total
                    val existing = if (refresh) emptyList() else {
                        (recordingsState as? LoadState.Success)?.value.orEmpty()
                    }
                    recordingsState = LoadState.Success((existing + page.items).distinctBy { it.id })
                }
                .onFailure { error ->
                    if (recordingsState !is LoadState.Success) recordingsState = LoadState.Failure(error)
                }
        }
    }

    fun loadMoreRecordings() {
        val loaded = (recordingsState as? LoadState.Success)?.value ?: return
        if (loadingMore || loaded.size >= recordingsTotal) return
        loadingMore = true
        recordingsPage += 1
        viewModelScope.launch {
            val query = recordingsQuery.takeIf { it.isNotBlank() }
            repository.loadRecordings(page = recordingsPage, order = recordingsOrder, query = query)
                .onSuccess { page ->
                    val existing = (recordingsState as? LoadState.Success)?.value.orEmpty()
                    recordingsState = LoadState.Success((existing + page.items).distinctBy { it.id })
                }
            loadingMore = false
        }
    }

    fun onRecordingsQueryChange(value: String) {
        recordingsQuery = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            loadRecordings(refresh = true)
        }
    }

    fun changeRecordingsFilter(filter: RecordingFilter) {
        recordingsFilter = filter
    }

    fun changeRecordingsOrder(order: String) {
        if (order == recordingsOrder) return
        recordingsOrder = order
        loadRecordings(refresh = true)
    }

    // ---- 再生 ----

    fun requestPlayChannel(channel: Channel) {
        if (currentSettings.warnOnMobileData && isMeteredNetwork(container.appContext)) {
            pendingMeteredPlay = PendingPlay.Live(channel)
        } else {
            playChannel(channel)
        }
    }

    fun requestPlayRecording(recording: Recording) {
        if (currentSettings.warnOnMobileData && isMeteredNetwork(container.appContext)) {
            pendingMeteredPlay = PendingPlay.Video(recording)
        } else {
            openRecording(recording)
        }
    }

    fun confirmMeteredPlay() {
        val pending = pendingMeteredPlay ?: return
        pendingMeteredPlay = null
        when (pending) {
            is PendingPlay.Live -> playChannel(pending.channel)
            is PendingPlay.Video -> openRecording(pending.recording)
        }
    }

    fun dismissMeteredPlay() {
        pendingMeteredPlay = null
    }

    private fun playChannel(channel: Channel) {
        viewModelScope.launch {
            if (playback.playLive(channel, currentSettings.defaultQuality)) {
                commentFlow = if (currentSettings.showComments) {
                    container.jikkyo.live(channel.id)
                } else {
                    null
                }
                openPlayer()
            }
        }
    }

    fun openRecording(recording: Recording) {
        viewModelScope.launch {
            val resume = if (currentSettings.resumePlayback) {
                settingsStore.positionFor(recording.id)
            } else 0L
            val durationMs = recording.durationSeconds * 1000
            if (resume > 10_000L && resume < durationMs - 30_000L) {
                pendingResume = PendingResume(recording, resume)
            } else {
                startRecording(recording, 0L)
            }
        }
    }

    fun confirmResume() {
        val pending = pendingResume ?: return
        pendingResume = null
        startRecording(pending.recording, pending.positionMs)
    }

    fun startFromBeginning() {
        val pending = pendingResume ?: return
        pendingResume = null
        startRecording(pending.recording, 0L)
    }

    fun dismissResume() {
        pendingResume = null
    }

    private fun startRecording(recording: Recording, positionMs: Long) {
        viewModelScope.launch {
            if (playback.playRecording(recording, currentSettings.defaultQuality, positionMs)) {
                commentFlow = if (currentSettings.showComments) {
                    recordedComments(recording.id)
                } else {
                    null
                }
                openPlayer()
            }
        }
    }

    /** 録画の再生位置に合わせて過去ログコメントを流す。 */
    private fun recordedComments(videoId: Int): Flow<JikkyoComment> = flow {
        val comments = container.jikkyo.recorded(videoId).sortedBy { it.timeSeconds }
        var index = 0
        while (true) {
            val positionSeconds = playback.positionMs() / 1000.0
            while (index < comments.size && comments[index].timeSeconds <= positionSeconds) {
                emit(comments[index])
                index += 1
            }
            delay(250)
        }
    }

    fun setQuality(quality: Quality) {
        viewModelScope.launch { playback.switchQuality(quality) }
    }

    fun selectAudioGroup(index: Int) {
        playback.selectAudioGroup(index)
    }

    fun togglePlayPause() = playback.togglePlayPause()

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun seekBy(deltaMs: Long) = playback.seekBy(deltaMs)

    fun retryPlayback() = playback.retry()

    fun stopPlayback() {
        playback.stop()
        commentFlow = null
        showPlayer = false
        playback.playerVisible = false
        viewModelScope.launch { resumePositions = settingsStore.positions() }
    }

    fun closePlayer() {
        showPlayer = false
        playback.playerVisible = false
    }

    /** ライブ視聴中に前後のチャンネルへ移動する。 */
    fun zap(delta: Int) {
        val channels = (channelsState as? LoadState.Success)?.value.orEmpty()
        if (channels.isEmpty()) return
        val currentId = nowPlaying.value?.channelId
        val index = channels.indexOfFirst { it.displayId == currentId }
        val nextIndex = if (index < 0) 0 else (index + delta + channels.size) % channels.size
        playChannel(channels[nextIndex])
    }

    // ---- 設定 ----

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDynamicColor(enabled) }
    }

    fun setDefaultQuality(quality: Quality) {
        viewModelScope.launch { settingsStore.setDefaultQuality(quality) }
    }

    fun setWarnOnMobileData(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setWarnOnMobileData(enabled) }
    }

    fun setResumePlayback(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setResumePlayback(enabled) }
    }

    fun setShowComments(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setShowComments(enabled) }
        if (!enabled) {
            commentFlow = null
        } else {
            val now = nowPlaying.value ?: return
            commentFlow = when {
                now.isLive && now.channelId != null -> {
                    val channel = (channelsState as? LoadState.Success)?.value
                        ?.firstOrNull { it.displayId == now.channelId }
                    channel?.let { container.jikkyo.live(it.id) }
                }
                now.videoId != null -> recordedComments(now.videoId)
                else -> null
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearChannelCache()
            channelsState = LoadState.Idle
            guideCache.clear()
            guideState = LoadState.Idle
        }
    }

    suspend fun testConnection(rawUrl: String): Result<ServerInfo> = repository.testConnection(rawUrl)

    suspend fun completeSetup(rawUrl: String): Boolean {
        val normalized = KonomiApi.normalizeServerUrl(rawUrl) ?: return false
        repository.saveServerUrl(normalized)
        refreshAll()
        return true
    }

    fun clearServer() {
        viewModelScope.launch {
            playback.stop()
            repository.clearServer()
            channelsState = LoadState.Idle
            guideState = LoadState.Idle
            recordingsState = LoadState.Idle
            reservationsState = LoadState.Idle
            guideCache.clear()
            commentFlow = null
            showPlayer = false
            playback.playerVisible = false
            tab = Tab.LIVE
        }
    }
}
