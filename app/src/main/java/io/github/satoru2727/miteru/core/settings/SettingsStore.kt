package io.github.satoru2727.miteru.core.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.satoru2727.miteru.core.model.Quality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.miteruDataStore by preferencesDataStore(name = "miteru_settings")

/**
 * サーバー URL から設定スコープを作る（12 桁 hex）。
 * 視聴位置・視聴済みフラグをサーバーごとに分離するためにキーの接尾辞として使う。
 */
internal fun serverScope(url: String?): String? {
    val normalized = url?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return null
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
    return digest.take(6).joinToString("") { "%02x".format(it) }
}

/** 接続先 URL に対応するサーバーを探す。接続先の末尾スラッシュは無視する。 */
internal fun findServerForUrl(servers: List<SavedServer>, url: String?): SavedServer? {
    val normalized = url?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return null
    return servers.firstOrNull { server ->
        server.addresses.any { it.trimEnd('/') == normalized }
    }
}

/**
 * 現在の接続先 URL に対応する設定スコープを解決する。
 * 同じサーバーに複数の接続先 (自宅 LAN と外出先など) があっても、
 * 使う接続先が変わっただけで視聴位置などのデータが分断されないよう、
 * URL ではなくサーバーの ID をスコープに使う。
 */
internal fun scopeFor(servers: List<SavedServer>, url: String?): String? =
    findServerForUrl(servers, url)?.id?.takeIf { it.isNotBlank() } ?: serverScope(url)

/** 新しいサーバーを作る。ID は最初の接続先から導出し、保存後は固定される。 */
internal fun newSavedServer(name: String, url: String): SavedServer =
    SavedServer(
        id = serverScope(url) ?: url,
        name = name.ifBlank { url },
        addresses = listOf(url),
    )

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class Settings(
    val serverUrl: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultQuality: Quality = Quality.Q1080P,
    val warnOnMobileData: Boolean = true,
    val resumePlayback: Boolean = true,
    val showComments: Boolean = true,
    val commentScale: Float = 1f,
    val commentAlpha: Float = 1f,
    val commentSpeed: String = "normal",
    val commentNgWords: String = "",
    val commentNgUsers: String = "",
    val showSubchannels: Boolean = true,
    val autoSkipCm: Boolean = false,
    val watchedVideoIds: Set<Int> = emptySet(),
    val servers: List<SavedServer> = emptyList(),
    val cachedChannelsAt: Long = 0L,
    val playbackActive: Boolean = false,
) {
    val isConfigured: Boolean get() = !serverUrl.isNullOrBlank()
}

data class CachedChannels(
    val json: String,
    val cachedAt: Long,
)

class SettingsStore(private val context: Context) {

    private val writeMutex = Mutex()

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val QUALITY = stringPreferencesKey("default_quality")
        val WARN_MOBILE = booleanPreferencesKey("warn_mobile")
        val RESUME = booleanPreferencesKey("resume_playback")
        val SHOW_COMMENTS = booleanPreferencesKey("show_comments")
        val COMMENT_SCALE = floatPreferencesKey("comment_scale")
        val COMMENT_ALPHA = floatPreferencesKey("comment_alpha")
        val COMMENT_SPEED = stringPreferencesKey("comment_speed")
        val COMMENT_NG = stringPreferencesKey("comment_ng_words")
        val COMMENT_NG_USERS = stringPreferencesKey("comment_ng_users")
        val SHOW_SUBCHANNELS = booleanPreferencesKey("show_subchannels")
        val AUTO_SKIP_CM = booleanPreferencesKey("auto_skip_cm")
        val WATCHED_IDS = stringPreferencesKey("watched_video_ids")
        val SERVERS = stringPreferencesKey("servers")
        val CHANNELS_JSON = stringPreferencesKey("channels_cache_json")
        val CHANNELS_AT = longPreferencesKey("channels_cache_at")
        val POSITIONS = stringPreferencesKey("resume_positions")
        val PLAYBACK_ACTIVE = booleanPreferencesKey("playback_active")

