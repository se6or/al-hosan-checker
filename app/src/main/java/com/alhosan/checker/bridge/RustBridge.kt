package com.alhosan.checker.bridge

import com.alhosan.checker.data.model.Subscription
import kotlinx.serialization.json.Json

/**
 * JNI Bridge to Rust core library.
 *
 * This class provides the interface between Kotlin and the Rust native library
 * (libalhosan_core.so). The Rust side handles heavy processing like:
 * - Xtream API checking with optimized HTTP client
 * - M3U playlist parsing and filtering (rayon parallel processing)
 * - Batch subscription checking
 * - Custom decoding/filtering operations
 *
 * The JNI functions are declared as `external` and map directly to
 * the Rust functions in `jni_bridge.rs`.
 */
object RustBridge {

    // Load the Rust native library
    init {
        System.loadLibrary("alhosan_core")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ─── JNI Native Function Declarations ──────────────────────────────

    /**
     * Check a single Xtream subscription (basic info only)
     * Returns JSON string with subscription data or error
     */
    @JvmStatic
    external fun nativeCheckXtream(host: String, username: String, password: String): String

    /**
     * Check a single Xtream subscription with full info (including live/VOD/series counts)
     * Returns JSON string with complete subscription data
     */
    @JvmStatic
    external fun nativeCheckXtreamFull(host: String, username: String, password: String): String

    /**
     * Batch check multiple subscriptions
     * @param jsonAccounts JSON array of [host, username, password] tuples
     * @return JSON array of subscription results
     */
    @JvmStatic
    external fun nativeBatchCheck(jsonAccounts: String): String

    /**
     * Parse M3U playlist content
     * @param content Raw M3U playlist text
     * @return JSON string with parsed result
     */
    @JvmStatic
    external fun nativeParseM3u(content: String): String

    /**
     * Filter M3U channels
     * @param jsonChannels JSON array of channels
     * @param filterType "group" or "name"
     * @param filterValue Value to filter by
     * @return JSON array of filtered channels
     */
    @JvmStatic
    external fun nativeFilterChannels(jsonChannels: String, filterType: String, filterValue: String): String

    /**
     * Get Rust library version
     */
    @JvmStatic
    external fun nativeGetVersion(): String

    // ─── High-Level Kotlin API ─────────────────────────────────────────

    /**
     * Check Xtream subscription - returns parsed Subscription object
     */
    fun checkXtream(host: String, username: String, password: String): Result<Subscription> {
        return try {
            val jsonString = nativeCheckXtreamFull(host, username, password)
            val subscription = json.decodeFromString<Subscription>(jsonString)
            Result.success(subscription)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Batch check multiple subscriptions
     */
    fun batchCheck(accounts: List<Triple<String, String, String>>): Result<List<Subscription>> {
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

    /**
     * Get the Rust library version
     */
    fun getVersion(): String {
        return try {
            nativeGetVersion()
        } catch (e: Exception) {
            "unknown"
        }
    }
}
