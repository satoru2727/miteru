package io.github.satoru2727.miteru.data

import io.github.satoru2727.miteru.core.model.Channel
import io.github.satoru2727.miteru.core.model.Fetched
import io.github.satoru2727.miteru.core.model.Program
import io.github.satoru2727.miteru.core.model.Recording
import io.github.satoru2727.miteru.core.model.RecordPreset
import io.github.satoru2727.miteru.core.model.RecordingsPage
import io.github.satoru2727.miteru.core.model.ReservationsPage
import io.github.satoru2727.miteru.core.model.ServerInfo
import io.github.satoru2727.miteru.core.model.Series
import io.github.satoru2727.miteru.core.model.Timetable
import io.github.satoru2727.miteru.core.net.KonomiApi
import io.github.satoru2727.miteru.core.net.ApiException
import io.github.satoru2727.miteru.core.net.RecordSettingsDto
import io.github.satoru2727.miteru.core.net.ReservationConditionDto
import io.github.satoru2727.miteru.core.net.ReservationConditionRequestDto
import io.github.satoru2727.miteru.core.net.LiveChannelsDto
import io.github.satoru2727.miteru.core.net.ServerConnector
import io.github.satoru2727.miteru.core.net.toDomain
import io.github.satoru2727.miteru.core.settings.SavedServer
import io.github.satoru2727.miteru.core.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.Instant

/**
 * KonomiTV へのアクセスをまとめる。サーバー URL が変わったら API クライアントを作り直す。
 * 通信エラー (接続不可) のときは、同じサーバーの別の接続先への切り替えを試して 1 回だけやり直す。
 * チャンネル一覧だけは DataStore にキャッシュし、圏外でも最後の一覧を表示できるようにする。
 */
