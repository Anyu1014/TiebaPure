package dev.anyu.tiebapure

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

internal data class PrivateMessageImage(val bytes: ByteArray, val width: Int, val height: Int, val preview: ImageBitmap) {
    companion object {
        fun read(context: Context, uri: Uri): PrivateMessageImage {
            require(uri.scheme == "content") { "图片来源不受支持" }
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 10 * 1024 * 1024) { "图片不能超过10 MB" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("无法读取图片，请重新选择")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            require(options.outWidth in 1..20000 && options.outHeight in 1..20000 && options.outWidth.toLong() * options.outHeight <= 80_000_000) { "图片尺寸无效或过大" }
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2048) options.inSampleSize *= 2
            options.inJustDecodeBounds = false
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("无法解码图片")
            val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            val normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (normalized !== bitmap) bitmap.recycle()
            val output = ByteArrayOutputStream()
            check(normalized.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "无法准备图片" }
            return PrivateMessageImage(output.toByteArray(), normalized.width, normalized.height, normalized.asImageBitmap())
        }
    }
}
