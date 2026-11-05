package io.github.satoru2727.miteru.core.net

import kotlinx.serialization.Serializable

/*
 * KonomiTV の API レスポンス DTO。
 * すべてのフィールドに既定値を持たせ、サーバーのバージョン差でフィールドが増減しても
 * デコードが失敗しないようにしている（ignoreUnknownKeys と併用）。
 */

@Serializable
data class VersionDto(
    val version: String = "",
    val latest_version: String? = null,
    val environment: String = "",
    val backend: String = "",
    val encoder: String = "",
)

@Serializable
data class GenreDto(
    val major: String = "",
    val middle: String = "",
)

@Serializable
data class ReservationDto(
    val id: Int = 0,
    val status: String = "",
    val recording_availability: String = "",
)

@Serializable
data class RecordingFolderDto(
    val recording_folder_path: String = "",
    val recording_file_name_template: String? = null,
    val is_oneseg_separate_recording_folder: Boolean = false,
)

@Serializable
data class RecordSettingsDto(
    val is_enabled: Boolean = true,
    val priority: Int = 3,
    val recording_folders: List<RecordingFolderDto> = emptyList(),
    val recording_start_margin: Int? = null,
    val recording_end_margin: Int? = null,
    val recording_mode: String = "SpecifiedService",
    val caption_recording_mode: String = "Default",
    val data_broadcasting_recording_mode: String = "Default",
    val post_recording_mode: String = "Default",
    val post_recording_bat_file_path: String? = null,
    val is_event_relay_follow_enabled: Boolean = true,
    val is_exact_recording_enabled: Boolean = false,
    val is_oneseg_separate_output_enabled: Boolean = false,
    val is_sequential_recording_in_single_file_enabled: Boolean = false,
    val forced_tuner_id: Int? = null,
)

@Serializable
data class ReservationItemDto(
    val id: Int = 0,
    val channel: ChannelDto? = null,
    val program: ProgramDto? = null,
    val is_recording_in_progress: Boolean = false,
    val recording_availability: String = "Full",
    val comment: String = "",
    val scheduled_recording_file_name: String = "",
    val estimated_recording_file_size: Long = 0,
    val record_settings: RecordSettingsDto? = null,
)

@Serializable
data class JikkyoInfoDto(
    val watch_session_url: String? = null,
    val nicolive_watch_session_url: String? = null,
    val nicolive_watch_session_error: String? = null,
    val comment_session_url: String? = null,
    val is_nxjikkyo_exclusive: Boolean = false,
)

@Serializable
data class JikkyoCommentDto(
    val time: Double = 0.0,
    val type: String = "right",
    val size: String = "medium",
    val color: String = "white",
    val author: String = "",
    val text: String = "",
)

@Serializable
data class JikkyoCommentsDto(
    val is_success: Boolean = false,
    val comments: List<JikkyoCommentDto> = emptyList(),
    val detail: String = "",
)

@Serializable
data class ReservationsDto(
    val total: Int = 0,
    val reservations: List<ReservationItemDto> = emptyList(),
)

@Serializable
data class ReservationAddRequestDto(
    val program_id: String,
    val record_settings: RecordSettingsDto,
)

@Serializable
data class ReservationUpdateRequestDto(
    val record_settings: RecordSettingsDto,
)

@Serializable
data class ProgramDto(
    val id: String = "",
    val channel_id: String = "",
    val network_id: Int = 0,
    val service_id: Int = 0,
    val event_id: Int = 0,
    val title: String = "",
    val description: String = "",
    val detail: Map<String, String> = emptyMap(),
    val start_time: String = "",
    val end_time: String = "",
    val duration: Double = 0.0,
    val is_free: Boolean = true,
    val genres: List<GenreDto> = emptyList(),
    val video_type: String? = null,
    val video_codec: String? = null,
    val video_resolution: String? = null,
    val primary_audio_type: String = "",
    val primary_audio_language: String = "",
    val primary_audio_sampling_rate: String = "",
    val secondary_audio_type: String? = null,
    val secondary_audio_language: String? = null,
    val secondary_audio_sampling_rate: String? = null,
    val reservation: ReservationDto? = null,
)

