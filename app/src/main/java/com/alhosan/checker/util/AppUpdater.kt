package com.alhosan.checker.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * In-app updater: checks the latest GitHub Release for se6or/al-hosan-checker
 * and triggers APK download/install. No Play Services required.
 *
 * - Uses the public GitHub REST API (no token needed) to fetch the latest
 *   release metadata (tag_name, html_url, apk asset URL).
 * - Compares the release's version code (taken from the APK asset name like
 *   "al-hosan-checker-v3.apk" or a "-vNNN-" segment) to BuildConfig.VERSION_CODE.
 * - Downloads the APK via DownloadManager so the system handles progress/retries
 *   and notifies us on completion.
 * - Launches an ACTION_VIEW install Intent via FileProvider (API 24+) so the
 *   Android package installer takes over.
 */
object AppUpdater {

    private const val OWNER = "se6or"
    private const val REPO = "al-hosan-checker"
    private const val APK_PREFIX = "al-hosan-checker"
    private const val PREFS_NAME = "alhosan_updater"
    private const val KEY_SKIPPED_VERSION = "skipped_version"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val releaseNotes: String,
        val apkUrl: String,
        val htmlUrl: String,
        val apkName: String,
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        val browser_download_url: String,
        val content_type: String? = null,
        val size: Long = 0,
    )

    @Serializable
    private data class GitHubRelease(
        val tag_name: String,
        val name: String? = null,
        val body: String? = null,
        val html_url: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GitHubAsset> = emptyList(),
    )

    /**
     * Check GitHub for a newer release. Returns null if up-to-date, no APK
     * asset, or user previously skipped this exact version.
     */
    private fun currentVersionCode(context: Context): Int = try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            (pInfo.longVersionCode and 0xFFFFFFFFL).toInt()
        } else {
            pInfo.versionCode
        }
    } catch (_: Exception) { 1 }

    private fun currentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val installedCode = currentVersionCode(context)
            val url = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "AlHosanChecker")
                .build()
            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string().orEmpty()
            }
            if (body.isBlank()) return@withContext null
            val release = json.decodeFromString<GitHubRelease>(body)
            if (release.draft) return@withContext null
            val asset = release.assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true) &&
                    it.name.startsWith(APK_PREFIX, ignoreCase = true)
            } ?: return@withContext null
            val versionCode = parseVersionCode(asset.name, release.tag_name)
            val versionName = parseVersionName(release.tag_name, release.name)
            if (versionCode <= installedCode) return@withContext null

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getInt(KEY_SKIPPED_VERSION, 0) == versionCode) {
                return@withContext null
            }

            UpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                releaseNotes = (release.body.orEmpty()).take(500),
                apkUrl = asset.browser_download_url,
                htmlUrl = release.html_url,
                apkName = asset.name,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun skipVersion(context: Context, versionCode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SKIPPED_VERSION, versionCode).apply()
    }

    /**
     * Begin downloading the APK via DownloadManager. Returns the download id
     * so the caller can register for ACTION_DOWNLOAD_COMPLETE.
     */
    fun startDownload(context: Context, info: UpdateInfo): Long {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "updates"
        ).apply { mkdirs() }
        val target = File(dir, info.apkName)
        if (target.exists()) target.delete()

        // API 26+: the user must grant REQUEST_INSTALL_PACKAGES per-app before
        // we can install. We don't request here — the UI layer asks for it up
        // front before calling startDownload.

        val isRtl = context.resources.configuration.layoutDirection ==
            android.util.LayoutDirection.RTL
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("AlHosan Checker v${info.versionName}")
            .setDescription(if (isRtl) "جاري تحميل التحديث..." else "Downloading update...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(target))
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request).also { id ->
            // Remember which file maps to this download id so the broadcast
            // receiver can trigger install.
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("download_path_$id", target.absolutePath)
                .putLong("pending_download_id", id)
                .apply()
        }
    }

    /** Query DownloadManager for download status. */
    fun downloadStatus(context: Context, id: Long): DownloadStatus {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = DownloadManager.Query().setFilterById(id)
        dm.query(q).use { c ->
            if (!c.moveToFirst()) return DownloadStatus(-1, -1, -1)
            val bytesIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val reasonIdx = c.getColumnIndex(DownloadManager.COLUMN_REASON)
            return DownloadStatus(
                status = if (statusIdx >= 0) c.getInt(statusIdx) else -1,
                bytesDownloaded = if (bytesIdx >= 0) c.getLong(bytesIdx) else -1,
                totalBytes = if (totalIdx >= 0) c.getLong(totalIdx) else -1,
                reason = if (reasonIdx >= 0) c.getInt(reasonIdx) else 0,
            )
        }
    }

    data class DownloadStatus(
        val status: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val reason: Int = 0,
    ) {
        val isRunning: Boolean get() =
            status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED || status == DownloadManager.STATUS_PENDING
        val isSuccessful: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
        val isFailed: Boolean get() = status == DownloadManager.STATUS_FAILED
    }

    /**
     * Install the APK for a completed download id. Call after the system
     * broadcasts ACTION_DOWNLOAD_COMPLETE.
     */
    fun installDownloadedApk(context: Context, downloadId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString("download_path_$downloadId", null) ?: return
        val apk = File(path)
        if (!apk.exists()) return

        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
        } else {
            Uri.fromFile(apk)
        }

        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(install)

        prefs.edit().remove("download_path_$downloadId")
            .remove("pending_download_id")
            .apply()
    }

    /** Register a receiver that auto-launches the installer when download completes. */
    fun registerDownloadReceiver(context: Context): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == -1L) return
                val pendingId = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getLong("pending_download_id", -1)
                if (id != pendingId) return
                // Query status to ensure it didn't fail
                val st = downloadStatus(ctx, id)
                if (st.isSuccessful) {
                    installDownloadedApk(ctx, id)
                }
            }
        }
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
        return receiver
    }

    private fun parseVersionCode(assetName: String, tagName: String): Int {
        // Prefer "-vNNN-" or "-vNNN.apk" in asset name, fall back to tag.
        val reAsset = Regex("""v(\d{1,5})""", RegexOption.IGNORE_CASE)
        reAsset.find(assetName)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        reAsset.find(tagName)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        // Fallback: strip leading 'v' from tag & try parse digits up to '.'.
        val digits = tagName.trimStart('v', 'V').takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    private fun parseVersionName(tagName: String, name: String?): String {
        return name?.takeIf { it.isNotBlank() } ?: tagName.trimStart('v', 'V')
    }
}
