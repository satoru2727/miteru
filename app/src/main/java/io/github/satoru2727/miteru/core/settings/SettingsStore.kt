package io.github.satoru2727.miteru.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
