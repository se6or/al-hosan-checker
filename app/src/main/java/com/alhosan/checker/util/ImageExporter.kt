package com.alhosan.checker.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Image exporter — saves a Bitmap as PNG to Pictures/AlHosan.
 *
 * File naming rule:
 *   username.png
 *   username (1).png
 *   username (2).png
 * ...when the same username is saved multiple times.
 */
object ImageExporter {

    private const val EXPORT_DIR_NAME = "AlHosan"
    private const val PNG_MIME_TYPE = "image/png"
    /**
     * Save a raw Android Bitmap as PNG to the gallery.
     * [fileName] is the base name without extension; .png is always applied.
     */
    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String
    ): Boolean {
        val resolver = context.contentResolver
        val safeBaseName = sanitizeFileName(fileName).ifBlank { "AlHosan" }
        val displayName = createUniquePngDisplayName(context, safeBaseName)

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, PNG_MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$EXPORT_DIR_NAME/"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val imageUri: Uri = resolver.insert(uri, contentValues) ?: return false

        var stream: OutputStream? = null
        return try {
            stream = resolver.openOutputStream(imageUri)
            if (stream == null) {
                resolver.delete(imageUri, null, null)
                return false
            }

            val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()

            if (!compressed) {
                resolver.delete(imageUri, null, null)
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            try { resolver.delete(imageUri, null, null) } catch (_: Exception) {}
            false
        } finally {
            stream?.close()
        }
    }

    private fun createUniquePngDisplayName(context: Context, baseName: String): String {
        var index = 0
        while (index < 10_000) {
            val candidate = if (index == 0) "$baseName.png" else "$baseName ($index).png"
            if (!displayNameExists(context, candidate)) return candidate
            index++
        }
        return "$baseName (${System.currentTimeMillis()}).png"
    }

    private fun displayNameExists(context: Context, displayName: String): Boolean {
        val resolver = context.contentResolver
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID)

        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
            selectionArgs = arrayOf(displayName, "${Environment.DIRECTORY_PICTURES}/$EXPORT_DIR_NAME/")
        } else {
            selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            selectionArgs = arrayOf(displayName)
        }

        return try {
            resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun sanitizeFileName(raw: String): String {
        return raw
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim('.', ' ')
            .take(80)
            .ifBlank { "AlHosan" }
    }
}
