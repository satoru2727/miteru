package io.github.satoru2727.miteru.core.model

import io.github.satoru2727.miteru.core.net.RecordSettingsDto
import kotlinx.serialization.Serializable
import java.time.Instant

/** チャンネル種別。KonomiTV の type フィールドに対応する。 */
enum class ChannelType(val apiValue: String) {
    GR("GR"),
    BS("BS"),
    CS("CS"),
    CATV("CATV"),
    SKY("SKY"),
    BS4K("BS4K");

    companion object {
        fun from(value: String): ChannelType =
            entries.firstOrNull { it.apiValue == value } ?: CATV
    }
}

/** ライブ視聴・録画視聴で共通に使う画質。KonomiTV がネイティブで扱えるものだけを持つ。 */
enum class Quality(val apiName: String, val label: String) {
    Q1080P60("1080p-60fps", "1080p (60fps)"),
    Q1080P("1080p", "1080p"),
    Q810P("810p", "810p"),
    Q720P("720p", "720p"),
    Q540P("540p", "540p"),
    Q480P("480p", "480p"),
    Q360P("360p", "360p"),
    Q240P("240p", "240p");

    companion object {
        fun fromApiName(value: String?): Quality? = entries.firstOrNull { it.apiName == value }
    }
}

data class ServerInfo(
    val version: String,
    val environment: String,
    val backend: String,
    val encoder: String,
) {
    val isEdcb: Boolean get() = backend == "EDCB"
}

data class Genre(val major: String, val middle: String)

data class Reservation(
    val id: Int,
    val status: String,
    val availability: String,
) {
    val isReserved: Boolean get() = status == "Reserved" || status == "Recording"
}

data class Program(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String,
    val detail: Map<String, String>,
    val start: Instant,
    val end: Instant,
    val genres: List<Genre>,
    val isFree: Boolean,
    val reservation: Reservation? = null,
) {
    val durationSeconds: Long get() = (end.epochSecond - start.epochSecond).coerceAtLeast(0)
    val isOnAir: Boolean
        get() {
            val now = Instant.now()
            return !now.isBefore(start) && now.isBefore(end)
        }
}

data class Channel(
    val id: String,
    val displayId: String,
    val name: String,
    val type: ChannelType,
    val remoconId: Int,
    val channelNumber: String,
    val isRadio: Boolean,
    val isSubchannel: Boolean,
    val viewerCount: Int,
    val current: Program?,
    val next: Program?,
    val jikkyoForce: Int? = null,
) {
    val hasCurrentProgram: Boolean get() = current != null
}

data class VideoInfo(
    val status: String,
    val container: String,
    val codec: String,
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val fileSize: Long,
    val durationSeconds: Double,
    val hasStreamChanges: Boolean,
) {
    val isRecording: Boolean get() = status == "Recording"
    val isMissing: Boolean get() = status == "Missing" || status == "AnalysisFailed"
}

/** 録画に埋め込まれた CM 区間（秒、録画先頭基準）。 */
@Serializable
data class CmSection(
    val startSeconds: Double,
    val endSeconds: Double,
)

/** タイルサムネイル（シークプレビュー用）の情報。 */
@Serializable
data class TileInfo(
    val tileWidth: Int,
    val tileHeight: Int,
    val totalTiles: Int,
    val columnCount: Int,
    val intervalSeconds: Double,
) {
    val isUsable: Boolean
        get() = tileWidth > 0 && tileHeight > 0 && totalTiles > 0 && columnCount > 0 && intervalSeconds > 0
}

data class Recording(
    val id: Int,
    val title: String,
    val subtitle: String?,
    val description: String,
    val start: Instant,
    val durationSeconds: Long,
    val channelName: String?,
    val channelId: String?,
    val seriesTitle: String?,
    val episodeNumber: String?,
    val genres: List<Genre>,
    val video: VideoInfo,
    val cmSections: List<CmSection> = emptyList(),
    val tileInfo: TileInfo? = null,
) {
    val displayTitle: String
        get() = if (!subtitle.isNullOrBlank() && subtitle != title) "$title — $subtitle" else title
}

data class RecordingsPage(
    val total: Int,
    val items: List<Recording>,
)

/** シリーズ番組（関連する録画のまとまり）。 */
data class Series(
    val id: Int,
    val title: String,
    val description: String,
    val genres: List<Genre>,
    val periods: List<SeriesPeriod>,
) {
    val recordingCount: Int get() = periods.sumOf { it.recordings.size }
}

/** シリーズ内の放送期間（チャンネル × 期間ごとの録画）。 */
data class SeriesPeriod(
    val channelName: String?,
    val channelId: String?,
    val startDate: String,
    val endDate: String,
    val recordings: List<Recording>,
)

/** 録画予約 1 件。program / channel は KonomiTV 側で解決できなかった場合に null になりうる。 */
data class ReservationItem(
    val id: Int,
    val channel: Channel?,
    val program: Program?,
    val isRecording: Boolean,
    val availability: String,
    val comment: String,
    val estimatedFileSize: Long,
    val isEnabled: Boolean,
    val priority: Int,
) {
    val isPartial: Boolean get() = availability == "Partial"
    val isUnavailable: Boolean get() = availability == "Unavailable"
}

/** 録画設定プリセット。設定本体は EDCB の値をそのまま保持する。 */
data class RecordPreset(
    val id: Int,
    val name: String,
    val settings: RecordSettingsDto,
)

data class JikkyoComment(
    val text: String,
    val type: String,
    val size: String,
    val color: String,
    val timeSeconds: Double,
)

data class ReservationsPage(
    val total: Int,
    val items: List<ReservationItem>,
)

data class TimetableChannel(
    val channel: Channel,
    val programs: List<Program>,
    val subchannels: List<TimetableChannel> = emptyList(),
)

data class Timetable(
    val channels: List<TimetableChannel>,
    val earliest: Instant,
    val latest: Instant,
)

/** ネットワークから取れたがキャッシュかもしれない、ということを表す薄いラッパー。 */
data class Fetched<T>(
    val value: T,
    val fromCache: Boolean = false,
    val cachedAt: Long = 0L,
)
