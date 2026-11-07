package io.github.satoru2727.miteru.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.satoru2727.miteru.core.log.MiteruLog
import io.github.satoru2727.miteru.core.model.Quality
import io.github.satoru2727.miteru.core.model.Recording
import io.github.satoru2727.miteru.player.OfflineDownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 録画のオフライン保存（HLS セグメントのミラー）。
 *
 * KonomiTV の /api/streams/video/{id}/{quality}/playlist は VOD のメディアプレイリストで、
 * すべてのセグメント（H.264 + AAC、MPEG-TS）を列挙している。これをそのままローカルへ保存し、
 * 再生時は書き換えたローカルプレイリストを HLS として再生する（サーバー側の変換は不要）。
 * ダウンロード中はプレイヤーと同じ keep-alive を打ち続ける必要がある。
 */
class OfflineDownloader(
    private val context: Context,
    private val repository: KonomiRepository,
    private val store: OfflineStore,
    private val client: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var worker: Job? = null

    @Volatile
    private var activeVideoId: Int? = null

    val items: Flow<List<OfflineItem>> get() = store.items

    init {
        // プロセスが死んで中断されたダウンロードを待機中に戻し、再開する
        scope.launch {
            var resume = false
            store.snapshot().forEach { item ->
                when (item.status) {
                    OfflineStatus.DOWNLOADING -> {
                        store.update(item.videoId) {
                            it.copy(status = OfflineStatus.QUEUED, updatedAt = System.currentTimeMillis())
                        }
                        resume = true
                    }
                    OfflineStatus.QUEUED -> resume = true
                    else -> Unit
                }
            }
            if (resume) {
                startService()
                ensureWorker()
            }
        }
    }

    /** ダウンロードをキューに追加する。既にダウンロード中/完了なら何もしない。 */
    fun enqueue(recording: Recording, quality: Quality) {
        scope.launch {
            val existing = store.snapshot().firstOrNull { it.videoId == recording.id }
            if (existing != null && (existing.isActive || existing.isCompleted)) return@launch
            deleteFiles(recording.id)
            store.upsert(
                OfflineItem(
                    videoId = recording.id,
                    title = recording.title,
                    subtitle = recording.subtitle,
                    channelName = recording.channelName,
                    quality = quality.apiName,
                    durationSeconds = recording.durationSeconds,
                    status = OfflineStatus.QUEUED,
                    cmSections = recording.cmSections,
                    tileInfo = recording.tileInfo,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            startService()
            ensureWorker()
        }
    }

    /** 進行中/待機中のダウンロードを取り消して削除する。 */
    fun cancel(videoId: Int) {
        scope.launch {
            if (activeVideoId == videoId) {
                worker?.cancel()
                worker = null
            }
            deleteFiles(videoId)
            store.remove(videoId)
            ensureWorker()
        }
    }

    /** 完了済み（または失敗済み）のダウンロードを削除する。 */
    fun delete(videoId: Int) {
        scope.launch {
            deleteFiles(videoId)
            store.remove(videoId)
        }
    }

    /** 失敗したダウンロードを最初からやり直す。 */
    fun retry(videoId: Int) {
        scope.launch {
            deleteFiles(videoId)
            store.update(videoId) {
                it.copy(
                    status = OfflineStatus.QUEUED,
                    error = null,
                    segmentsDone = 0,
                    segmentsTotal = 0,
                    bytes = 0,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            startService()
            ensureWorker()
        }
    }

    fun dirFor(videoId: Int): File = File(context.filesDir, "offline/$videoId")

    fun playlistFile(videoId: Int): File = File(dirFor(videoId), "playlist.m3u8")

    // ---- 内部 ----

    private fun startService() {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OfflineDownloadService::class.java),
            )
        }
    }

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch { runQueue() }
    }

    private suspend fun runQueue() {
        while (true) {
            val next = store.snapshot()
                .filter { it.status == OfflineStatus.QUEUED }
                .minByOrNull { it.updatedAt } ?: break
            try {
                download(next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MiteruLog.w("Offline", "download failed video=${next.videoId}: ${e.message}")
                store.update(next.videoId) {
                    it.copy(
                        status = OfflineStatus.FAILED,
                        error = e.message ?: "download failed",
                        updatedAt = System.currentTimeMillis(),
                    )
                }
            }
        }
        activeVideoId = null
    }

    private suspend fun download(item: OfflineItem) {
        val api = repository.api() ?: throw IOException("server not configured")
        val quality = Quality.fromApiName(item.quality) ?: throw IOException("unknown quality")
        val sessionId = UUID.randomUUID().toString().substringBefore('-')

        activeVideoId = item.videoId
        store.update(item.videoId) {
            it.copy(status = OfflineStatus.DOWNLOADING, error = null, updatedAt = System.currentTimeMillis())
        }

        val playlistUrl: HttpUrl = api.videoStreamUrl(item.videoId, quality, sessionId).toHttpUrl()
        val playlist = getText(playlistUrl.toString())
        val segmentUris = playlistSegmentUris(playlist)
        if (segmentUris.isEmpty()) throw IOException("empty playlist")
        val (localPlaylist, _) = rewriteLocalPlaylist(playlist)

        val dir = dirFor(item.videoId)
        dir.deleteRecursively()
        dir.mkdirs()

        store.update(item.videoId) {
            it.copy(segmentsTotal = segmentUris.size, segmentsDone = 0, bytes = 0, updatedAt = System.currentTimeMillis())
        }

        // プレイヤーと同じく、セグメント生成が止まらないよう keep-alive を打ち続ける
        val keepAlive = scope.launch {
            while (true) {
                delay(10_000L)
                runCatching { api.keepAlive(item.videoId, quality, sessionId) }
            }
        }

        try {
            var done = 0
            var bytes = 0L
            var lastReport = 0L
            segmentUris.forEachIndexed { index, uri ->
                val url = playlistUrl.resolve(uri) ?: throw IOException("bad segment uri: $uri")
                val target = File(dir, segmentFileName(index))
                downloadToFile(url.toString(), target)
                bytes += target.length()
                done += 1
                val now = System.currentTimeMillis()
                if (now - lastReport > 1_000 || done == segmentUris.size) {
                    lastReport = now
                    store.update(item.videoId) {
                        it.copy(segmentsDone = done, bytes = bytes, updatedAt = now)
                    }
                }
            }
            File(dir, "playlist.m3u8").writeText(localPlaylist)
            store.update(item.videoId) {
                it.copy(
                    status = OfflineStatus.COMPLETED,
                    segmentsDone = done,
                    bytes = bytes,
                    error = null,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            MiteruLog.d("Offline", "download completed video=${item.videoId} segments=$done bytes=$bytes")
        } catch (e: Exception) {
            throw e
        } finally {
            keepAlive.cancel()
        }
    }

    private suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string() ?: throw IOException("empty body")
        }
    }

    private suspend fun downloadToFile(url: String, target: File) = withContext(Dispatchers.IO) {
        val tmp = File(target.parentFile, "${target.name}.part")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty body")
            tmp.outputStream().use { out -> body.byteStream().copyTo(out, 64 * 1024) }
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun deleteFiles(videoId: Int) {
        runCatching { dirFor(videoId).deleteRecursively() }
    }
}

/** セグメントの保存ファイル名。 */
internal fun segmentFileName(index: Int): String = "seg_%05d.ts".format(index)

/** プレイリストからセグメント URI だけを取り出す（コメント行を除く）。 */
internal fun playlistSegmentUris(playlist: String): List<String> =
    playlist.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toList()

/**
 * プレイリストのセグメント URI をローカルファイル名に書き換える。
 * EXTINF などのタグ行はそのまま保持する。戻り値は (書き換え後の内容, セグメント数)。
 */
internal fun rewriteLocalPlaylist(playlist: String): Pair<String, Int> {
    val out = StringBuilder()
    var count = 0
    playlist.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isEmpty() -> Unit
            line.startsWith("#") -> out.append(line).append('\n')
            else -> {
                out.append(segmentFileName(count)).append('\n')
                count += 1
            }
        }
    }
    return out.toString() to count
}
