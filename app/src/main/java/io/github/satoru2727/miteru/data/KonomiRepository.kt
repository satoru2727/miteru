package io.github.satoru2727.miteru.data

import io.github.satoru2727.miteru.core.model.Channel
import io.github.satoru2727.miteru.core.model.Fetched
import io.github.satoru2727.miteru.core.model.Recording
import io.github.satoru2727.miteru.core.model.RecordingsPage
import io.github.satoru2727.miteru.core.model.ReservationsPage
import io.github.satoru2727.miteru.core.model.ServerInfo
import io.github.satoru2727.miteru.core.model.Timetable
import io.github.satoru2727.miteru.core.net.KonomiApi
import io.github.satoru2727.miteru.core.net.LiveChannelsDto
import io.github.satoru2727.miteru.core.net.toDomain
import io.github.satoru2727.miteru.core.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
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
 * チャンネル一覧だけは DataStore にキャッシュし、圏外でも最後の一覧を表示できるようにする。
 */
class KonomiRepository(
    private val settings: SettingsStore,
    private val client: OkHttpClient,
    private val json: Json,
) {
    private val apiLock = Mutex()
    private var cachedApi: KonomiApi? = null
    private var cachedApiUrl: String? = null

    private val _serverInfo = MutableStateFlow<ServerInfo?>(null)
    val serverInfo: StateFlow<ServerInfo?> = _serverInfo.asStateFlow()

    private val _lastChannelRefresh = MutableStateFlow(0L)
    val lastChannelRefresh: StateFlow<Long> = _lastChannelRefresh.asStateFlow()

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

    suspend fun refreshServerInfo(): Result<ServerInfo> {
        val api = api() ?: return Result.failure(IllegalStateException("server not configured"))
        return runCatching { api.version() }.onSuccess { _serverInfo.value = it }
    }

    suspend fun loadChannels(): Result<Fetched<List<Channel>>> {
        val api = api() ?: return Result.failure(IllegalStateException("server not configured"))
        return try {
            val dto = api.channelsDto()
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
        val api = api() ?: return Result.failure(IllegalStateException("server not configured"))
        return runCatching { api.timetable(start, end, channelType) }
    }

    suspend fun loadRecordings(
        page: Int = 1,
        order: String = "desc",
        query: String? = null,
    ): Result<RecordingsPage> {
        val api = api() ?: return Result.failure(IllegalStateException("server not configured"))
        return runCatching { api.recordings(page, order, query) }
    }

    suspend fun loadReservations(): Result<ReservationsPage> {
        val api = api() ?: return Result.failure(IllegalStateException("server not configured"))
        return runCatching { api.reservations() }
    }

    suspend fun addReservation(programId: String): Result<Unit> {
        val api = api() ?: return Result.failure(IllegalStateException("server not configured"))
        return runCatching { api.addReservation(programId) }
    }

    suspend fun deleteReservation(id: Int): Result<Unit> {
        val api = api() ?: return Result.failure(IllegalStateException("server not configured"))
        return runCatching { api.deleteReservation(id) }
    }

    suspend fun setReservationEnabled(id: Int, enabled: Boolean): Result<Unit> {
        val api = api() ?: return Result.failure(IllegalStateException("server not configured"))
        return runCatching { api.setReservationEnabled(id, enabled) }
    }

    suspend fun loadRecording(id: Int): Result<Recording> {
        val api = api() ?: return Result.failure(IllegalStateException("server not configured"))
        return runCatching { api.recording(id) }
    }

    suspend fun clearChannelCache() {
        settings.clearCache()
    }
}
