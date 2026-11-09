package io.github.satoru2727.miteru.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import io.github.satoru2727.miteru.core.log.MiteruLog
import io.github.satoru2727.miteru.core.settings.SavedServer
import io.github.satoru2727.miteru.core.settings.SettingsStore
import io.github.satoru2727.miteru.core.settings.findServerForUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * サーバーの接続先候補から、いま使う URL を選ぶ。
 *
 * セルフホストの KonomiTV は「自宅 LAN」「外出先 (ドメイン / Tailscale)」のように
 * 同じサーバーへ複数の入口を持つことが多い。最後に接続できた接続先を優先し、
 * 応答がなければほかの接続先を探して自動で切り替える。
 * 接続先が変わっても視聴位置などはサーバー単位のスコープで引き継がれる。
 */
class ServerConnector(
    private val settings: SettingsStore,
    client: OkHttpClient,
    json: Json,
    private val probe: suspend (String) -> Boolean = makeAddressProbe(client, json),
) {
    private val switchMutex = Mutex()

    /** 直近で応答がなかった接続先とその時刻。短時間のうちに何度も探索しないために使う。 */
    private val recentProbeFailures = mutableMapOf<String, Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _switched = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** 自動で接続先を切り替えたときに、新しい接続先の URL を流す。 */
    val switched: SharedFlow<String> = _switched.asSharedFlow()

    /** 接続先候補から使う URL を選ぶ。見つかった接続先は次回優先されるように記録する。 */
    suspend fun selectAddress(server: SavedServer): String? {
        return switchMutex.withLock {
            val order = candidateOrderFor(server.addresses, settings.lastGoodAddress(server.id))
            val selected = firstReachable(order, this::probeCached) ?: return@withLock null
            settings.setLastGoodAddress(server.id, selected)
            selected
        }
    }

    /**
     * 通信エラーで現在の接続先が使えなくなったときに呼ぶ。
     * ほかの接続先が見つかったら保存して切り替える（切り替えたときだけ true）。
     */
    suspend fun failoverIfNeeded(): Boolean {
        return switchMutex.withLock {
            val snapshot = settings.snapshot()
            val current = snapshot.serverUrl?.trim()?.trimEnd('/') ?: return@withLock false
            val server = findServerForUrl(snapshot.servers, current) ?: return@withLock false
            val lastGood = settings.lastGoodAddress(server.id)
            val candidates = buildList {
                lastGood?.let { if (it != current && it in server.addresses) add(it) }
                addAll(server.addresses.filterNot { it == current || it == lastGood })
            }
            val next = firstReachable(candidates, this::probeCached) ?: return@withLock false
            settings.setLastGoodAddress(server.id, next)
            settings.setServerUrl(next)
            MiteruLog.i("Connector", "failover: $current -> $next")
            _switched.tryEmit(next)
            true
        }
    }

    /**
     * ネットワークが変わったときに接続先を見直す（Wi-Fi に戻ったら自宅 LAN を優先し直すなど）。
     * 優先順はユーザーが並べた登録順。見つかった接続先が現在と違うときだけ切り替える。
     */
    private suspend fun reevaluate() {
        switchMutex.withLock {
            val snapshot = settings.snapshot()
            val current = snapshot.serverUrl?.trim()?.trimEnd('/') ?: return@withLock
            val server = findServerForUrl(snapshot.servers, current) ?: return@withLock
            if (server.addresses.size < 2) return@withLock
            val selected = firstReachable(server.addresses, this::probeCached) ?: return@withLock
            if (selected == current) return@withLock
            settings.setLastGoodAddress(server.id, selected)
            settings.setServerUrl(selected)
            MiteruLog.i("Connector", "reevaluate: $current -> $selected")
            _switched.tryEmit(selected)
        }
    }

    /** ネットワークの変化を監視して、接続先を自動で見直す。アプリ起動時に一度だけ呼ぶ。 */
    fun startNetworkMonitoring(context: Context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        // 切り替え直後に連続して届くイベントをまとめる
                        delay(700)
                        runCatching { reevaluate() }
                    }
                }
            })
        }
    }

    /** 直近で失敗した接続先はすぐには再探索しない（並列リクエストの探索が積み重なるのを防ぐ）。 */
    private suspend fun probeCached(url: String): Boolean {
        val now = System.currentTimeMillis()
        val failedAt = recentProbeFailures[url]
        if (failedAt != null && now - failedAt < PROBE_FAILURE_TTL_MS) return false
        val ok = probe(url)
        if (ok) recentProbeFailures.remove(url) else recentProbeFailures[url] = now
        return ok
    }
}

/** 応答がなかった接続先を、この時間だけ再探索しない (ミリ秒)。 */
private const val PROBE_FAILURE_TTL_MS = 5_000L

/** 接続先候補を、最後に成功した接続先 → 登録順に並べる。 */
internal fun candidateOrderFor(addresses: List<String>, lastGood: String?): List<String> =
    buildList {
        lastGood?.let { if (it in addresses) add(it) }
        addAll(addresses.filterNot { it == lastGood })
    }

/** 候補を順に試し、最初に応答した接続先を返す。 */
internal suspend fun firstReachable(
    candidates: List<String>,
    probe: suspend (String) -> Boolean,
): String? {
    for (candidate in candidates) {
        if (probe(candidate)) return candidate
    }
    return null
}

/** /api/version に短いタイムアウトでアクセスし、応答があるかを確かめる。 */
private fun makeAddressProbe(client: OkHttpClient, json: Json): suspend (String) -> Boolean {
    val probeClient = client.newBuilder()
        .callTimeout(3, TimeUnit.SECONDS)
        .build()
    return { url ->
        runCatching {
            KonomiApi(url.toHttpUrl(), probeClient, json).version()
        }.isSuccess
    }
}