@Serializable
data class ChannelDto(
    val id: String = "",
    val display_channel_id: String = "",
    val network_id: Int = 0,
    val service_id: Int = 0,
    val transport_stream_id: Int? = null,
    val remocon_id: Int = 0,
    val channel_number: String = "",
    val type: String = "GR",
    val name: String = "",
    val jikkyo_force: Int? = null,
    val is_subchannel: Boolean = false,
    val is_radiochannel: Boolean = false,
    val is_watchable: Boolean = true,
    val is_display: Boolean = true,
    val viewer_count: Int = 0,
    val program_present: ProgramDto? = null,
    val program_following: ProgramDto? = null,
)

@Serializable
data class LiveChannelsDto(
    val GR: List<ChannelDto> = emptyList(),
    val BS: List<ChannelDto> = emptyList(),
    val CS: List<ChannelDto> = emptyList(),
    val CATV: List<ChannelDto> = emptyList(),
    val SKY: List<ChannelDto> = emptyList(),
    val BS4K: List<ChannelDto> = emptyList(),
)

@Serializable
data class TimeTableDateRangeDto(
    val earliest: String = "",
    val latest: String = "",
)

@Serializable
data class TimeTableChannelDto(
    val channel: ChannelDto = ChannelDto(),
    val programs: List<ProgramDto> = emptyList(),
    val subchannels: List<TimeTableSubchannelDto>? = null,
)

@Serializable
data class TimeTableSubchannelDto(
    val channel: ChannelDto = ChannelDto(),
    val programs: List<ProgramDto> = emptyList(),
)

@Serializable
data class TimeTableDto(
    val channels: List<TimeTableChannelDto> = emptyList(),
    val date_range: TimeTableDateRangeDto = TimeTableDateRangeDto(),
)

@Serializable
data class RecordedVideoDto(
    val id: Int = -1,
    val status: String = "Recorded",
    val file_path: String = "",
    val file_hash: String = "",
    val file_size: Long = 0,
    val file_created_at: String = "",
    val file_modified_at: String = "",
    val recording_start_time: String? = null,
    val recording_end_time: String? = null,
    val duration: Double = 0.0,
    val container_format: String = "MPEG-TS",
    val video_codec: String = "MPEG-2",
    val video_codec_profile: String = "",
    val video_scan_type: String = "",
    val video_frame_rate: Double = 0.0,
    val video_resolution_width: Int = 0,
    val video_resolution_height: Int = 0,
    val has_video_stream_changes: Boolean = false,
    val primary_audio_codec: String = "",
    val primary_audio_channel: String = "",
    val primary_audio_sampling_rate: Int = 0,
    val secondary_audio_codec: String? = null,
    val secondary_audio_channel: String? = null,
    val secondary_audio_sampling_rate: Int? = null,
)

@Serializable
data class RecordedProgramDto(
    val id: Int = -1,
    val recorded_video: RecordedVideoDto = RecordedVideoDto(),
    val recording_start_margin: Double = 0.0,
    val recording_end_margin: Double = 0.0,
    val is_partially_recorded: Boolean = false,
    val channel: ChannelDto? = null,
    val network_id: Int? = null,
    val service_id: Int? = null,
    val event_id: Int? = null,
    val series_id: Int? = null,
    val series_broadcast_period_id: Int? = null,
    val title: String = "",
    val series_title: String? = null,
    val episode_number: String? = null,
    val subtitle: String? = null,
    val description: String = "",
    val detail: Map<String, String> = emptyMap(),
    val start_time: String = "",
    val end_time: String = "",
    val duration: Double = 0.0,
    val is_free: Boolean = true,
    val genres: List<GenreDto> = emptyList(),
    val primary_audio_type: String = "",
    val primary_audio_language: String = "",
    val secondary_audio_type: String? = null,
    val secondary_audio_language: String? = null,
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class RecordedProgramsDto(
    val total: Int = 0,
    val recorded_programs: List<RecordedProgramDto> = emptyList(),
)

@Serializable
data class ApiErrorDto(
    val detail: String = "",
)
