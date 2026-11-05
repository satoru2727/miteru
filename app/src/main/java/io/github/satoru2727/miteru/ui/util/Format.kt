package io.github.satoru2727.miteru.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()
private val isJapanese: Boolean get() = Locale.getDefault().language == "ja"

private fun timeFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
private fun dateTimeFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("M/d(E) H:mm", Locale.getDefault())
private fun dateFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.getDefault())

fun formatTime(instant: Instant): String = timeFormatter().withZone(zone).format(instant)

fun formatTimeRange(start: Instant, end: Instant): String = "${formatTime(start)} – ${formatTime(end)}"

fun formatDateTime(instant: Instant): String = dateTimeFormatter().withZone(zone).format(instant)

fun formatDate(instant: Instant): String = dateFormatter().withZone(zone).format(instant)

fun localDateOf(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

/** 録画の長さ。日本語なら「1時間30分」、英語なら「1h 30m」。 */
fun formatDurationHuman(seconds: Long): String {
    val totalMinutes = (seconds / 60).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (isJapanese) {
        if (hours > 0) "${hours}時間${minutes}分" else "${minutes}分"
    } else {
        if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}

/** プレイヤーの経過時間。1:23:45 / 23:45。 */
fun formatClock(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    return if (index == 0) {
        "${value.toLong()} ${units[index]}"
    } else {
        "%.1f %s".format(Locale.US, value, units[index])
    }
}

/** 「3分前」のような相対表記。 */
fun formatRelative(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val diffSeconds = ((System.currentTimeMillis() - epochMillis) / 1000).coerceAtLeast(0)
    return when {
        diffSeconds < 60 -> if (isJapanese) "たった今" else "just now"
        diffSeconds < 3600 -> {
            val minutes = diffSeconds / 60
            if (isJapanese) "${minutes}分前" else "${minutes} min ago"
        }
        diffSeconds < 86_400 -> {
            val hours = diffSeconds / 3600
            if (isJapanese) "${hours}時間前" else "${hours} h ago"
        }
        else -> {
            val days = diffSeconds / 86_400
            if (isJapanese) "${days}日前" else "${days} d ago"
        }
    }
}
