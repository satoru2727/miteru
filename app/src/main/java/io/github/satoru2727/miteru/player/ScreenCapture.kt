package io.github.satoru2727.miteru.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore

/**
 * ビットマップをギャラリー（Pictures/Miteru）へ PNG で保存する。
 * Android 10+ の MediaStore 経由なのでストレージ権限は不要。失敗したら null。
 */
fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? = runCatching {
    val name = "miteru_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Miteru")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    resolver.openOutputStream(uri)?.use { out ->
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
            throw IllegalStateException("compress failed")
        }
    } ?: throw IllegalStateException("no output stream")
    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    uri
}.getOrNull()
