package io.github.satoru2727.miteru.core.model

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
) {
    val displayTitle: String
        get() = if (!subtitle.isNullOrBlank() && subtitle != title) "$title — $subtitle" else title
}

data class RecordingsPage(
    val total: Int,
    val items: List<Recording>,
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