class KonomiRepository(
    private val settings: SettingsStore,
    private val client: OkHttpClient,
    private val json: Json,
    private val connector: ServerConnector,
) {
    private val apiLock = Mutex()
    private var cachedApi: KonomiApi? = null
    private var cachedApiUrl: String? = null

    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()

    private val _lastChannelRefresh = MutableStateFlow(0L)
    val lastChannelRefresh: StateFlow<Long> = _lastChannelRefresh.asStateFlow()

    /** 自動で接続先が切り替わったときの新しい URL（UI で控えめに知らせる用）。 */
    val switchedNotice: SharedFlow<String> = connector.switched

    suspend fun api(): KonomiApi? {
        val url = settings.snapshot().serverUrl ?: return null
        return apiLock.withLock {
            val existing = cachedApi
            if (existing != null && cachedApiUrl == url) {
                existing
            } else {
                val created = KonomiApi(url.toHttpUrl(), client, json)
                cachedApi = created
                cachedApiUrl = url
                created
            }
        }
    }

    /** 接続先候補から、いま使う URL を選ぶ（サーバーの切り替え時に使う）。 */
    suspend fun selectAddressFor(server: SavedServer): String? = connector.selectAddress(server)

    /** 通信エラーで現在の接続先が使えないときに呼ぶ。別の接続先へ切り替えたときだけ true。 */
    suspend fun failoverIfNeeded(): Boolean = connector.failoverIfNeeded()

    /**
     * 通信エラー (接続不可) のときだけ、別の接続先への切り替えを試して 1 回だけやり直す。
     * HTTP エラー (4xx/5xx) は切り替えの対象にしない。
     */
    private suspend fun <T> withFailover(block: suspend (KonomiApi) -> T): T {
        val first = api() ?: throw IllegalStateException("server not configured")
        val firstUrl = first.baseUrlString
        try {
            return block(first)
        } catch (e: ApiException) {
            if (!e.isNetworkError) throw e
            // ほかのリクエストが先に切り替えを済ませている場合もあるため、
            // 切り替えの成否に関わらず、接続先が変わっていれば 1 回だけやり直す
            connector.failoverIfNeeded()
            val retried = api() ?: throw e
            if (retried.baseUrlString == firstUrl) throw e
            return block(retried)
        }
    }

    /** 接続テスト。成功してもサーバー URL はまだ保存しない。 */
    suspend fun testConnection(rawUrl: String): Result<ServerInfo> {
        val normalized = KonomiApi.normalizeServerUrl(rawUrl)
            ?: return Result.failure(IllegalArgumentException("invalid server url"))
        return runCatching { KonomiApi(normalized.toHttpUrl(), client, json).version() }
    }

    suspend fun saveServerUrl(url: String) {
        settings.setServerUrl(url)
        _serverInfo.value = null
    }

    suspend fun clearServer() {
        settings.setServerUrl(null)
        _serverInfo.value = null
        apiLock.withLock {
            cachedApi = null
            cachedApiUrl = null
        }
    }

    suspend fun refreshServerInfo(): Result<ServerInfo> =
        runCatching { withFailover { it.version() } }
            .onSuccess { _serverInfo.value = it }

    suspend fun loadChannels(): Result<Fetched<List<Channel>>> {
        return try {
            val dto = withFailover { it.channelsDto() }
            val now = System.currentTimeMillis()
            settings.cacheChannels(json.encodeToString(LiveChannelsDto.serializer(), dto), now)
            _lastChannelRefresh.value = now
            Result.success(Fetched(dto.toDomain()))
        } catch (e: Exception) {
            android.util.Log.e("Miteru", "loadChannels failed", e)
            val cache = settings.cachedChannels()
            if (cache != null) {
                val decoded = runCatching {
                    json.decodeFromString(LiveChannelsDto.serializer(), cache.json).toDomain()
                }.getOrNull()
                if (decoded != null) {
                    _lastChannelRefresh.value = cache.cachedAt
                    return Result.success(Fetched(decoded, fromCache = true, cachedAt = cache.cachedAt))
                }
            }
            Result.failure(e)
        }
    }

    suspend fun loadTimetable(
        start: Instant,
        end: Instant,
        channelType: String? = null,
    ): Result<Timetable> {
        return runCatching { withFailover { it.timetable(start, end, channelType) } }
    }

    suspend fun loadRecordings(
        page: Int = 1,
        order: String = "desc",
        query: String? = null,
    ): Result<RecordingsPage> {
        return runCatching { withFailover { it.recordings(page, order, query) } }
    }

    /** 続きから再生用: 指定した ID の録画だけをまとめて取得する。 */
    suspend fun loadRecordingsByIds(ids: List<Int>): List<Recording> {
        if (ids.isEmpty()) return emptyList()
        return runCatching { withFailover { it.recordingsByIds(ids).items } }.getOrDefault(emptyList())
    }

    suspend fun loadReservations(): Result<ReservationsPage> {
        return runCatching { withFailover { it.reservations() } }
    }

    suspend fun addReservation(
        programId: String,
        settings: RecordSettingsDto = RecordSettingsDto(),
    ): Result<Unit> {
        return runCatching { withFailover { it.addReservation(programId, settings) } }
    }

    suspend fun searchPrograms(keyword: String): Result<List<Program>> {
        return runCatching { withFailover { it.searchPrograms(keyword) } }
    }

    suspend fun loadRecordPresets(): Result<List<RecordPreset>> {
        return runCatching { withFailover { it.recordingPresets() } }
    }

    suspend fun deleteReservation(id: Int): Result<Unit> {
        return runCatching { withFailover { it.deleteReservation(id) } }
    }

    suspend fun setReservationEnabled(id: Int, enabled: Boolean): Result<Unit> {
        return runCatching { withFailover { it.setReservationEnabled(id, enabled) } }
    }

    suspend fun loadRecording(id: Int): Result<Recording> {
        return runCatching { withFailover { it.recording(id) } }
    }

    // ---- キーワード自動予約条件（EDCB のみ） ----

    suspend fun loadReservationConditions(): Result<List<ReservationConditionDto>> {
        return runCatching { withFailover { it.reservationConditions().reservation_conditions } }
    }

    suspend fun addReservationCondition(body: ReservationConditionRequestDto): Result<Unit> {
        return runCatching { withFailover { it.addReservationCondition(body) } }
    }

    suspend fun updateReservationCondition(id: Int, body: ReservationConditionRequestDto): Result<Unit> {
        return runCatching { withFailover { it.updateReservationCondition(id, body) } }
    }

    suspend fun deleteReservationCondition(id: Int): Result<Unit> {
        return runCatching { withFailover { it.deleteReservationCondition(id) } }
    }

    // ---- シリーズ番組 ----

    suspend fun loadSeries(
        page: Int = 1,
        order: String = "desc",
        query: String? = null,
    ): Result<Pair<Int, List<Series>>> {
        return runCatching {
            val dto = withFailover { api ->
                if (query.isNullOrBlank()) {
                    api.seriesList(page, order)
                } else {
                    api.seriesSearch(query, page, order)
                }
            }
            dto.toDomain()
        }
    }

    suspend fun clearChannelCache() {
        settings.clearCache()
    }
}
