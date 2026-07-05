package com.alhosan.checker.bridge

import android.util.Log
import com.alhosan.checker.data.model.Subscription
import kotlinx.serialization.json.Json

/**
 * JNI Bridge to Rust core library.
 *
 * NOTE: The Rust native library (libalhosan_core.so) is optional.
 * If it's not available, all methods will gracefully fail and the app
 * will use the OkHttp-based CheckerRepository instead.
 *
 * The Rust side handles heavy processing like:
 * - Xtream API checking with optimized HTTP client
 * - M3U playlist parsing and filtering (rayon parallel processing)
 * - Batch subscription checking
 * - Custom decoding/filtering operations
 */
object RustBridge {

    private const val TAG = "RustBridge"
    private var libraryLoaded = false

    // Attempt to load the Rust native library - graceful fallback if not available
    init {
        libraryLoaded = try {
            System.loadLibrary("alhosan_core")
            Log.i(TAG, "Rust native library loaded successfully")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Rust native library not available, using OkHttp fallback")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load Rust native library: ${e.message}")
            false
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ─── JNI Native Function Declarations ───────────────────────────────

    @JvmStatic
    external fun nativeCheckXtream(host: String, username: String, password: String): String

    @JvmStatic
    external fun nativeCheckXtreamFull(host: String, username: String, password: String): String

    @JvmStatic
    external fun nativeBatchCheck(jsonAccounts: String): String

    @JvmStatic
    external fun nativeParseM3u(content: String): String

    @JvmStatic
    external fun nativeFilterChannels(jsonChannels: String, filterType: String, filterValue: String): String

    @JvmStatic
    external fun nativeGetVersion(): String

    // ─── High-Level Kotlin API (with graceful fallback) ─────────────────

    fun checkXtream(host: String, username: String, password: String): Result<Subscription> {
        if (!libraryLoaded) return Result.failure(Exception("Rust library not available"))
        return try {
            val jsonString = nativeCheckXtreamFull(host, username, password)
            val subscription = json.decodeFromString<Subscription>(jsonString)
            Result.success(subscription)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun batchCheck(accounts: List<Triple<String, String, String>>): Result<List<Subscription>> {
        if (!libraryLoaded) return Result.failure(Exception("Rust library not available"))
        return try {
            val jsonAccounts = Json.encodeToString(
                kotlinx.serialization.serializer<List<List<String>>>(),
                accounts.map { listOf(it.first, it.second, it.third) }
            )
            val jsonString = nativeBatchCheck(jsonAccounts)
            val subscriptions = json.decodeFromString<List<Subscription>>(jsonString)
            Result.success(subscriptions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getVersion(): String {
        if (!libraryLoaded) return "okhttp-fallback"
        return try {
            nativeGetVersion()
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun isAvailable(): Boolean = libraryLoaded
}
