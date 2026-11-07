package io.github.satoru2727.miteru.ui

import android.graphics.Bitmap
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
import io.github.satoru2727.miteru.core.model.RecordPreset
import io.github.satoru2727.miteru.core.model.Recording
import io.github.satoru2727.miteru.core.model.ReservationItem
import io.github.satoru2727.miteru.core.model.Series
import io.github.satoru2727.miteru.core.model.ServerInfo
import io.github.satoru2727.miteru.core.model.TileInfo
import io.github.satoru2727.miteru.core.model.Timetable
import io.github.satoru2727.miteru.core.net.KonomiApi
import io.github.satoru2727.miteru.core.net.ProgramSearchConditionDto
import io.github.satoru2727.miteru.core.net.RecordSettingsDto
import io.github.satoru2727.miteru.core.net.ReservationConditionDto
import io.github.satoru2727.miteru.core.net.ReservationConditionRequestDto
import io.github.satoru2727.miteru.core.settings.Settings
import io.github.satoru2727.miteru.core.settings.SavedServer
import io.github.satoru2727.miteru.core.settings.ThemeMode
import io.github.satoru2727.miteru.core.util.isMeteredNetwork
import io.github.satoru2727.miteru.data.JikkyoPostResult
import io.github.satoru2727.miteru.data.OfflineItem
import io.github.satoru2727.miteru.ui.player.decodeTileRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

enum class Tab { LIVE, GUIDE, RESERVATIONS, RECORDINGS, SETTINGS }

enum class ChannelFilter { ALL, GR, BS, CS, OTHER }

enum class GuideFilter { ALL, GR, BS, CS }

enum class RecordingFilter { ALL, TS, MP4 }

