package io.github.satoru2727.miteru.core.settings

import android.content.Context
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
        val SHOW_SUBCHANNELS = booleanPreferencesKey("show_subchannels")
        val AUTO_SKIP_CM = booleanPreferencesKey("auto_skip_cm")
        val WATCHED_IDS = stringPreferencesKey("watched_video_ids")
        val SERVERS = stringPreferencesKey("servers")
        val CHANNELS_JSON = stringPreferencesKey("channels_cache_json")
        val CHANNELS_AT = longPreferencesKey("channels_cache_at")
        val POSITIONS = stringPreferencesKey("resume_positions")
        val PLAYBACK_ACTIVE = booleanPreferencesKey("playback_active")
    }

    val settings: Flow<Settings> = context.miteruDataStore.data.map { prefs ->
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
            showSubchannels = prefs[Keys.SHOW_SUBCHANNELS] ?: true,
            autoSkipCm = prefs[Keys.AUTO_SKIP_CM] ?: false,
            watchedVideoIds = decodeIdSet(prefs[Keys.WATCHED_IDS]),
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

    suspend fun setShowSubchannels(enabled: Boolean) {
        context.miteruDataStore.edit { it[Keys.SHOW_SUBCHANNELS] = enabled }
    }

    suspend fun setAutoSkipCm(enabled: Boolean) {
        context.miteruDataStore.edit { it[Keys.AUTO_SKIP_CM] = enabled }
    }

    suspend fun setServers(servers: List<SavedServer>) {
        context.miteruDataStore.edit { it[Keys.SERVERS] = encodeServers(servers) }
    }

    suspend fun watchedIds(): Set<Int> =
        decodeIdSet(context.miteruDataStore.data.first()[Keys.WATCHED_IDS])

    /** 録画を視聴済みとして記録する（未視聴フィルタ用）。 */
    suspend fun markWatched(videoId: Int) {
        writeMutex.withLock {
            val current = watchedIds().toMutableSet()
            if (current.add(videoId)) {
                val encoded = encodeIdSet(current)
                context.miteruDataStore.edit { prefs -> prefs[Keys.WATCHED_IDS] = encoded }
            }
        }
    }

    /** 視聴済みマークを取り消す。 */
    suspend fun unmarkWatched(videoId: Int) {
        writeMutex.withLock {
            val current = watchedIds().toMutableSet()
            if (current.remove(videoId)) {
                val encoded = encodeIdSet(current)
                context.miteruDataStore.edit { prefs ->
                    if (encoded.isBlank()) prefs.remove(Keys.WATCHED_IDS) else prefs[Keys.WATCHED_IDS] = encoded
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

    suspend fun positions(): Map<Int, Long> {
        val raw = context.miteruDataStore.data.first()[Keys.POSITIONS].orEmpty()
        if (raw.isBlank()) return emptyMap()
        return raw.split(';').mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return@mapNotNull null
            val id = parts[0].toIntOrNull() ?: return@mapNotNull null
            val position = parts[1].toLongOrNull() ?: return@mapNotNull null
            id to position
        }.toMap()
    }

    suspend fun positionFor(videoId: Int): Long = positions()[videoId] ?: 0L

    suspend fun savePosition(videoId: Int, positionMs: Long) {
        writeMutex.withLock {
            val current = positions().toMutableMap()
            if (positionMs <= 0L) current.remove(videoId) else current[videoId] = positionMs
            val encoded = current.entries.joinToString(";") { "${it.key}:${it.value}" }
            context.miteruDataStore.edit { prefs ->
                if (encoded.isBlank()) prefs.remove(Keys.POSITIONS) else prefs[Keys.POSITIONS] = encoded
            }
        }
    }

    suspend fun clearPosition(videoId: Int) {
        writeMutex.withLock {
            val current = positions().toMutableMap()
            current.remove(videoId)
            val encoded = current.entries.joinToString(";") { "${it.key}:${it.value}" }
            context.miteruDataStore.edit { prefs ->
                if (encoded.isBlank()) prefs.remove(Keys.POSITIONS) else prefs[Keys.POSITIONS] = encoded
            }
        }
    }
}

private fun decodeIdSet(raw: String?): Set<Int> =
    raw.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

private fun encodeIdSet(ids: Set<Int>): String = ids.joinToString(",")

/** 保存済みサーバー（名前 + URL）。複数サーバーの切り替えに使う。 */
data class SavedServer(val name: String, val url: String)

private fun encodeServers(servers: List<SavedServer>): String =
    servers.joinToString("\u001E") { "${it.name}\u001F${it.url}" }

private fun decodeServers(raw: String?): List<SavedServer> =
    raw.orEmpty().split('\u001E').mapNotNull { entry ->
        val separator = entry.indexOf('\u001F')
        if (separator <= 0) return@mapNotNull null
        SavedServer(entry.substring(0, separator), entry.substring(separator + 1))
    }.filter { it.url.isNotBlank() }
