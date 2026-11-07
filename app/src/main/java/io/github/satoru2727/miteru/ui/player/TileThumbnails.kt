package io.github.satoru2727.miteru.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import io.github.satoru2727.miteru.core.model.TileInfo
import java.io.File
import kotlin.math.floor

/** 再生位置からスプライト内のタイル番号を求める。範囲外は端に丸める。 */
internal fun tileIndexFor(positionSeconds: Double, intervalSeconds: Double, totalTiles: Int): Int? {
    if (intervalSeconds <= 0.0 || totalTiles <= 0) return null
    if (positionSeconds.isNaN() || positionSeconds.isInfinite()) return null
    val index = floor(positionSeconds.coerceAtLeast(0.0) / intervalSeconds).toInt()
    return index.coerceIn(0, totalTiles - 1)
}

/**
 * タイルサムネイルのスプライト画像から 1 タイルを切り出して返す。
 * 領域デコードなので大きな画像でもメモリをほぼ消費しない。失敗したら null。
 */
internal fun decodeTileRegion(file: File, info: TileInfo, positionSeconds: Double): Bitmap? {
    if (!info.isUsable || !file.exists() || file.length() == 0L) return null
    val index = tileIndexFor(positionSeconds, info.intervalSeconds, info.totalTiles) ?: return null
    val column = index % info.columnCount
    val row = index / info.columnCount
    val left = column * info.tileWidth
    val top = row * info.tileHeight
    return runCatching {
        val decoder = BitmapRegionDecoder.newInstance(file.inputStream(), false) ?: return@runCatching null
        try {
            decoder.decodeRegion(
                Rect(left, top, left + info.tileWidth, top + info.tileHeight),
                null,
            )
        } finally {
            decoder.recycle()
        }
    }.getOrNull()
}
