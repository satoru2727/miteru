package io.github.satoru2727.miteru.core.net

import io.github.satoru2727.miteru.core.model.Channel
import io.github.satoru2727.miteru.core.model.ChannelType
import io.github.satoru2727.miteru.core.model.CmSection
import io.github.satoru2727.miteru.core.model.Genre
import io.github.satoru2727.miteru.core.model.JikkyoComment
import io.github.satoru2727.miteru.core.model.Program
import io.github.satoru2727.miteru.core.model.Recording
import io.github.satoru2727.miteru.core.model.RecordingsPage
import io.github.satoru2727.miteru.core.model.ReservationItem
import io.github.satoru2727.miteru.core.model.ReservationsPage
import io.github.satoru2727.miteru.core.model.Reservation
import io.github.satoru2727.miteru.core.model.Series
import io.github.satoru2727.miteru.core.model.SeriesPeriod
import io.github.satoru2727.miteru.core.model.ServerInfo
import io.github.satoru2727.miteru.core.model.TileInfo
import io.github.satoru2727.miteru.core.model.Timetable
import io.github.satoru2727.miteru.core.model.TimetableChannel
import io.github.satoru2727.miteru.core.model.VideoInfo
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** KonomiTV は ISO8601（+09:00 付き）で日時を返す。 */
fun parseInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toInstant() }
        .recoverCatching { Instant.parse(value) }
        .getOrNull()
}

fun parseInstantOrEpoch(value: String?, fallback: Instant = Instant.EPOCH): Instant =
    parseInstant(value) ?: fallback

fun VersionDto.toDomain(): ServerInfo = ServerInfo(
    version = version,
    environment = environment,
    backend = backend,
    encoder = encoder,
)

fun GenreDto.toDomain(): Genre = Genre(major = major, middle = middle)

fun ReservationDto.toDomain(): Reservation = Reservation(
    id = id,
    status = status,
    availability = recording_availability,
)

fun ReservationItemDto.toDomain(): ReservationItem = ReservationItem(
    id = id,
    channel = channel?.toDomain(),
    program = program?.toDomain(),
    isRecording = is_recording_in_progress,
    availability = recording_availability,
    comment = comment,
    estimatedFileSize = estimated_recording_file_size,
    isEnabled = record_settings?.is_enabled ?: true,
    priority = record_settings?.priority ?: 3,
)

private val jikkyoColors = mapOf(
    "white" to "#FFFFFF", "red" to "#FF4B4B", "pink" to "#FF7BAC",
    "orange" to "#FF9D00", "yellow" to "#FFE100", "green" to "#4BFF4B",
    "cyan" to "#00E5FF", "blue" to "#4B8BFF", "purple" to "#C77DFF",
    "black" to "#000000", "white2" to "#CCCC99", "red2" to "#CC0033",
    "pink2" to "#FF33CC", "orange2" to "#FF6600", "yellow2" to "#999900",
    "green2" to "#00CC66", "cyan2" to "#00CCCC", "blue2" to "#3399FF",
    "purple2" to "#6633CC", "black2" to "#666666",
)

fun JikkyoCommentDto.toDomain(): JikkyoComment = JikkyoComment(
    text = text,
    type = type,
    size = size,
    color = if (color.startsWith("#")) color else jikkyoColors[color] ?: "#FFFFFF",
    timeSeconds = time,
    userId = author.takeIf { it.isNotBlank() },
)

fun ReservationsDto.toDomain(): ReservationsPage = ReservationsPage(
    total = total,
    items = reservations.map { it.toDomain() },
)

fun ProgramDto.toDomain(): Program = Program(
    id = id,
    channelId = channel_id,
    title = title.ifBlank { "番組情報がありません" },
    description = description,
    detail = detail,
    start = parseInstantOrEpoch(start_time),
    end = parseInstantOrEpoch(end_time, parseInstantOrEpoch(start_time)),
    genres = genres.map { it.toDomain() },
    isFree = is_free,
    reservation = reservation?.toDomain(),
)

fun ChannelDto.toDomain(): Channel = Channel(
    id = id,
    displayId = display_channel_id,
    name = name,
    type = ChannelType.from(type),
    remoconId = remocon_id,
    channelNumber = channel_number,
    isRadio = is_radiochannel,
    isSubchannel = is_subchannel,
    viewerCount = viewer_count,
    current = program_present?.toDomain(),
    next = program_following?.toDomain(),
    jikkyoForce = jikkyo_force,
)