/** フィルタ用に全件取得するときのページ上限（30 件 × 30 = 900 件）。 */
private const val MAX_RECORDINGS_PAGES = 30

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

    /** 視聴済みの録画 ID（未視聴フィルタ用）。 */
    val watchedIds: StateFlow<Set<Int>> = settingsStore.settings
        .map { it.watchedVideoIds }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** オフライン保存の一覧（保存済み + ダウンロード中）。 */
    val offlineItems: StateFlow<List<OfflineItem>> = container.offline.items
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
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
    var recordPresets by mutableStateOf<List<RecordPreset>>(emptyList())
        private set

    // キーワード自動予約条件（EDCB のみ）
    var conditionsState by mutableStateOf<LoadState<List<ReservationConditionDto>>>(LoadState.Idle)
        private set

    // シリーズ番組
    var seriesState by mutableStateOf<LoadState<List<Series>>>(LoadState.Idle)
        private set
    var seriesQuery by mutableStateOf("")
        private set
    var loadingMoreSeries by mutableStateOf(false)
        private set

    private var seriesPage = 1
    private var seriesTotal = 0
    private var seriesSearchJob: Job? = null

    // 番組検索（EDCB バックエンドのみ）
    var searchState by mutableStateOf<LoadState<List<Program>>>(LoadState.Idle)
        private set
    var searchQuery by mutableStateOf("")
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
    var recordingsUnwatchedOnly by mutableStateOf(false)
        private set
    var recordingsGenre by mutableStateOf<String?>(null)
        private set
    var recordingsAllLoaded by mutableStateOf(false)
        private set
    var recordingsCapped by mutableStateOf(false)
        private set
    var resumePositions by mutableStateOf<Map<Int, Long>>(emptyMap())
        private set
    var pendingResume by mutableStateOf<PendingResume?>(null)
        private set
    var pendingMeteredPlay by mutableStateOf<PendingPlay?>(null)
        private set

    private var recordingsPage = 1
    private var recordingsTotal = 0
    private var recordingsFetchJob: Job? = null
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
        loadRecordPresets()
        loadChannels()
        loadRecordings(refresh = true)
        viewModelScope.launch { resumePositions = settingsStore.positions() }
    }

    fun selectTab(next: Tab) {
        tab = next
        when (next) {
            Tab.GUIDE -> {
                if (guideState is LoadState.Idle) loadGuide()
                loadRecordPresets()
            }
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

    fun loadRecordPresets() {
        if (recordPresets.isNotEmpty()) return
        viewModelScope.launch {
            repository.loadRecordPresets().onSuccess { recordPresets = it }
        }
    }

    /** 番組表の番組を録画予約する。EDCB バックエンドでのみ成功する。 */
    suspend fun reserve(
        program: Program,
        settings: RecordSettingsDto = RecordSettingsDto(),
    ): Result<Unit> =
        repository.addReservation(program.id, settings).onSuccess {
            loadReservations(force = true)
            loadGuide(force = true)
        }

    // ---- 番組検索 ----

    fun onSearchQueryChange(value: String) {
        searchQuery = value
    }

    fun runSearch() {
        val keyword = searchQuery.trim()
        if (keyword.isEmpty()) return
        viewModelScope.launch {
            searchState = LoadState.Loading
            repository.searchPrograms(keyword)
                .onSuccess { searchState = LoadState.Success(it) }
                .onFailure { searchState = LoadState.Failure(it) }
        }
    }

    fun clearSearch() {
        searchQuery = ""
        searchState = LoadState.Idle
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

    // ---- キーワード自動予約条件（EDCB のみ） ----

    fun loadConditions(force: Boolean = false) {
        viewModelScope.launch {
            if (force || conditionsState !is LoadState.Success) conditionsState = LoadState.Loading
            repository.loadReservationConditions()
                .onSuccess { conditionsState = LoadState.Success(it) }
                .onFailure { error ->
                    if (conditionsState !is LoadState.Success) conditionsState = LoadState.Failure(error)
                }
        }
    }

    suspend fun addCondition(
        keyword: String,
        excludeKeyword: String,
        note: String,
        isEnabled: Boolean,
        recordSettings: RecordSettingsDto = RecordSettingsDto(),
    ): Result<Unit> = repository.addReservationCondition(
        ReservationConditionRequestDto(
            program_search_condition = ProgramSearchConditionDto(
                is_enabled = isEnabled,
                keyword = keyword,
                exclude_keyword = excludeKeyword,
                note = note,
            ),
            record_settings = recordSettings,
        ),
    ).onSuccess { loadConditions(force = true) }

    /** UI で扱わないフィールドを保持したまま有効/無効だけを切り替える。 */
    suspend fun setConditionEnabled(condition: ReservationConditionDto, enabled: Boolean): Result<Unit> {
        val updated = condition.copy(
            program_search_condition = condition.program_search_condition.copy(is_enabled = enabled),
        )
        return repository.updateReservationCondition(
            updated.id,
            ReservationConditionRequestDto(updated.program_search_condition, updated.record_settings),
        ).onSuccess { loadConditions(force = true) }
    }

    suspend fun deleteCondition(id: Int): Result<Unit> =
        repository.deleteReservationCondition(id).onSuccess { loadConditions(force = true) }

    // ---- シリーズ番組 ----

    fun onSeriesQueryChange(value: String) {
        seriesQuery = value
        seriesSearchJob?.cancel()
        seriesSearchJob = viewModelScope.launch {
            delay(350)
            loadSeries(refresh = true)
        }
    }

    fun loadSeries(refresh: Boolean = true) {
        if (refresh) seriesPage = 1
        viewModelScope.launch {
            if (refresh) seriesState = LoadState.Loading
            val query = seriesQuery.takeIf { it.isNotBlank() }
            repository.loadSeries(page = seriesPage, query = query)
                .onSuccess { (total, items) ->
                    seriesTotal = total
                    val existing = if (refresh) emptyList() else {
                        (seriesState as? LoadState.Success)?.value.orEmpty()
                    }
                    seriesState = LoadState.Success((existing + items).distinctBy { it.id })
                }
                .onFailure { error ->
                    if (seriesState !is LoadState.Success) seriesState = LoadState.Failure(error)
                }
        }
    }

    fun loadMoreSeries() {
        val loaded = (seriesState as? LoadState.Success)?.value ?: return
        if (loadingMoreSeries || loaded.size >= seriesTotal) return
        loadingMoreSeries = true
        seriesPage += 1
        viewModelScope.launch {
            val query = seriesQuery.takeIf { it.isNotBlank() }
            repository.loadSeries(page = seriesPage, query = query)
                .onSuccess { (_, items) ->
                    val existing = (seriesState as? LoadState.Success)?.value.orEmpty()
                    seriesState = LoadState.Success((existing + items).distinctBy { it.id })
                }
            loadingMoreSeries = false
        }
    }

    // ---- 録画 ----

    fun loadRecordings(refresh: Boolean = true) {
        if (refresh) {
            recordingsPage = 1
            recordingsAllLoaded = false
            recordingsCapped = false
            recordingsFetchJob?.cancel()
            recordingsFetchJob = null
        }
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

    fun changeRecordingsUnwatchedOnly(enabled: Boolean) {
        recordingsUnwatchedOnly = enabled
    }

    fun changeRecordingsGenre(genre: String?) {
        recordingsGenre = genre
    }

    /**
     * 未視聴・ジャンルフィルタは読み込み済みの一覧に対して行うため、
     * フィルタが有効になったら全ページを取得しておく（上限 MAX_RECORDINGS_PAGES ページ）。
     */
    fun ensureAllRecordings() {
        if (recordingsAllLoaded || recordingsFetchJob?.isActive == true) return
        val loaded = (recordingsState as? LoadState.Success)?.value ?: return
        if (loaded.size >= recordingsTotal) {
            recordingsAllLoaded = true
            return
        }
        recordingsFetchJob = viewModelScope.launch {
            while (true) {
                val current = (recordingsState as? LoadState.Success)?.value ?: break
                if (current.size >= recordingsTotal || recordingsPage >= MAX_RECORDINGS_PAGES) break
                recordingsPage += 1
                val query = recordingsQuery.takeIf { it.isNotBlank() }
                val page = repository.loadRecordings(
                    page = recordingsPage,
                    order = recordingsOrder,
                    query = query,
                ).getOrNull() ?: break
                recordingsTotal = page.total
                recordingsState = LoadState.Success(
                    ((recordingsState as? LoadState.Success)?.value.orEmpty() + page.items)
                        .distinctBy { it.id },
                )
            }
            val final = (recordingsState as? LoadState.Success)?.value.orEmpty()
            recordingsAllLoaded = true
            recordingsCapped = final.size < recordingsTotal
        }
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
        commentPostState = CommentPostState.Idle
        viewModelScope.launch {
            if (playback.playLive(channel, currentSettings.defaultQuality)) {
                commentFlow = if (currentSettings.showComments) {
                    mutedFlow(container.jikkyo.live(channel.id))
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
        commentPostState = CommentPostState.Idle
        viewModelScope.launch {
            if (playback.playRecording(recording, currentSettings.defaultQuality, positionMs)) {
                commentFlow = if (currentSettings.showComments) {
                    mutedFlow(recordedComments(recording.id))
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
            // 途中再開時に何百件も一気に流さないよう、3 秒より前のコメントは捨てる
            while (index < comments.size && comments[index].timeSeconds < positionSeconds - 3) {
                index += 1
            }
            while (index < comments.size && comments[index].timeSeconds <= positionSeconds) {
                emit(comments[index])
                index += 1
            }
            delay(250)
        }
    }

    // ---- オフライン保存 ----

    fun downloadRecording(recording: Recording, quality: Quality) {
        container.offline.enqueue(recording, quality)
    }

    fun cancelDownload(videoId: Int) = container.offline.cancel(videoId)

    fun deleteDownload(videoId: Int) = container.offline.delete(videoId)

    fun retryDownload(videoId: Int) = container.offline.retry(videoId)

    /** オフライン保存した録画を再生する。続きから位置があればそこから。 */
    fun playOfflineItem(item: OfflineItem) {
        commentPostState = CommentPostState.Idle
        viewModelScope.launch {
            val resume = if (currentSettings.resumePlayback) settingsStore.positionFor(item.videoId) else 0L
            val durationMs = item.durationSeconds * 1000
            val start = if (resume > 10_000L && resume < durationMs - 30_000L) resume else 0L
            if (playback.playOffline(item, start)) {
                commentFlow = if (currentSettings.showComments) {
                    mutedFlow(recordedComments(item.videoId))
                } else {
                    null
                }
                openPlayer()
            }
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
        commentPostState = CommentPostState.Idle
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
        val all = (channelsState as? LoadState.Success)?.value.orEmpty()
        val channels = if (currentSettings.showSubchannels) all else all.filter { !it.isSubchannel }
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

    fun setCommentScale(scale: Float) {
        viewModelScope.launch { settingsStore.setCommentScale(scale) }
    }

    fun setCommentAlpha(alpha: Float) {
        viewModelScope.launch { settingsStore.setCommentAlpha(alpha) }
    }

    fun setCommentSpeed(speed: String) {
        viewModelScope.launch { settingsStore.setCommentSpeed(speed) }
    }

    fun setCommentNgWords(words: String) {
        viewModelScope.launch { settingsStore.setCommentNgWords(words) }
    }

    fun setShowSubchannels(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setShowSubchannels(enabled) }
    }

    fun setAutoSkipCm(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoSkipCm(enabled) }
    }

    /** NG ワードに一致するコメントを落とす。 */
    private fun muted(text: String): Boolean {
        val words = settings.value?.commentNgWords.orEmpty()
            .split(',', ' ', '　')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return false
        val lower = text.lowercase()
        return words.any { lower.contains(it.lowercase()) }
    }

    private fun mutedFlow(source: Flow<JikkyoComment>): Flow<JikkyoComment> =
        source.filter { !muted(it.text) }

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
                    channel?.let { mutedFlow(container.jikkyo.live(it.id)) }
                }
                now.videoId != null -> mutedFlow(recordedComments(now.videoId))
                else -> null
            }
        }
    }

    // ---- コメント投稿 ----

    /** ライブの視聴セッションが確立し、コメント投稿が可能か。 */
    val liveCommentPostReady: StateFlow<Boolean> get() = container.jikkyo.livePostReady

    /** コメント投稿の状態（送信シートが参照する）。 */
    var commentPostState by mutableStateOf<CommentPostState>(CommentPostState.Idle)
        private set

    fun postLiveComment(text: String, color: String, position: String, size: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 75) return
        if (commentPostState is CommentPostState.Posting) return
        viewModelScope.launch {
            commentPostState = CommentPostState.Posting
            commentPostState = when (
                val result = container.jikkyo.postLiveComment(trimmed, color, position, size)
            ) {
                is JikkyoPostResult.Success -> CommentPostState.Posted
                is JikkyoPostResult.Failure -> CommentPostState.Failed(result.message)
            }
        }
    }

    fun resetCommentPostState() {
        commentPostState = CommentPostState.Idle
    }

    // ---- シークプレビュー（タイルサムネイル） ----

    private val tileMutex = Mutex()
    private var tileSpriteCache: Pair<Int, File>? = null

    /** スプライト画像を事前にダウンロードしておく（失敗しても無視）。 */
    fun prefetchTileSprite(videoId: Int) {
        viewModelScope.launch { runCatching { tileSprite(videoId) } }
    }

    /** 再生位置に対応するタイル画像を切り出して返す。 */
    suspend fun tilePreview(videoId: Int, info: TileInfo, positionMs: Long): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = tileSprite(videoId) ?: return@withContext null
            decodeTileRegion(file, info, positionMs / 1000.0)
        }

    /** スプライト画像をキャッシュへダウンロードして返す。 */
    private suspend fun tileSprite(videoId: Int): File? = withContext(Dispatchers.IO) {
        tileMutex.withLock {
            tileSpriteCache?.let { (cachedId, cachedFile) ->
                if (cachedId == videoId && cachedFile.exists()) return@withLock cachedFile
            }
            val dir = File(container.appContext.cacheDir, "tiles").apply { mkdirs() }
            val file = File(dir, "video_$videoId.webp")
            if (file.exists() && file.length() > 0L) {
                tileSpriteCache = videoId to file
                return@withLock file
            }
            val api = repository.api() ?: return@withLock null
            val request = Request.Builder().url(api.tileThumbnailUrl(videoId)).build()
            val ok = runCatching {
                container.httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    val body = response.body ?: return@use false
                    body.byteStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    true
                }
            }.getOrDefault(false)
            if (!ok) {
                file.delete()
                return@withLock null
            }
            tileSpriteCache = videoId to file
            file
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

    // ---- サーバー管理（複数サーバーの切り替え） ----

    fun switchServer(url: String) {
        viewModelScope.launch {
            if (url == settingsStore.snapshot().serverUrl) return@launch
            playback.stop()
            repository.saveServerUrl(url)
            channelsState = LoadState.Idle
            guideState = LoadState.Idle
            recordingsState = LoadState.Idle
            reservationsState = LoadState.Idle
            conditionsState = LoadState.Idle
            seriesState = LoadState.Idle
            guideCache.clear()
            commentFlow = null
            showPlayer = false
            playback.playerVisible = false
            tab = Tab.LIVE
            refreshAll()
        }
    }

    /** サーバーを一覧に追加して切り替える。URL が不正なら false。 */
    suspend fun addServer(name: String, rawUrl: String): Boolean {
        val normalized = KonomiApi.normalizeServerUrl(rawUrl) ?: return false
        val current = settingsStore.snapshot()
        val base = current.servers.toMutableList()
        val active = current.serverUrl
        if (base.isEmpty() && active != null && active != normalized) {
            // 旧バージョンからの移行: 現在のサーバーを一覧の先頭に残す
            base.add(SavedServer(active, active))
        }
        if (base.none { it.url == normalized }) {
            base.add(SavedServer(name.ifBlank { normalized }, normalized))
        }
        settingsStore.setServers(base)
        switchServer(normalized)
        return true
    }

    fun removeServer(url: String) {
        viewModelScope.launch {
            val current = settingsStore.snapshot()
            if (url == current.serverUrl) return@launch
            settingsStore.setServers(current.servers.filterNot { it.url == url })
        }
    }

    fun clearServer() {
        viewModelScope.launch {
            playback.stop()
            repository.clearServer()
            channelsState = LoadState.Idle
            guideState = LoadState.Idle
            recordingsState = LoadState.Idle
            reservationsState = LoadState.Idle
            conditionsState = LoadState.Idle
            seriesState = LoadState.Idle
            recordingsUnwatchedOnly = false
            recordingsGenre = null
            recordingsAllLoaded = false
            recordingsCapped = false
            guideCache.clear()
            commentFlow = null
            commentPostState = CommentPostState.Idle
            showPlayer = false
            playback.playerVisible = false
            tab = Tab.LIVE
        }
    }
}

/** コメント投稿の UI 状態。 */
sealed interface CommentPostState {
    data object Idle : CommentPostState
    data object Posting : CommentPostState
    data object Posted : CommentPostState
    data class Failed(val message: String) : CommentPostState
}
