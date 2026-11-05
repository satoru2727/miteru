package io.github.satoru2727.miteru.ui.util

import java.time.Instant
import java.time.ZoneId

/** その日の 0:00 からの経過分（秒を含む）。番組表の横位置計算に使う。 */
fun minutesOfDay(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): Float {
    val time = instant.atZone(zone).toLocalTime()
    return time.hour * 60f + time.minute + time.second / 60f
}