        /** 旧キー（サーバー分離前）の移行が完了したスコープを記録する。 */
        val LEGACY_MARKER = stringPreferencesKey("legacy_scope_migrated")
    }

    private fun watchedKeyFor(scope: String?) =
        if (scope == null) Keys.WATCHED_IDS else stringPreferencesKey("watched_video_ids@$scope")

    private fun positionsKeyFor(scope: String?) =
        if (scope == null) Keys.POSITIONS else stringPreferencesKey("resume_positions@$scope")

    /** サーバーごとに、最後に接続に成功した接続先を記録する（自動切り替えの優先に使う）。 */
    private fun lastGoodKeyFor(serverId: String) = stringPreferencesKey("last_good_address@$serverId")

    /** スコープ済みキーを優先し、未移行のときだけ旧キーにフォールバックする。 */
    private fun effective(
        prefs: Preferences,
        scope: String?,
        scopedKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>,
    ): String? {
        if (scope == null) return prefs[legacyKey]
        return prefs[scopedKey] ?: if (prefs[Keys.LEGACY_MARKER] != scope) prefs[legacyKey] else null
    }

    private data class Scoped(val scope: String?, val watched: String?, val positions: String?)

    private suspend fun readScoped(): Scoped {
        val prefs = context.miteruDataStore.data.first()
        val scope = scopeFor(decodeServers(prefs[Keys.SERVERS]), prefs[Keys.SERVER_URL])
        return Scoped(
            scope = scope,
            watched = effective(prefs, scope, watchedKeyFor(scope), Keys.WATCHED_IDS),
            positions = effective(prefs, scope, positionsKeyFor(scope), Keys.POSITIONS),
        )
    }

    /** 旧キーのデータを現在のサーバーのスコープへ一度だけ移行する。アプリ起動直後に呼ぶ。 */
    suspend fun ensureLegacyMigrated() {
        writeMutex.withLock {
            migrateLocked()
            ensureServersLocked()
        }
    }

    /** サーバー一覧が空なら現在の接続先から作る（冪等）。接続先の管理の前に呼ぶ。 */
    suspend fun ensureServersListed() {
        writeMutex.withLock { ensureServersLocked() }
    }

    private suspend fun migrateLocked() {
        val prefs = context.miteruDataStore.data.first()
        val scope = scopeFor(decodeServers(prefs[Keys.SERVERS]), prefs[Keys.SERVER_URL]) ?: return
        if (prefs[Keys.LEGACY_MARKER] == scope) return
        val legacyWatched = prefs[Keys.WATCHED_IDS]
        val legacyPositions = prefs[Keys.POSITIONS]
        context.miteruDataStore.edit { p ->
            val wKey = watchedKeyFor(scope)
            val pKey = positionsKeyFor(scope)
            if (legacyWatched != null && p[wKey] == null) p[wKey] = legacyWatched
            if (legacyPositions != null && p[pKey] == null) p[pKey] = legacyPositions
            p.remove(Keys.WATCHED_IDS)
            p.remove(Keys.POSITIONS)
            p[Keys.LEGACY_MARKER] = scope
        }
    }

    /** サーバー一覧がまだ無いとき、現在の接続先から 1 件作る（サーバー一覧の導入前からの移行）。 */
    private suspend fun ensureServersLocked() {
        val prefs = context.miteruDataStore.data.first()
        if (decodeServers(prefs[Keys.SERVERS]).isNotEmpty()) return
        val url = prefs[Keys.SERVER_URL]?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return
        context.miteruDataStore.edit { p ->
            p[Keys.SERVERS] = encodeServers(listOf(newSavedServer(url, url)))
        }
    }

    val settings: Flow<Settings> = context.miteruDataStore.data.map { prefs ->
        val scope = scopeFor(decodeServers(prefs[Keys.SERVERS]), prefs[Keys.SERVER_URL])
        Settings(
            serverUrl = prefs[Keys.SERVER_URL],
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            defaultQuality = Quality.fromApiName(prefs[Keys.QUALITY]) ?: Quality.Q1080P,
            warnOnMobileData = prefs[Keys.WARN_MOBILE] ?: true,
            resumePlayback = prefs[Keys.RESUME] ?: true,
            showComments = prefs[Keys.SHOW_COMMENTS] ?: true,
            commentScale = prefs[Keys.COMMENT_SCALE] ?: 1f,
            commentAlpha = prefs[Keys.COMMENT_ALPHA] ?: 1f,
            commentSpeed = prefs[Keys.COMMENT_SPEED] ?: "normal",
            commentNgWords = prefs[Keys.COMMENT_NG] ?: "",
            commentNgUsers = prefs[Keys.COMMENT_NG_USERS] ?: "",
            showSubchannels = prefs[Keys.SHOW_SUBCHANNELS] ?: true,
            autoSkipCm = prefs[Keys.AUTO_SKIP_CM] ?: false,
            watchedVideoIds = decodeIdSet(
                effective(prefs, scope, watchedKeyFor(scope), Keys.WATCHED_IDS),
            ),
            servers = decodeServers(prefs[Keys.SERVERS]),
            cachedChannelsAt = prefs[Keys.CHANNELS_AT] ?: 0L,
            playbackActive = prefs[Keys.PLAYBACK_ACTIVE] ?: false,
        )
    }

    suspend fun snapshot(): Settings = settings.first()

    suspend fun setServerUrl(url: String?) {
        context.miteruDataStore.edit { prefs ->
            if (url.isNullOrBlank()) prefs.remove(Keys.SERVER_URL) else prefs[Keys.SERVER_URL] = url
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.miteruDataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.miteruDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDefaultQuality(quality: Quality) {
        context.miteruDataStore.edit { it[Keys.QUALITY] = quality.apiName }
    }

    suspend fun setWarnOnMobileData(enabled: Boolean) {
        context.miteruDataStore.edit { it[Keys.WARN_MOBILE] = enabled }
    }

    suspend fun setResumePlayback(enabled: Boolean) {
        context.miteruDataStore.edit { it[Keys.RESUME] = enabled }
    }

    suspend fun setShowComments(enabled: Boolean) {
        context.miteruDataStore.edit { it[Keys.SHOW_COMMENTS] = enabled }
    }

    suspend fun setCommentScale(scale: Float) {
        context.miteruDataStore.edit { it[Keys.COMMENT_SCALE] = scale }
    }

    suspend fun setCommentAlpha(alpha: Float) {
        context.miteruDataStore.edit { it[Keys.COMMENT_ALPHA] = alpha }
    }

    suspend fun setCommentSpeed(speed: String) {
        context.miteruDataStore.edit { it[Keys.COMMENT_SPEED] = speed }
    }

    suspend fun setCommentNgWords(words: String) {
        context.miteruDataStore.edit { it[Keys.COMMENT_NG] = words }
    }

    suspend fun setCommentNgUsers(users: String) {
        context.miteruDataStore.edit { it[Keys.COMMENT_NG_USERS] = users }
    }

    suspend fun setShowSubchannels(enabled: Boolean) {
        context.miteruDataStore.edit { it[Keys.SHOW_SUBCHANNELS] = enabled }
    }

    suspend fun setAutoSkipCm(enabled: Boolean) {
        context.miteruDataStore.edit { it[Keys.AUTO_SKIP_CM] = enabled }
    }

    suspend fun setServers(servers: List<SavedServer>) {
        context.miteruDataStore.edit { it[Keys.SERVERS] = encodeServers(servers) }
    }

    suspend fun lastGoodAddress(serverId: String): String? =
        context.miteruDataStore.data.first()[lastGoodKeyFor(serverId)]

    suspend fun setLastGoodAddress(serverId: String, url: String) {
        context.miteruDataStore.edit { it[lastGoodKeyFor(serverId)] = url }
    }

    suspend fun watchedIds(): Set<Int> =
        decodeIdSet(readScoped().watched)

    /** 録画を視聴済みとして記録する（未視聴フィルタ用）。 */
    suspend fun markWatched(videoId: Int) {
        writeMutex.withLock {
            migrateLocked()
            val snap = readScoped()
            val current = decodeIdSet(snap.watched).toMutableSet()
            if (current.add(videoId)) {
                val encoded = encodeIdSet(current)
                val key = watchedKeyFor(snap.scope)
                context.miteruDataStore.edit { prefs -> prefs[key] = encoded }
            }
        }
    }

    /** 視聴済みマークを取り消す。 */
    suspend fun unmarkWatched(videoId: Int) {
        writeMutex.withLock {
            migrateLocked()
            val snap = readScoped()
            val current = decodeIdSet(snap.watched).toMutableSet()
            if (current.remove(videoId)) {
                val encoded = encodeIdSet(current)
                val key = watchedKeyFor(snap.scope)
                context.miteruDataStore.edit { prefs ->
                    if (encoded.isBlank()) prefs.remove(key) else prefs[key] = encoded
                }
            }
        }
    }

    suspend fun cacheChannels(json: String, cachedAt: Long) {
        context.miteruDataStore.edit { prefs ->
            prefs[Keys.CHANNELS_JSON] = json
            prefs[Keys.CHANNELS_AT] = cachedAt
        }
    }

    suspend fun cachedChannels(): CachedChannels? {
        val prefs = context.miteruDataStore.data.first()
        val json = prefs[Keys.CHANNELS_JSON] ?: return null
        return CachedChannels(json = json, cachedAt = prefs[Keys.CHANNELS_AT] ?: 0L)
    }

    suspend fun setPlaybackActive(active: Boolean) {
        context.miteruDataStore.edit { it[Keys.PLAYBACK_ACTIVE] = active }
    }

    suspend fun clearCache() {
        context.miteruDataStore.edit { prefs ->
            prefs.remove(Keys.CHANNELS_JSON)
            prefs.remove(Keys.CHANNELS_AT)
        }
    }

    suspend fun positions(): Map<Int, Long> = parsePositions(readScoped().positions)

    suspend fun positionFor(videoId: Int): Long = positions()[videoId] ?: 0L

    suspend fun savePosition(videoId: Int, positionMs: Long) {
        writeMutex.withLock {
            migrateLocked()
            val snap = readScoped()
            val current = parsePositions(snap.positions).toMutableMap()
            if (positionMs <= 0L) current.remove(videoId) else current[videoId] = positionMs
            val encoded = current.entries.joinToString(";") { "${it.key}:${it.value}" }
            val key = positionsKeyFor(snap.scope)
            context.miteruDataStore.edit { prefs ->
                if (encoded.isBlank()) prefs.remove(key) else prefs[key] = encoded
            }
        }
    }

    suspend fun clearPosition(videoId: Int) {
        writeMutex.withLock {
            migrateLocked()
            val snap = readScoped()
            val current = parsePositions(snap.positions).toMutableMap()
            current.remove(videoId)
            val encoded = current.entries.joinToString(";") { "${it.key}:${it.value}" }
            val key = positionsKeyFor(snap.scope)
            context.miteruDataStore.edit { prefs ->
                if (encoded.isBlank()) prefs.remove(key) else prefs[key] = encoded
            }
        }
    }
}

private fun parsePositions(raw: String?): Map<Int, Long> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(';').mapNotNull { entry ->
        val parts = entry.split(':')
        if (parts.size != 2) return@mapNotNull null
        val id = parts[0].toIntOrNull() ?: return@mapNotNull null
        val position = parts[1].toLongOrNull() ?: return@mapNotNull null
        id to position
    }.toMap()
}

