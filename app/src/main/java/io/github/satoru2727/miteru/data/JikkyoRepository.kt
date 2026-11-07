package io.github.satoru2727.miteru.data

import io.github.satoru2727.miteru.core.log.MiteruLog
import io.github.satoru2727.miteru.core.model.JikkyoComment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** ライブコメント投稿の結果。 */
sealed interface JikkyoPostResult {
    data object Success : JikkyoPostResult
    data class Failure(val message: String) : JikkyoPostResult
}

/**
 * ニコニコ実況（NX-Jikkyo）のコメント取得と投稿。
 *
 * ライブは KonomiTV の `/api/channels/{id}/jikkyo` が返す WebSocket URL を使い、
 * 視聴セッション（座席維持・thread 情報・コメント投稿）とコメントセッション（chat 受信）の 2 本を開く。
 * コメント投稿は NX-Jikkyo のニコニコ生放送互換 API に 184 匿名で送る（KonomiTV 本体と同じ）。
 * 録画は `/api/videos/{id}/jikkyo` の過去ログを REST で取得する（投稿はライブのみ）。
 */
class JikkyoRepository(
    private val repository: KonomiRepository,
    private val client: OkHttpClient,
    private val json: Json,
) {
    /** ライブの視聴セッションが確立し、コメント投稿が可能か。 */
    private val _livePostReady = MutableStateFlow(false)
    val livePostReady: StateFlow<Boolean> = _livePostReady.asStateFlow()

    /** 現在アクティブなライブコメントセッション（投稿に使う）。 */
    @Volatile
    private var liveSession: LiveCommentSession? = null

    private class LiveCommentSession {
        @Volatile
        var watchSocket: WebSocket? = null

        @Volatile
        var vposBaseTimeMs: Long? = null

        @Volatile
        var pendingPost: CompletableDeferred<JikkyoPostResult>? = null
    }

    suspend fun recorded(videoId: Int): List<JikkyoComment> {
        val api = repository.api() ?: return emptyList()
        return runCatching { api.jikkyoComments(videoId) }.getOrDefault(emptyList())
    }

    fun live(channelId: String): Flow<JikkyoComment> = callbackFlow {
        val api = repository.api()
        if (api == null) {
            close()
            return@callbackFlow
        }
        val info = runCatching { api.jikkyoInfo(channelId) }.getOrNull()
        val watchUrl = info?.watch_session_url
        if (watchUrl == null) {
            MiteruLog.i("Jikkyo", "comments unavailable for channel=$channelId")
            close()
            return@callbackFlow
        }
        MiteruLog.i("Jikkyo", "connecting watch session (channel=$channelId)")

        val session = LiveCommentSession()
        liveSession = session
        var commentSocket: WebSocket? = null
        var seatJob: Job? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun openComments(url: String, threadId: String, threadKey: String?) {
            if (commentSocket != null) return
            commentSocket = client.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        MiteruLog.e("Jikkyo", "comment session failed (HTTP ${response?.code})", t)
                    }

                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        MiteruLog.i("Jikkyo", "comment session connected")
                        val payload = buildJsonArray {
                            add(buildJsonObject { put("ping", buildJsonObject { put("content", "rs:0") }) })
                            add(buildJsonObject { put("ping", buildJsonObject { put("content", "ps:0") }) })
                            add(
                                buildJsonObject {
                                    put(
                                        "thread",
                                        buildJsonObject {
                                            put("version", "20061206")
                                            put("thread", threadId)
                                            if (threadKey != null) put("threadkey", threadKey)
                                            put("user_id", "")
                                            put("res_from", -100)
                                        },
                                    )
                                },
                            )
                            add(buildJsonObject { put("ping", buildJsonObject { put("content", "pf:0") }) })
                            add(buildJsonObject { put("ping", buildJsonObject { put("content", "rf:0") }) })
                        }
                        webSocket.send(payload.toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val chat = runCatching {
                            json.parseToJsonElement(text).jsonObject["chat"]?.jsonObject
                        }.getOrNull() ?: return
                        val content = chat["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (content.isBlank()) return
                        val mail = chat["mail"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val (type, size, color) = parseMail(mail)
                        val userId = chat["user_id"]?.jsonPrimitive?.contentOrNull
                            ?: chat["hashed_user_id"]?.jsonPrimitive?.contentOrNull
                        trySend(JikkyoComment(content, type, size, color, 0.0, userId))
                    }
                },
            )
        }

        val watchSocket = client.newWebSocket(
            Request.Builder().url(watchUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"startWatching","data":{"reconnect":false}}""")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    MiteruLog.e("Jikkyo", "watch session failed (HTTP ${response?.code})", t)
                    completePendingPost(session, JikkyoPostResult.Failure("コメントサーバーとの接続が切断されました"))
                    if (liveSession === session) {
                        _livePostReady.value = false
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                    val data = obj["data"]?.jsonObject
                    when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                        "ping" -> webSocket.send("""{"type":"pong"}""")

                        "seat" -> {
                            val interval = data
                                ?.get("keepIntervalSec")?.jsonPrimitive?.intOrNull ?: 20
                            seatJob?.cancel()
                            seatJob = scope.launch {
                                while (isActive) {
                                    delay(interval.coerceAtLeast(5) * 1000L)
                                    webSocket.send("""{"type":"keepSeat"}""")
                                }
                            }
                        }

                        "room" -> {
                            if (data == null) return
                            captureVposBase(session, data)
                            val server = data["messageServer"]?.jsonObject
                                ?.get("uri")?.jsonPrimitive?.contentOrNull
                            val thread = data["threadId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val key = data["yourPostKey"]?.jsonPrimitive?.contentOrNull
                            if (server != null) openComments(server, thread, key)
                        }

                        "messageServer" -> {
                            if (data == null) return
                            captureVposBase(session, data)
                            val server = info.comment_session_url ?: return
                            val hashed = data["hashedUserId"]?.jsonPrimitive?.contentOrNull
                            openComments(server, "", hashed?.let { "nicolive:$it" })
                        }

                        "postCommentResult" -> {
                            MiteruLog.d("Jikkyo", "comment posted")
                            completePendingPost(session, JikkyoPostResult.Success)
                        }

                        "error" -> {
                            val code = data?.get("code")?.jsonPrimitive?.contentOrNull
                            when (code) {
                                "COMMENT_POST_NOT_ALLOWED" ->
                                    completePendingPost(session, JikkyoPostResult.Failure("コメントが許可されていません"))

                                "INVALID_MESSAGE" ->
                                    completePendingPost(session, JikkyoPostResult.Failure("コメント内容が無効です"))

                                else -> MiteruLog.w("Jikkyo", "watch session error: $code")
                            }
                        }
                    }
                }
            },
        )
        session.watchSocket = watchSocket

        awaitClose {
            seatJob?.cancel()
            watchSocket.close(1000, null)
            commentSocket?.close(1000, null)
            scope.cancel()
            completePendingPost(session, JikkyoPostResult.Failure("コメントサーバーとの接続が切断されました"))
            if (liveSession === session) {
                liveSession = null
                _livePostReady.value = false
            }
        }
    }

    /**
     * ライブにコメントを投稿する（NX-Jikkyo、184 匿名）。
     * 視聴セッションが確立していない場合は Failure を返す。
     */
    suspend fun postLiveComment(
        text: String,
        color: String,
        position: String,
        size: String,
    ): JikkyoPostResult {
        val session = liveSession ?: return JikkyoPostResult.Failure("コメントサーバーに接続されていません")
        val socket = session.watchSocket ?: return JikkyoPostResult.Failure("コメントサーバーに接続されていません")
        val vposBase = session.vposBaseTimeMs ?: return JikkyoPostResult.Failure("コメントサーバーの準備中です")
        if (session.pendingPost?.isActive == true) return JikkyoPostResult.Failure("送信中です")

        val vpos = computeVpos(System.currentTimeMillis(), vposBase)
        val deferred = CompletableDeferred<JikkyoPostResult>()
        session.pendingPost = deferred
        MiteruLog.d("Jikkyo", "posting comment (color=$color position=$position size=$size vpos=$vpos)")

        val sent = socket.send(buildPostCommentPayload(text, color, position, size, vpos).toString())
        if (!sent) {
            if (session.pendingPost === deferred) session.pendingPost = null
            return JikkyoPostResult.Failure("送信に失敗しました")
        }

        val result = withTimeoutOrNull(10_000L) { deferred.await() }
        if (result == null) {
            if (session.pendingPost === deferred) session.pendingPost = null
            return JikkyoPostResult.Failure("応答がありませんでした")
        }
        return result
    }

    private fun captureVposBase(session: LiveCommentSession, data: JsonObject) {
        val raw = data["vposBaseTime"]?.jsonPrimitive?.contentOrNull ?: return
        val millis = parseVposBaseTime(raw) ?: return
        session.vposBaseTimeMs = millis
        if (liveSession === session) {
            _livePostReady.value = true
        }
    }

    private fun completePendingPost(session: LiveCommentSession, result: JikkyoPostResult) {
        val pending = session.pendingPost
        session.pendingPost = null
        if (pending != null && pending.isActive) {
            pending.complete(result)
        }
    }

    private fun parseMail(mail: String): Triple<String, String, String> {
        var type = "right"
        var size = "medium"
        var color = "#FFFFFF"
        mail.split(' ').filter { it.isNotBlank() }.forEach { command ->
            when (command) {
                "ue" -> type = "top"
                "shita" -> type = "bottom"
                "naka" -> type = "right"
                "big", "medium", "small" -> size = command
                else -> {
                    if (command.startsWith("#")) {
                        color = command
                    } else {
                        jikkyoColor(command)?.let { color = it }
                    }
                }
            }
        }
        return Triple(type, size, color)
    }

    private fun jikkyoColor(name: String): String? = when (name) {
        "white" -> "#FFFFFF"
        "red" -> "#FF4B4B"
        "pink" -> "#FF7BAC"
        "orange" -> "#FF9D00"
        "yellow" -> "#FFE100"
        "green" -> "#4BFF4B"
        "cyan" -> "#00E5FF"
        "blue" -> "#4B8BFF"
        "purple" -> "#C77DFF"
        "black" -> "#000000"
        "white2" -> "#CCCC99"
        "red2" -> "#CC0033"
        "pink2" -> "#FF33CC"
        "orange2" -> "#FF6600"
        "yellow2" -> "#999900"
        "green2" -> "#00CC66"
        "cyan2" -> "#00CCCC"
        "blue2" -> "#3399FF"
        "purple2" -> "#6633CC"
        "black2" -> "#666666"
        else -> null
    }
}

// ---- 投稿まわりの純関数（ユニットテスト対象） ----

/** 投稿用の vpos（番組開始からの経過時間、10ms 単位）を計算する。 */
internal fun computeVpos(timestampMs: Long, vposBaseTimeMs: Long): Long =
    ((timestampMs - vposBaseTimeMs) / 10L).coerceAtLeast(0L)

/** NX-Jikkyo の postComment メッセージを組み立てる。 */
internal fun buildPostCommentPayload(
    text: String,
    color: String,
    position: String,
    size: String,
    vpos: Long,
): JsonObject = buildJsonObject {
    put("type", "postComment")
    put(
        "data",
        buildJsonObject {
            put("text", text)
            put("color", color)
            put("position", position)
            put("size", size)
            put("vpos", vpos)
            put("isAnonymous", true)
        },
    )
}

/** vposBaseTime の文字列（ISO 8601、タイムゾーンなしは JST 扱い）をエポックミリ秒に変換する。 */
internal fun parseVposBaseTime(value: String): Long? = runCatching {
    OffsetDateTime.parse(value).toInstant().toEpochMilli()
}.recoverCatching {
    LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli()
}.getOrNull()
