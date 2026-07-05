package com.alhosan.checker.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Image exporter — replaces the HTML reference's html2canvas approach.
 *
 * Captures a Compose GraphicsLayer into a Bitmap, then saves it to the
 * device's Pictures/AlHosan directory via MediaStore so it appears in
 * the Gallery app on Android 10+ (no permission needed) and uses
 * legacy WRITE_EXTERNAL_STORAGE only on Android 9 and below.
 *
 * NOTE: `GraphicsLayer.toImageBitmap()` is a suspend function in Compose UI 1.7+,
 * so callers must invoke `saveLayerToGallery` from a coroutine.
 */
object ImageExporter {

    /**
     * Save a Compose GraphicsLayer as a PNG image to the gallery.
     * MUST be called from a coroutine (suspend function).
     *
     * toImageBitmap() must run on the calling dispatcher (typically Main,
     * because it interacts with the rendering pipeline). The file write is
     * moved to Dispatchers.IO.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    suspend fun saveLayerToGallery(
        context: Context,
        layer: GraphicsLayer,
        fileName: String = "HORSE_PRO_${System.currentTimeMillis()}"
    ): Boolean {
        return try {
            // toImageBitmap() is suspend and may need to interact with the rendering thread
            val bitmap = layer.toImageBitmap().asAndroidBitmap()
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
