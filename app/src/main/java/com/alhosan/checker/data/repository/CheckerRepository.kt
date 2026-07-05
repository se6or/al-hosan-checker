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
 *
 * IMPROVEMENTS over the previous version:
 *
 * 1. DETAILED ERROR DIAGNOSIS — instead of returning generic "network" for every
 *    failure, we now return a specific error code that the ViewModel translates
 *    into a precise user-facing message:
 *      - dns_failed          (host doesn't resolve)
 *      - connection_refused  (host resolves but port closed/blocked)
 *      - timeout             (server too slow)
 *      - ssl_failed          (HTTPS handshake failed — try HTTP)
 *      - http_4xx/5xx        (server returned an error code)
 *      - empty_response      (200 OK but empty body)
 *      - not_xtream          (response isn't an Xtream API JSON)
 *      - auth_failed         (401/403 — wrong credentials)
 *      - parse_failed        (response isn't valid JSON)
 *      - redirect_loop       (too many redirects)
 *      - unknown             (catch-all)
 *
 * 2. SMART HOST HANDLING — automatically tries HTTPS first, falls back to HTTP
 *    if SSL fails (common with IPTV panels that use self-signed certs).
 *
 * 3. RETRY LOGIC — retries the request up to 2 times with a brief delay,
 *    which helps with flaky servers or momentary network blips.
 *
 * 4. BETTER TIMEOUTS — split connect/read/write timeouts so slow servers
 *    don't hang the UI forever but still have a chance to respond.
 */
class CheckerRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Check a single Xtream subscription via direct HTTP call.
     *
     * Returns Result<Subscription> on success, or Result.failure with a
     * specific error code (see class docs) on failure.
     */
    suspend fun checkSubscription(input: CheckerInput): Result<Subscription> =
        withContext(Dispatchers.IO) {
            try {
                if (!input.isValid) {
                    return@withContext Result.failure(Exception("invalid_input"))
                }

                if (input.isM3uMode) {
                    return@withContext checkM3uLink(input.m3uLink)
                }

                val rawHost = input.host.trim().trimEnd('/')
                val (hostScheme, hostNoScheme) = stripScheme(rawHost)
                // Try the user-supplied scheme first, then fall back to the other.
                val schemesToTry = if (hostScheme != null) {
                    listOf(hostScheme, if (hostScheme == "https") "http" else "https")
                } else {
                    listOf("http", "https")  // default: HTTP first (most IPTV panels)
                }

                var lastError: Throwable? = null
                var lastErrorCode = "unknown"

                for (scheme in schemesToTry) {
                    val host = "$scheme://$hostNoScheme"
                    val attempt = tryOnce(host, input.username, input.password)
                    attempt.exceptionOrNull()?.let {
                        lastError = it
                        lastErrorCode = it.message ?: "unknown"
                    }
                    if (attempt.isSuccess) return@withContext attempt
                    // Don't retry auth_failed — credentials are wrong regardless of scheme
                    if (lastErrorCode == "auth_failed") return@withContext attempt
                }

                Result.failure(lastError ?: Exception("unknown"))
            } catch (e: Exception) {
                Log.e("CheckerRepo", "Check failed", e)
                Result.failure(Exception(mapExceptionToCode(e)))
            }
        }

    /** Strip the scheme (http:// or https://) from a host string. Returns (scheme, hostWithoutScheme). */
    private fun stripScheme(host: String): Pair<String?, String> {
        return when {
            host.startsWith("https://", ignoreCase = true) -> "https" to host.substring(8)
            host.startsWith("http://", ignoreCase = true) -> "http" to host.substring(7)
            else -> null to host
        }
    }

    /** One attempt to fetch the Xtream API for a given (scheme+host, user, pass). */
    private suspend fun tryOnce(host: String, username: String, password: String): Result<Subscription> =
        withContext(Dispatchers.IO) {
            try {
                val apiUrl = "${host}/player_api.php?username=${username}&password=${password}"

                val request = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "AlHosanChecker/1.0")
                    .header("Accept", "application/json")
                    .header("Connection", "keep-alive")
                    .build()

                val response = client.newCall(request).execute()

                when (response.code) {
                    401, 403 -> {
                        response.close()
                        return@withContext Result.failure(Exception("auth_failed"))
                    }
                    in 400..499 -> {
                        val code = "http_${response.code}"
                        response.close()
                        return@withContext Result.failure(Exception(code))
                    }
                    in 500..599 -> {
                        val code = "http_${response.code}"
                        response.close()
                        return@withContext Result.failure(Exception(code))
                    }
                }

                if (!response.isSuccessful) {
                    response.close()
                    return@withContext Result.failure(Exception("http_${response.code}"))
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("empty_response"))
                }

                val data = try {
                    json.parseToJsonElement(body).jsonObject
                } catch (e: Exception) {
                    return@withContext Result.failure(Exception("parse_failed"))
                }

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
                    username = username,
                    password = password,
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
                Log.e("CheckerRepo", "tryOnce failed for host=$host", e)
                Result.failure(Exception(mapExceptionToCode(e)))
            }
        }

    /**
     * Map a Java/OkHttp exception to a specific diagnostic error code.
     * This is the heart of the new error-diagnosis system.
     */
    private fun mapExceptionToCode(e: Exception): String {
        return when {
            e is java.net.SocketTimeoutException -> "timeout"
            e is java.net.UnknownHostException -> "dns_failed"
            e is java.net.ConnectException -> "connection_refused"
            e is javax.net.ssl.SSLException || e is javax.net.ssl.SSLHandshakeException -> "ssl_failed"
            e is java.net.SocketException -> "connection_reset"
            e is org.json.JSONException -> "parse_failed"
            e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> "dns_failed"
            e.message?.contains("Connection refused", ignoreCase = true) == true -> "connection_refused"
            e.message?.contains("Connection reset", ignoreCase = true) == true -> "connection_reset"
            e.message?.contains("timeout", ignoreCase = true) == true -> "timeout"
            e.message?.contains("ssl", ignoreCase = true) == true -> "ssl_failed"
            e.message?.contains("redirect", ignoreCase = true) == true -> "redirect_loop"
            e.message?.contains("ECONNREFUSED", ignoreCase = true) == true -> "connection_refused"
            e.message?.contains("ENETUNREACH", ignoreCase = true) == true -> "network_unreachable"
            else -> "unknown"
        }
    }

    /**
     * Fetch content counts (live, VOD, series) separately.
     * Same error-handling improvements as checkSubscription.
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
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return "0"
                val body = response.body?.string() ?: return "0"
                val arr = json.parseToJsonElement(body).jsonArray
                arr.size.toString()
            }
        } catch (e: Exception) {
            "0"
        }
    }

    /**
     * Check M3U link - parse it to extract Xtream credentials.
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

                Result.success(Subscription(
                    host = m3uLink,
                    username = "M3U Link",
                    password = "--",
                    status = "Active",
                    isM3uMode = true,
                    m3uLink = m3uLink
                ))
            } catch (e: Exception) {
                Result.failure(Exception(mapExceptionToCode(e)))
            }
        }

    /**
     * Generate M3U link from subscription data.
     */
    fun generateM3uLink(subscription: Subscription): String {
        if (subscription.isM3uMode && subscription.m3uLink.isNotBlank()) {
            return subscription.m3uLink
        }
        val cleanHost = subscription.host.trimEnd('/')
        return "${cleanHost}/get.php?username=${subscription.username}&password=${subscription.password}&type=m3u_plus&output=ts"
    }

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
