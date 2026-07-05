package com.alhosan.checker.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.alhosan.checker.data.model.AppLang
import com.alhosan.checker.data.model.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Image exporter — replaces the HTML reference's html2canvas approach.
 *
 * Instead of capturing a Compose subtree (which requires the experimental
 * GraphicsLayer API from Compose 1.7+), we render the result card data
 * directly to a Bitmap using Android's native Canvas API via
 * [ResultImageRenderer]. This works on any Compose version and gives us
 * full control over the exported image layout.
 *
 * The PNG is saved to the device's Pictures/AlHosan directory via MediaStore
 * (visible in the Gallery on Android 10+ without any permission).
 */
object ImageExporter {

    /**
     * Render the [subscription] data to a PNG image and save it to the gallery.
     * Safe to call from any coroutine; file IO is moved to Dispatchers.IO.
     */
    suspend fun saveSubscriptionToGallery(
        context: Context,
        subscription: Subscription,
        lang: AppLang,
        fileName: String = "HORSE_PRO_${System.currentTimeMillis()}"
    ): Boolean {
        return try {
            // Rendering is CPU work, do it on Default dispatcher
            val bitmap = withContext(Dispatchers.Default) {
                ResultImageRenderer.render(subscription, lang)
            }
            // File IO on the IO dispatcher
            withContext(Dispatchers.IO) {
                saveBitmapToGallery(context, bitmap, fileName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Save a raw Android Bitmap to the gallery via MediaStore.
     */
    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String = "HORSE_PRO_${System.currentTimeMillis()}"
    ): Boolean {
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AlHosan")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val imageUri: Uri = resolver.insert(uri, contentValues) ?: return false

        var stream: OutputStream? = null
        return try {
            stream = resolver.openOutputStream(imageUri) ?: return false
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            Toast.makeText(
                context,
                "Saved to Pictures/AlHosan/${fileName}.png",
                Toast.LENGTH_SHORT
            ).show()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            try { resolver.delete(imageUri, null, null) } catch (_: Exception) {}
            false
        } finally {
            stream?.close()
        }
    }
}
