package io.github.satoru2727.miteru.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.satoru2727.miteru.core.model.CmSection
import io.github.satoru2727.miteru.core.model.TileInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.offlineDataStore by preferencesDataStore(name = "miteru_offline")

/** オフライン保存の状態。 */
@Serializable
enum class OfflineStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED }

/** オフライン保存された録画 1 件（またはダウンロード中の項目）。 */
@Serializable
data class OfflineItem(
    val videoId: Int,
    val title: String,
    val subtitle: String? = null,
    val channelName: String? = null,
    /** KonomiTV の画質 API 名（例: "720p"）。 */
    val quality: String,
    val durationSeconds: Long = 0,
    val status: OfflineStatus = OfflineStatus.QUEUED,
    val segmentsDone: Int = 0,
    val segmentsTotal: Int = 0,
    val bytes: Long = 0,
    val cmSections: List<CmSection> = emptyList(),
    val tileInfo: TileInfo? = null,
    val error: String? = null,
    val updatedAt: Long = 0L,
) {
    val progress: Float
        get() = if (segmentsTotal > 0) segmentsDone.toFloat() / segmentsTotal else 0f

    val isCompleted: Boolean get() = status == OfflineStatus.COMPLETED
    val isActive: Boolean get() = status == OfflineStatus.QUEUED || status == OfflineStatus.DOWNLOADING
}

/** オフライン保存の一覧を DataStore に永続化する。 */
class OfflineStore(private val context: Context, private val json: Json) {

    private val key = stringPreferencesKey("offline_items")
    private val writeMutex = Mutex()

    val items: Flow<List<OfflineItem>> = context.offlineDataStore.data.map { prefs ->
        decode(prefs[key])
    }

    suspend fun snapshot(): List<OfflineItem> = items.first()

    suspend fun upsert(item: OfflineItem) {
        writeMutex.withLock {
            val current = snapshot().toMutableList()
            val index = current.indexOfFirst { it.videoId == item.videoId }
            if (index >= 0) current[index] = item else current.add(item)
            save(current)
        }
    }

    suspend fun update(videoId: Int, transform: (OfflineItem) -> OfflineItem) {
        writeMutex.withLock {
            val current = snapshot().toMutableList()
            val index = current.indexOfFirst { it.videoId == videoId }
            if (index >= 0) {
                current[index] = transform(current[index])
                save(current)
            }
        }
    }

    suspend fun remove(videoId: Int) {
        writeMutex.withLock {
            save(snapshot().filterNot { it.videoId == videoId })
        }
    }

    private fun decode(raw: String?): List<OfflineItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<OfflineItem>>(raw) }.getOrDefault(emptyList())
    }

    private suspend fun save(list: List<OfflineItem>) {
        val raw = json.encodeToString(list)
        context.offlineDataStore.edit { it[key] = raw }
    }
}