private fun decodeIdSet(raw: String?): Set<Int> =
    raw.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

private fun encodeIdSet(ids: Set<Int>): String = ids.joinToString(",")

/**
 * 保存済みサーバー（名前 + 接続先候補）。
 * 1 つのサーバーに複数の接続先 (自宅 LAN・外出先のドメイン・Tailscale など) を持てて、
 * 到達できる接続先へ自動で切り替わる。視聴位置などは接続先ではなくサーバー単位で共有される。
 */
data class SavedServer(
    val id: String,
    val name: String,
    val addresses: List<String>,
) {
    /** 最初の接続先（優先順の先頭）。 */
    val primaryUrl: String get() = addresses.first()
}

private const val SERVER_RECORD_SEPARATOR = "\u001E"
private const val SERVER_FIELD_SEPARATOR = "\u001F"
private const val SERVER_ADDRESS_SEPARATOR = "\u001D"

/** サーバー一覧を保存用の文字列にまとめる。 */
internal fun encodeServers(servers: List<SavedServer>): String =
    servers.joinToString(SERVER_RECORD_SEPARATOR) { server ->
        listOf(
            server.id,
            server.name,
            server.addresses.joinToString(SERVER_ADDRESS_SEPARATOR),
        ).joinToString(SERVER_FIELD_SEPARATOR)
    }

/** 保存用の文字列からサーバー一覧を復元する。旧形式 (名前 + 単一 URL) も読める。 */
internal fun decodeServers(raw: String?): List<SavedServer> =
    raw.orEmpty().split(SERVER_RECORD_SEPARATOR).mapNotNull { entry ->
        val fields = entry.split(SERVER_FIELD_SEPARATOR)
        when (fields.size) {
            // 旧形式: 「名前 + URL」。ID は URL から導出する
            2 -> SavedServer(
                id = serverScope(fields[1]) ?: fields[1],
                name = fields[0],
                addresses = listOf(fields[1]),
            )
            // 新形式: 「ID + 名前 + 接続先候補」
            3 -> {
                val addresses = fields[2].split(SERVER_ADDRESS_SEPARATOR).filter { it.isNotBlank() }
                SavedServer(id = fields[0], name = fields[1], addresses = addresses)
            }
            else -> null
        }
    }.filter { it.addresses.isNotEmpty() }
