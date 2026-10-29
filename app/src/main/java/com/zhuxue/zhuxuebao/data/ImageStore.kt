package com.zhuxue.zhuxuebao.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageStore(private val context: Context) {

    private val recordDir = File(context.filesDir, "record_images").apply { mkdirs() }
    private val answerDir = File(context.filesDir, "answer_images").apply { mkdirs() }

    fun newCameraFile(): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "camera_${System.currentTimeMillis()}.jpg")
    }

    suspend fun importRecordImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        compressToFile(uri, recordDir, "record")
    }

    suspend fun importAnswerImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        compressToFile(uri, answerDir, "answer")
    }

    suspend fun readBytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { File(path).readBytes() }.getOrNull()
    }

    fun delete(paths: List<String>) {
        paths.forEach { path -> runCatching { File(path).delete() } }
    }

    fun pruneOrphans(
        keepRecordPaths: Set<String>,
        answerMaxAgeMs: Long = 7L * 24 * 60 * 60 * 1000
    ) {
        val now = System.currentTimeMillis()
        recordDir.listFiles()?.forEach { file ->
            if (file.absolutePath !in keepRecordPaths) runCatching { file.delete() }
        }
        answerDir.listFiles()?.forEach { file ->
            if (now - file.lastModified() > answerMaxAgeMs) runCatching { file.delete() }
        }
        File(context.cacheDir, "camera").listFiles()?.forEach { file ->
            if (now - file.lastModified() > 24L * 60 * 60 * 1000) {
                runCatching { file.delete() }
            }
        }
        File(context.cacheDir, "bank_import").listFiles()?.forEach { file ->
            if (now - file.lastModified() > 24L * 60 * 60 * 1000) {
                runCatching { file.delete() }
            }
        }
    }

    private fun compressToFile(uri: Uri, dir: File, prefix: String): String? {
        val source = runCatching { decodeBitmap(uri) }.getOrNull() ?: return null
        val scaled = scaleDown(source)
        val target = File(dir, "${prefix}_${UUID.randomUUID()}.jpg")
        return try {
            FileOutputStream(target).use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
            }
            target.absolutePath
        } catch (error: Exception) {
            target.delete()
            null
        } finally {
            if (scaled !== source) scaled.recycle()
            source.recycle()
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeModern(uri)
        } else {
            decodeLegacy(uri)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeModern(uri: Uri): Bitmap? {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            val sample = if (longest > 2000) longest / 2000 else 1
            decoder.setTargetSampleSize(max(1, sample))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }

    private fun decodeLegacy(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2000) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= 1600) return bitmap
        val ratio = 1600f / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
