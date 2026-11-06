package io.github.satoru2727.miteru.core.net

import io.github.satoru2727.miteru.core.log.MiteruLog
import io.github.satoru2727.miteru.core.model.Channel
import io.github.satoru2727.miteru.core.model.Program
import io.github.satoru2727.miteru.core.model.Quality
import io.github.satoru2727.miteru.core.model.Recording
import io.github.satoru2727.miteru.core.model.JikkyoComment
import io.github.satoru2727.miteru.core.model.RecordPreset
import io.github.satoru2727.miteru.core.model.RecordingsPage
import io.github.satoru2727.miteru.core.model.ReservationsPage
import io.github.satoru2727.miteru.core.model.ServerInfo
import io.github.satoru2727.miteru.core.model.Timetable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(
    val statusCode: Int,
    val detail: String,
    cause: Throwable? = null,
) : IOException(detail.ifBlank { "HTTP $statusCode" }, cause) {
    val isNetworkError: Boolean get() = statusCode == 0
}

/** KonomiTV の REST API クライアント。1 インスタンス = 1 サーバー。 */
class KonomiApi(
    val baseUrl: HttpUrl,
    private val client: OkHttpClient,
    private val json: Json,
) {
    val baseUrlString: String get() = baseUrl.toString().trimEnd('/')

    suspend fun version(): ServerInfo =
        json.decodeFromString<VersionDto>(execute(get("api/version"))).toDomain()

    suspend fun channelsDto(): LiveChannelsDto =
        json.decodeFromString<LiveChannelsDto>(execute(get("api/channels")))

    suspend fun channels(): List<Channel> = channelsDto().toDomain()

    suspend fun timetable(
        start: Instant,
        end: Instant,
        channelType: String? = null,
    ): Timetable {
        val formatter = DateTimeFormatter.ISO_INSTANT
        return json.decodeFromString<TimeTableDto>(
            execute(
                get(
                    "api/programs/timetable",
                    listOf(
                        "start_time" to formatter.format(start),
                        "end_time" to formatter.format(end),
                        "channel_type" to channelType,
                    ),
                ),
            ),
        ).toDomain()
    }

    suspend fun recordings(
        page: Int = 1,
        order: String = "desc",
        query: String? = null,
    ): RecordingsPage {
        val path = if (query.isNullOrBlank()) "api/videos" else "api/videos/search"
        val params = buildList {
            add("order" to order)
            add("page" to page.toString())
            if (!query.isNullOrBlank()) add("query" to query)
        }
        return json.decodeFromString<RecordedProgramsDto>(
            execute(get(path, params)),
        ).toDomain()
    }

    suspend fun jikkyoInfo(channelId: String): JikkyoInfoDto =
        json.decodeFromString(execute(get("api/channels/$channelId/jikkyo")))

    suspend fun jikkyoComments(videoId: Int): List<JikkyoComment> =
        json.decodeFromString<JikkyoCommentsDto>(
            execute(get("api/videos/$videoId/jikkyo")),
        ).comments.map { it.toDomain() }

    suspend fun reservations(): ReservationsPage =
        json.decodeFromString<ReservationsDto>(
            execute(get("api/recording/reservations")),
        ).toDomain()

    suspend fun searchPrograms(keyword: String): List<Program> {
        val body = buildJsonObject { put("keyword", keyword) }.toString()
        return json.decodeFromString<ProgramsDto>(
            execute(jsonRequest("api/programs/search", "POST", body)),
        ).programs.map { it.toDomain() }
    }

    /** 録画設定プリセット一覧（EDCB バックエンドのみ）。 */
    suspend fun recordingPresets(): List<RecordPreset> =
        json.decodeFromString<RecordSettingsPresetsDto>(
            execute(get("api/recording/presets")),
        ).presets.map { RecordPreset(id = it.id, name = it.name, settings = it.record_settings) }

    suspend fun addReservation(programId: String, recordSettings: RecordSettingsDto = RecordSettingsDto()) {
        val payload = ReservationAddRequestDto(
            program_id = programId,
            record_settings = recordSettings,
        )
        execute(
            jsonRequest(
                "api/recording/reservations",
                "POST",
                json.encodeToString(ReservationAddRequestDto.serializer(), payload),
            ),
        )
    }

    suspend fun deleteReservation(id: Int) {
        execute(request(url("api/recording/reservations/$id")).delete().build())
    }

    /** 既存の録画設定を保持したまま有効/無効だけを切り替える。 */
    suspend fun setReservationEnabled(id: Int, enabled: Boolean) {
        val current = json.decodeFromString<ReservationsDto>(
            execute(get("api/recording/reservations")),
        ).reservations.firstOrNull { it.id == id }
            ?: throw ApiException(404, "reservation not found")
        val settings = (current.record_settings ?: RecordSettingsDto()).copy(is_enabled = enabled)
        val payload = ReservationUpdateRequestDto(record_settings = settings)
        execute(
            jsonRequest(
                "api/recording/reservations/$id",
                "PUT",
                json.encodeToString(ReservationUpdateRequestDto.serializer(), payload),
            ),
        )
    }

    suspend fun recording(videoId: Int): Recording =
        json.decodeFromString<RecordedProgramDto>(
            execute(get("api/videos/$videoId")),
        ).toDomain()

    suspend fun keepAlive(videoId: Int, quality: Quality, sessionId: String) {
        execute(
            request(
                url(
                    "api/streams/video/$videoId/${quality.apiName}/keep-alive",
                    listOf("session_id" to sessionId),
                ),
            ).put(ByteArray(0).toRequestBody(null)).build(),
        )
    }

    fun logoUrl(displayChannelId: String): String =
        url("api/channels/$displayChannelId/logo").toString()

    fun thumbnailUrl(videoId: Int): String =
        url("api/videos/$videoId/thumbnail").toString()

    fun liveStreamUrl(channel: Channel, quality: Quality): String =
        url("api/streams/live/${channel.displayId}/${quality.apiName}/mpegts").toString()

    fun videoStreamUrl(videoId: Int, quality: Quality, sessionId: String): String =
        url(
            "api/streams/video/$videoId/${quality.apiName}/playlist",
            listOf("session_id" to sessionId, "type" to "master"),
        ).toString()

    // --- 内部 ---

    private fun get(path: String, params: List<Pair<String, String?>> = emptyList()): Request =
        request(url(path, params)).build()

    private fun request(url: HttpUrl): Request.Builder = Request.Builder().url(url)

    private fun jsonRequest(path: String, method: String, body: String): Request =
        request(url(path))
            .method(method, body.toRequestBody("application/json".toMediaType()))
            .build()

    private fun url(path: String, params: List<Pair<String, String?>> = emptyList()): HttpUrl {
        val builder = baseUrl.newBuilder().addPathSegments(path.trimStart('/'))
        params.forEach { (key, value) -> if (value != null) builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                MiteruLog.e("Api", "request failed: ${MiteruLog.mask(call.request().url.toString())}", e)
                if (continuation.isActive) {
                    continuation.resumeWithException(ApiException(0, e.message ?: "network error", e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        val detail = runCatching {
                            json.decodeFromString<ApiErrorDto>(body).detail
                        }.getOrNull().orEmpty()
                        MiteruLog.e(
                            "Api",
                            "HTTP ${res.code} ${MiteruLog.mask(call.request().url.toString())}: ${detail.ifBlank { res.message }}",
                        )
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                ApiException(res.code, detail.ifBlank { res.message }, null),
                            )
                        }
                    } else if (continuation.isActive) {
                        continuation.resume(body)
                    }
                }
            }
        })
    }

    companion object {
        /** 入力された URL を API のベース URL に正規化する。失敗したら null。 */
        fun normalizeServerUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = when {
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                trimmed.contains("://") -> return null
                else -> "http://$trimmed"
            }
            val parsed = withScheme.toHttpUrlOrNull() ?: return null
            if (parsed.host.isBlank()) return null
            val builder = parsed.newBuilder().query(null).fragment(null)
            var path = parsed.encodedPath.trimEnd('/')
            if (path.endsWith("/api")) path = path.removeSuffix("/api")
            builder.encodedPath(if (path.isBlank()) "/" else path)
            return builder.build().toString().trimEnd('/')
        }
    }
}
