package io.github.satoru2727.miteru.data

import io.github.satoru2727.miteru.core.model.JikkyoComment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
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

/**
 * ニコニコ実況（NX-Jikkyo）のコメント取得。
 *
 * ライブは KonomiTV の `/api/channels/{id}/jikkyo` が返す WebSocket URL を使い、
 * 視聴セッション（座席維持・thread 情報）とコメントセッション（chat 受信）の 2 本を開く。
 * 録画は `/api/videos/{id}/jikkyo` の過去ログを REST で取得する。
 */
class JikkyoRepository(
    private val repository: KonomiRepository,
    private val client: OkHttpClient,
    private val json: Json,
) {
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
            close()
            return@callbackFlow
        }

        var watchSocket: WebSocket? = null
        var commentSocket: WebSocket? = null
        var seatJob: Job? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun openComments(url: String, threadId: String, threadKey: String?) {
            if (commentSocket != null) return
            commentSocket = client.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
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
                        trySend(JikkyoComment(content, type, size, color, 0.0))
                    }
                },
            )
        }

        watchSocket = client.newWebSocket(
            Request.Builder().url(watchUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"startWatching","data":{"reconnect":false}}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                    when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                        "ping" -> webSocket.send("""{"type":"pong"}""")
                        "seat" -> {
                            val interval = obj["data"]?.jsonObject
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
                            val data = obj["data"]?.jsonObject ?: return
                            val server = data["messageServer"]?.jsonObject
                                ?.get("uri")?.jsonPrimitive?.contentOrNull
                            val thread = data["threadId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val key = data["yourPostKey"]?.jsonPrimitive?.contentOrNull
                            if (server != null) openComments(server, thread, key)
                        }

                        "messageServer" -> {
                            val data = obj["data"]?.jsonObject ?: return
                            val server = info?.comment_session_url ?: return
                            val hashed = data["hashedUserId"]?.jsonPrimitive?.contentOrNull
                            openComments(server, "", hashed?.let { "nicolive:$it" })
                        }
                    }
                }
            },
        )

        awaitClose {
            seatJob?.cancel()
            watchSocket?.close(1000, null)
            commentSocket?.close(1000, null)
            scope.cancel()
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
