package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object MediaCaptureHelper {

    fun createCameraImageUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val file = File(dir, "crop_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Compresses an image picked via Photo Picker, Files (OpenDocument), or Camera (TakePicture)
     * into a compact Base64 JPEG data URL (~30-55 KB) so it syncs across Firebase RTDB
     * to both Android and Website without requiring READ_MEDIA_* or READ_EXTERNAL_STORAGE.
     */
    suspend fun encodeUriToCompactDataUrl(context: Context, uri: Uri, maxDim: Int = 512): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val inputBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext null
                val original = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size)
                    ?: return@withContext null

                val scale = minOf(1f, maxDim.toFloat() / max(original.width, original.height).toFloat())
                val targetW = max(1, (original.width * scale).roundToInt())
                val targetH = max(1, (original.height * scale).roundToInt())
                val scaled = if (scale < 1f) {
                    original.scale(targetW, targetH)
                } else {
                    original
                }

                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
                if (scaled !== original) scaled.recycle()
                original.recycle()

                val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                "data:image/jpeg;base64,$base64"
            }.getOrNull()
        }

    /**
     * Converts Base64 data URLs (`data:image/...;base64,...`) to ByteArray for Coil AsyncImage,
     * while preserving standard `https://` or `content://` URIs.
     */
    fun imageModelFromUri(imageUri: String): Any? {
        if (imageUri.isBlank()) return null
        if (imageUri.startsWith("data:image", ignoreCase = true)) {
            val commaIdx = imageUri.indexOf(',')
            if (commaIdx > 0 && commaIdx < imageUri.length - 1) {
                val decoded = runCatching {
                    Base64.decode(imageUri.substring(commaIdx + 1), Base64.DEFAULT)
                }.getOrNull()
                if (decoded != null) return decoded
            }
        }
        return imageUri
    }
}