fun LiveChannelsDto.toDomain(): List<Channel> {
    // KonomiTV の UI と同じ並び（地上波 → BS → CS → CATV → SKY → BS4K）を保つ。
    return buildList {
        addAll(GR)
        addAll(BS)
        addAll(CS)
        addAll(CATV)
        addAll(SKY)
        addAll(BS4K)
    }.map { it.toDomain() }
}

fun TimeTableDto.toDomain(): Timetable = Timetable(
    channels = channels.map { it.toDomain() },
    earliest = parseInstantOrEpoch(date_range.earliest),
    latest = parseInstantOrEpoch(date_range.latest),
)

fun TimeTableChannelDto.toDomain(): TimetableChannel = TimetableChannel(
    channel = channel.toDomain(),
    programs = programs.map { it.toDomain() },
    subchannels = subchannels.orEmpty().map { it.toDomain() },
)

fun TimeTableSubchannelDto.toDomain(): TimetableChannel = TimetableChannel(
    channel = channel.toDomain(),
    programs = programs.map { it.toDomain() },
)

fun RecordedVideoDto.toDomain(): VideoInfo = VideoInfo(
    status = status,
    container = container_format,
    codec = video_codec,
    width = video_resolution_width,
    height = video_resolution_height,
    frameRate = video_frame_rate,
    fileSize = file_size,
    durationSeconds = duration,
    hasStreamChanges = has_video_stream_changes,
)

fun CmSectionDto.toDomain(): CmSection = CmSection(startSeconds = start_time, endSeconds = end_time)

fun ThumbnailTileDto.toDomain(): TileInfo = TileInfo(
    tileWidth = tile_width,
    tileHeight = tile_height,
    totalTiles = total_tiles,
    columnCount = column_count,
    intervalSeconds = interval_sec,
)

fun RecordedProgramDto.toDomain(): Recording = Recording(
    id = id,
    title = title.ifBlank { "無題の録画" },
    subtitle = subtitle,
    description = description,
    start = parseInstantOrEpoch(start_time),
    durationSeconds = (recorded_video.duration.takeIf { it > 0 } ?: duration).toLong(),
    channelName = channel?.name,
    channelId = channel?.display_channel_id,
    seriesTitle = series_title,
    episodeNumber = episode_number,
    genres = genres.map { it.toDomain() },
    video = recorded_video.toDomain(),
    cmSections = recorded_video.cm_sections.orEmpty().map { it.toDomain() },
    tileInfo = recorded_video.thumbnail_info?.tile?.takeIf { it.total_tiles > 0 }?.toDomain(),
)

fun RecordedProgramsDto.toDomain(): RecordingsPage = RecordingsPage(
    total = total,
    items = recorded_programs.map { it.toDomain() },
)

fun SeriesBroadcastPeriodDto.toDomain(): SeriesPeriod = SeriesPeriod(
    channelName = channel?.name,
    channelId = channel?.display_channel_id,
    startDate = start_date,
    endDate = end_date,
    recordings = recorded_programs.map { it.toDomain() },
)

fun SeriesDto.toDomain(): Series = Series(
    id = id,
    title = title.ifBlank { "無題のシリーズ" },
    description = description,
    genres = genres.map { it.toDomain() },
    periods = broadcast_periods.map { it.toDomain() },
)

fun SeriesListDto.toDomain(): Pair<Int, List<Series>> = total to series_list.map { it.toDomain() }

/** 録画の長さが 0 のときに start_time と end_time から補う。 */
fun RecordedProgramDto.durationOrFallback(): Long {
    if (recorded_video.duration > 0) return recorded_video.duration.toLong()
    if (duration > 0) return duration.toLong()
    val start = parseInstant(start_time)
    val end = parseInstant(end_time)
    if (start != null && end != null) return (end.epochSecond - start.epochSecond).coerceAtLeast(0)
    return 0
}

/** 表示用の録画開始時刻（録画開始マージンではなく番組開始時刻）。 */
fun RecordedProgramDto.broadcastStart(): Instant =
    parseInstant(start_time) ?: parseInstant(recorded_video.recording_start_time) ?: Instant.now()

@Suppress("unused")
private val jstOffset = ZoneOffset.ofHours(9)
