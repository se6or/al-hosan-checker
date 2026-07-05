package com.alhosan.checker.data.repository

import android.util.Log
import com.alhosan.checker.data.model.CheckerInput
import com.alhosan.checker.data.model.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Repository layer that performs actual Xtream API calls using OkHttp.
 * Falls back to Rust core if available, but primarily uses direct HTTP calls
 * to ensure reliability (matching the HTML reference's fetch-based approach).
 */
class CheckerRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Check a single Xtream subscription via direct HTTP call
     * This matches the HTML reference's fetch() approach exactly
     */
    suspend fun checkSubscription(input: CheckerInput): Result<Subscription> =
        withContext(Dispatchers.IO) {
            try {
                if (!input.isValid) {
                    return@withContext Result.failure(IllegalArgumentException("All fields required"))
                }

                if (input.isM3uMode) {
                    return@withContext checkM3uLink(input.m3uLink)
                }

                var host = input.host.trim().trimEnd('/')
                if (!host.startsWith("http://", ignoreCase = true) &&
                    !host.startsWith("https://", ignoreCase = true)) {
                    host = "http://$host"
                }

                val apiUrl = "${host}/player_api.php?username=${input.username}&password=${input.password}"

                val request = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "AlHosanChecker/1.0")
                    .build()

                val response = client.newCall(request).execute()

                if (response.code == 401 || response.code == 403) {
                    return@withContext Result.failure(Exception("auth_failed"))
                }

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("http_error"))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))

                val data = json.parseToJsonElement(body).jsonObject

                val userInfo = data["user_info"]?.jsonObject
                    ?: return@withContext Result.failure(Exception("not_xtream"))

                val serverInfo = data["server_info"]?.jsonObject

                val status = userInfo["status"]?.jsonPrimitive?.content ?: "Unknown"
                val expDate = userInfo["exp_date"]?.jsonPrimitive?.content ?: "0"
                val createdAt = userInfo["created_at"]?.jsonPrimitive?.content ?: "0"
                val activeCons = userInfo["active_cons"]?.jsonPrimitive?.content ?: "0"
                val maxCons = userInfo["max_connections"]?.jsonPrimitive?.content ?: "0"
                val isTrial = userInfo["is_trial"]?.jsonPrimitive?.content == "1"

                val serverUrl = serverInfo?.get("url")?.jsonPrimitive?.content ?: ""
                val serverProtocol = serverInfo?.get("server_protocol")?.jsonPrimitive?.content ?: ""
                val timezone = serverInfo?.get("timezone")?.jsonPrimitive?.content ?: ""

                val subscription = Subscription(
                    host = host,
                    username = input.username,
                    password = input.password,
                    status = status,
                    expiry = formatTimestamp(expDate),
                    created = formatTimestamp(createdAt),
                    activeCons = activeCons,
                    maxCons = maxCons,
                    isTrial = isTrial,
                    liveCount = "0",
                    movieCount = "0",
                    seriesCount = "0",
                    serverUrl = serverUrl,
                    serverProtocol = serverProtocol,
                    timezone = timezone,
                    isM3uMode = false
                )

                Result.success(subscription)
            } catch (e: Exception) {
                Log.e("CheckerRepo", "Check failed", e)
                when {
                    e.message == "auth_failed" -> Result.failure(e)
                    e.message == "http_error" -> Result.failure(e)
                    e.message == "not_xtream" -> Result.failure(e)
                    e is java.net.SocketTimeoutException -> Result.failure(Exception("timeout"))
                    e is java.net.UnknownHostException -> Result.failure(Exception("network"))
                    else -> Result.failure(Exception("network"))
                }
            }
        }

    /**
     * Fetch content counts (live, VOD, series) separately
     * This matches the HTML reference's fetchContentCounts() function
     */
    suspend fun fetchContentCounts(
        host: String,
        username: String,
        password: String
    ): Triple<String, String, String> = withContext(Dispatchers.IO) {
        try {
            val h = host.trimEnd('/')
            val liveUrl = "${h}/player_api.php?username=${username}&password=${password}&action=get_live_streams"
            val vodUrl = "${h}/player_api.php?username=${username}&password=${password}&action=get_vod_streams"
            val seriesUrl = "${h}/player_api.php?username=${username}&password=${password}&action=get_series"

            val liveCount = fetchArrayCount(liveUrl)
            val vodCount = fetchArrayCount(vodUrl)
            val seriesCount = fetchArrayCount(seriesUrl)

            Triple(liveCount, vodCount, seriesCount)
        } catch (e: Exception) {
            Triple("0", "0", "0")
        }
    }

    private fun fetchArrayCount(url: String): String {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AlHosanChecker/1.0")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return "0"
            val arr = json.parseToJsonElement(body).jsonArray
            arr.size.toString()
        } catch (e: Exception) {
            "0"
        }
    }

    /**
     * Check M3U link - parse it to extract Xtream credentials
     * Matches the HTML reference's parseM3UtoXtream() function
     */
    private suspend fun checkM3uLink(m3uLink: String): Result<Subscription> =
        withContext(Dispatchers.IO) {
            try {
                val uri = URI(m3uLink)
                val query = uri.query ?: ""
                val params = query.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                }

                val username = params["username"] ?: params["user"] ?: ""
                val password = params["password"] ?: params["pass"] ?: ""
                val host = "${uri.scheme}://${uri.host}${if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""}"

                if (username.isNotBlank()) {
                    // This is an Xtream M3U link - check it as Xtream
                    val input = CheckerInput(
                        host = host,
                        username = username,
                        password = password
                    )
                    val result = checkSubscription(input)
                    result.getOrNull()?.let { sub ->
                        return@withContext Result.success(sub.copy(isM3uMode = true, m3uLink = m3uLink))
                    }
                    return@withContext result
                }

                // Pure M3U link - just display info
                Result.success(Subscription(
                    host = m3uLink,
                    username = "M3U Link",
                    password = "--",
                    status = "Active",
                    isM3uMode = true,
                    m3uLink = m3uLink
                ))
            } catch (e: Exception) {
                Result.failure(Exception("not_xtream"))
            }
        }

    /**
     * Generate M3U link from subscription data
     * Matches the HTML reference's generateM3U() function
     */
    fun generateM3uLink(subscription: Subscription): String {
        if (subscription.isM3uMode && subscription.m3uLink.isNotBlank()) {
            return subscription.m3uLink
        }
        val cleanHost = subscription.host.trimEnd('/')
        return "${cleanHost}/get.php?username=${subscription.username}&password=${subscription.password}&type=m3u_plus&output=ts"
    }

    /**
     * Format unix timestamp to human-readable date
     * Matches the HTML reference's date formatting
     */
    private fun formatTimestamp(ts: String): String {
        if (ts.isEmpty() || ts == "0" || ts == "null") return "--"
        return try {
            val timestamp = ts.toLong()
            val date = java.util.Date(timestamp * 1000)
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(date)
        } catch (e: Exception) {
            ts
        }
    }
}
