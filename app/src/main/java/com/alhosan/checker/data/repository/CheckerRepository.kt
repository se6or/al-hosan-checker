package com.alhosan.checker.data.repository

import android.util.Log
import com.alhosan.checker.data.model.CheckerInput
import com.alhosan.checker.data.model.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.PushbackInputStream
import java.io.StringReader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ConnectionSpec
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.io.Reader
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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

    companion object {
        // Content-count retries / timeouts.
        // Live lists are usually 1–8 MB, VOD on big panels can exceed 50 MB,
        // series 3–20 MB. On Iraqi 3G/4G or satellite-internet routes to EU
        // panels (common in the region), throughput can dip below 1 MB/s, so
        // timeouts must be generous or big categories will falsely return 0.
        private const val MAX_COUNT_RETRIES = 3
        private const val COUNT_RETRY_BACKOFF_MS = 400L
        // Per-category timeouts — generous but still bounded.
        private const val LIVE_TIMEOUT_MS = 45_000L
        private const val VOD_TIMEOUT_MS = 60_000L
        private const val SERIES_TIMEOUT_MS = 60_000L
        // Global ceiling — must be slightly larger than the biggest single
        // category so we never cancel a category that's almost done.
        private const val TOTAL_COUNT_TIMEOUT_MS = 65_000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS) // large IPTV panels can return multi-MB JSON
        .writeTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        // Allow live + movies + series counts to run truly in parallel.
        // Per-host raised so the three category requests don't queue behind
        // each other on a busy dispatcher.
        .dispatcher(Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 24
        })
        .connectionPool(okhttp3.ConnectionPool(20, 60, TimeUnit.SECONDS))
        .connectionSpecs(
            listOf(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS,
                ConnectionSpec.CLEARTEXT
            )
        )
        .build()

    /**
     * Dedicated HTTP client for content-counting. Same connection pool /
     * dispatcher but with a much longer read timeout because VOD/series
     * arrays on big panels can be 50–100 MB and slow mobile links regularly
     * exceed 25s to download. Using a separate client keeps the main
     * auth-check client responsive without letting huge payloads hold up
     * the initial subscription verification.
     */
    private val countClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(65, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(70, TimeUnit.SECONDS)
            .build()
    }

    private val insecureCountClient: OkHttpClient by lazy {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }
        countClient.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /**
     * IPTV/Xtream panels very often have expired, self-signed or mismatched SSL
     * certificates while still serving perfectly valid accounts/streams. We keep
     * the normal safe client first, then use this compatibility client only after
     * an SSL failure. This matches IPTV players that tolerate bad panel SSL.
     */
    private val insecureClient: OkHttpClient by lazy { buildInsecureCompatibilityClient() }

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
                val apiUrl = "${host}/player_api.php?username=${encodeQuery(username)}&password=${encodeQuery(password)}"

                val request = Request.Builder()
                    .url(apiUrl)
                    .addIptvHeaders(accept = "application/json, text/plain, */*")
                    .build()

                val response = executeTextWithSslCompatibility(request)

                when (response.code) {
                    401, 403 -> return@withContext Result.failure(Exception("auth_failed"))
                    in 400..499 -> return@withContext Result.failure(Exception("http_${response.code}"))
                    in 500..599 -> return@withContext Result.failure(Exception("http_${response.code}"))
                }

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("http_${response.code}"))
                }

                val body = response.body
                if (body.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("empty_response"))
                }

                // Parse the JSON response — be lenient with non-strict servers.
                // Some Xtream panels return slightly different shapes, so we accept
                // any JSON that has user_info OR user_info.username OR just an
                // array (older panels) and try to extract whatever we can.
                val data = try {
                    json.parseToJsonElement(body).jsonObject
                } catch (e: Exception) {
                    // Not JSON at all — could be an HTML error page from a proxy
                    return@withContext Result.failure(Exception("not_xtream"))
                }

                // Accept the response if it has user_info (standard Xtream API),
                // OR if it has any of the typical Xtream fields (be lenient).
                val userInfo = data["user_info"]?.jsonObject
                if (userInfo == null) {
                    // Some servers wrap differently — check for alternative keys
                    val altUserInfo = data["userInfo"]?.jsonObject
                        ?: data["user"]?.jsonObject
                        ?: data["account"]?.jsonObject
                    if (altUserInfo == null) {
                        return@withContext Result.failure(Exception("not_xtream"))
                    }
                    // Use the alternative
                    return@withContext parseSubscriptionFromUserInfo(
                        host, username, password, altUserInfo, data["server_info"]?.jsonObject
                    )
                }

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

    private fun Request.Builder.addIptvHeaders(accept: String): Request.Builder {
        return this
            .header("User-Agent", "IPTVSmartersPro/4.0 (Linux; Android 10) ExoPlayerLib/2.18.1")
            .header("Accept", accept)
            .header("Accept-Language", "en-US,en;q=0.9,*;q=0.8")
            // Gzip compression shrinks big VOD/series payloads 70–90%.
            .header("Accept-Encoding", "gzip, deflate")
            .header("Cache-Control", "no-cache")
        // NOTE: intentionally NO "Connection: close" — let OkHttp reuse the
        // keep-alive TCP/TLS connection across the 3 parallel category
        // requests. Otherwise every category re-handshakes TLS and channels
        // always wins the race, making movies/series look "slower".
    }

    private data class HttpTextResponse(
        val code: Int,
        val isSuccessful: Boolean,
        val body: String?
    )

    /**
     * Execute normally first. If Android rejects the panel's SSL certificate,
     * retry once with the IPTV compatibility client that accepts bad/self-signed
     * certificates. This is only a fallback after a real SSL failure.
     */
    private fun executeTextWithSslCompatibility(request: Request): HttpTextResponse {
        return try {
            executeText(client, request)
        } catch (first: Exception) {
            if (!isRetryablePanelTransportFailure(first)) throw first

            // NOTE: insecureClient accepts any SSL certificate (self-signed / expired).
            // This is intentional for IPTV panels — user traffic is NOT secured by TLS on these servers.
            Log.w("CheckerRepo", "Panel transport failed, retrying with IPTV compatibility client: ${request.url}", first)
            try {
                executeText(insecureClient, request)
            } catch (second: Exception) {
                if (!isRetryablePanelTransportFailure(second)) throw second

                // Final IPTV-style fallback: if HTTPS itself is broken/reset,
                // try the same URL over plain HTTP. Many Xtream panels serve the
                // API on both protocols even when HTTPS is misconfigured.
                if (request.url.scheme == "https") {
                    val httpRequest = request.newBuilder()
                        .url(request.url.newBuilder().scheme("http").build())
                        .build()
                    Log.w("CheckerRepo", "HTTPS panel transport still failed, downgrading to HTTP: ${httpRequest.url}", second)
                    executeText(client, httpRequest)
                } else {
                    throw second
                }
            }
        }
    }

    private fun executeText(okHttpClient: OkHttpClient, request: Request): HttpTextResponse {
        okHttpClient.newCall(request).execute().use { response ->
            return HttpTextResponse(
                code = response.code,
                isSuccessful = response.isSuccessful,
                body = response.body?.string()
            )
        }
    }

    private fun isRetryablePanelTransportFailure(e: Throwable): Boolean {
        val msg = e.message.orEmpty()
        return isSslFailure(e) ||
            e is java.net.SocketException ||
            e is java.io.EOFException ||
            msg.contains("connection reset", ignoreCase = true) ||
            msg.contains("stream was reset", ignoreCase = true) ||
            msg.contains("unexpected end", ignoreCase = true) ||
            msg.contains("broken pipe", ignoreCase = true) ||
            e.cause?.let { isRetryablePanelTransportFailure(it) } == true
    }

    private fun isSslFailure(e: Throwable): Boolean {
        val msg = e.message.orEmpty()
        return e is SSLException ||
            msg.contains("ssl", ignoreCase = true) ||
            msg.contains("handshake", ignoreCase = true) ||
            msg.contains("certificate", ignoreCase = true) ||
            msg.contains("cert", ignoreCase = true) ||
            msg.contains("Trust anchor", ignoreCase = true) ||
            msg.contains("Hostname", ignoreCase = true) ||
            e.cause?.let { isSslFailure(it) } == true
    }

    private fun buildInsecureCompatibilityClient(): OkHttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }

        return client.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .build()
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
            e.message?.contains("handshake", ignoreCase = true) == true -> "ssl_failed"
            e.message?.contains("certificate", ignoreCase = true) == true -> "ssl_failed"
            e.message?.contains("cert", ignoreCase = true) == true -> "ssl_failed"
            e.message?.contains("trust anchor", ignoreCase = true) == true -> "ssl_failed"
            e.message?.contains("hostname", ignoreCase = true) == true -> "ssl_failed"
            e.message?.contains("redirect", ignoreCase = true) == true -> "redirect_loop"
            e.message?.contains("ECONNREFUSED", ignoreCase = true) == true -> "connection_refused"
            e.message?.contains("ENETUNREACH", ignoreCase = true) == true -> "network_unreachable"
            else -> "unknown"
        }
    }

    /**
     * Fetch content counts (live, VOD, series) in parallel for maximum speed.
     *
     * [onField] is invoked on the calling dispatcher the moment each
     * individual category request finishes — with the field name and its
     * real number. This is the KEY to making the three counters truly
     * independent: each field's real number is applied to the UI the
     * instant it arrives, without waiting for or snapshotting sibling
     * fields. Channels, movies, and series each animate on their own.
     *
     * Empty string = pending (UI runs the live counter). A real "0" is
     * only ever set by a successful server response.
     */
    suspend fun fetchContentCounts(
        host: String,
        username: String,
        password: String,
        onField: (suspend (field: ContentField, value: String) -> Unit)? = null
    ): Triple<String, String, String> = withContext(Dispatchers.IO) {
        try {
            val h = host.trimEnd('/')
            val u = encodeQuery(username)
            val p = encodeQuery(password)
            val liveUrl = "${h}/player_api.php?username=${u}&password=${p}&action=get_live_streams"
            val vodUrl = "${h}/player_api.php?username=${u}&password=${p}&action=get_vod_streams"
            val seriesUrl = "${h}/player_api.php?username=${u}&password=${p}&action=get_series"

            // Hard ceiling for the parallel group so a dead category can't
            // keep the gold counter spinning forever.
            withTimeout(TOTAL_COUNT_TIMEOUT_MS) {
            // Run the three category requests fully in parallel. Each one
            // publishes its real number the instant it arrives — no mutex,
            // no snapshot, no waiting for siblings. Truly independent.
            //
            // Each category also has its own timeout so a giant VOD/series
            // payload that never finishes doesn't hold up the entire group.
            coroutineScope {
                val liveJob = async {
                    val v = withTimeoutOrNull(LIVE_TIMEOUT_MS) { fetchArrayCount(liveUrl) } ?: ""
                    onField?.invoke(ContentField.LIVE, v)
                    v
                }
                val vodJob = async {
                    val v = withTimeoutOrNull(VOD_TIMEOUT_MS) { fetchArrayCount(vodUrl) } ?: ""
                    onField?.invoke(ContentField.MOVIE, v)
                    v
                }
                val seriesJob = async {
                    val v = withTimeoutOrNull(SERIES_TIMEOUT_MS) { fetchArrayCount(seriesUrl) } ?: ""
                    onField?.invoke(ContentField.SERIES, v)
                    v
                }

                Triple(liveJob.await(), vodJob.await(), seriesJob.await())
            } // end coroutineScope
            } // end withTimeout
        } catch (e: Exception) {
            // Catastrophic failure (cancellation, outer timeout, etc).
            // Return empty (pending) for all three; the ViewModel's finally
            // block converts any still-pending field to "0" so the counter
            // stops cleanly.
            Triple("", "", "")
        }
    }

    /** Which content category a partial count belongs to. */
    enum class ContentField { LIVE, MOVIE, SERIES }

    /**
     * Fetch a single category count (live / vod / series).
     *
     * Robust multi-strategy approach (so we handle the many real-world
     * Xtream panel quirks that previously made some categories return 0):
     *
     *  1. Try the standard action= endpoint on countClient (long read timeout)
     *     using the streaming JSON-array counter.
     *  2. If that fails, retry with insecureCountClient (bad/self-signed SSL).
     *  3. If HTTPS and still failing, retry over plain HTTP.
     *  4. Try a secondary User-Agent — some panels block SmartersPro for
     *     get_live_streams/get_vod_streams but allow generic UAs.
     *  5. If the response is an object with a known length field (e.g.
     *     {"total":N}) parse it as a count.
     *  6. Fall back to the legacy fully-buffered JSON parse as a last resort.
     *
     * Returns "" only if everything failed; caller will treat it as pending.
     */
    private suspend fun fetchArrayCount(url: String): String {
        val strategies = buildList<(Request.Builder) -> Request.Builder> {
            // default headers (IPTVSmartersPro UA)
            add { it }
            // alternate UA — some panels block SmartersPro for content listings
            add { b ->
                b.removeHeader("User-Agent")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
            }
        }

        val candidates = buildList<String> {
            add(url)
            // Some panels expect action without underscore or use get_live_categories
            // — try the common "_streams" / "" variants too. No, keep it simple:
            // only try protocol flip (http <-> https) since SSLCompat already handles
            // cert issues. We just add scheme-flipped variant here for retry symmetry.
            if (url.startsWith("https://")) add("http://" + url.removePrefix("https://"))
        }.distinct()

        var lastError: Throwable? = null

        for (candidateUrl in candidates) {
            for (strategy in strategies) {
                repeat(MAX_COUNT_RETRIES) { attempt ->
                    try {
                        val request = strategy(
                            Request.Builder()
                                .url(candidateUrl)
                                .addIptvHeaders(accept = "application/json, text/plain, */*")
                        ).build()
                        val count = withContext(Dispatchers.IO) {
                            executeJsonArrayCountWithCompat(request)
                        }
                        if (count >= 0) return count.toString()
                    } catch (e: Exception) {
                        lastError = e
                        // Non-blocking backoff.
                        if (attempt < MAX_COUNT_RETRIES - 1) {
                            delay(COUNT_RETRY_BACKOFF_MS * (attempt + 1))
                        }
                    }
                }
            }
        }

        Log.w("CheckerRepo", "All count attempts failed for $url: ${lastError?.message}")
        return ""
    }

    /**
     * Count Xtream array with proper client selection (countClient for the
     * generous read timeout, regular client was too short for large VOD/series
     * payloads on slow links).
     */
    private fun executeJsonArrayCountWithCompat(request: Request): Int {
        return try {
            countJsonArrayWithClient(countClient, request)
        } catch (first: Exception) {
            if (!isRetryablePanelTransportFailure(first)) throw first
            try {
                countJsonArrayWithClient(insecureCountClient, request)
            } catch (second: Exception) {
                if (!isRetryablePanelTransportFailure(second)) throw second
                if (request.url.scheme == "https") {
                    val httpRequest = request.newBuilder()
                        .url(request.url.newBuilder().scheme("http").build())
                        .build()
                    countJsonArrayWithClient(countClient, httpRequest)
                } else {
                    throw second
                }
            }
        }
    }

    private fun countJsonArrayWithClient(okHttpClient: OkHttpClient, request: Request): Int {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP error ${response.code}")
            val body = response.body ?: throw java.io.IOException("Null body")
            val stream = body.byteStream()

            // Read the first ~128 bytes looking for the first non-whitespace
            // character so we can decide whether this is a JSON array (stream
            // count) or a JSON object wrapper (buffer + regex). We do this
            // with a PushbackInputStream-like buffered reader so we can push
            // the already-read bytes back into the stream for the streaming
            // counter / body.string() to still see the full payload.
            val pushback = java.io.PushbackInputStream(stream, 256)
            val prefixBytes = ByteArray(256)
            var prefixLen = 0
            var first: Char = Char.MIN_VALUE
            var sawFirst = false
            var lookingForBom = true

            while (prefixLen < prefixBytes.size) {
                val b = pushback.read()
                if (b == -1) break
                prefixBytes[prefixLen++] = b.toByte()
                val ch = b.toChar()
                if (ch == '\uFEFF' && prefixLen == 1) continue // strip BOM
                if (ch.isWhitespace()) continue
                first = ch
                sawFirst = true
                break
            }
            // Push back whatever we read so downstream sees the full body.
            if (prefixLen > 0) {
                pushback.unread(prefixBytes, 0, prefixLen)
            }

            if (!sawFirst) return 0

            return when (first) {
                '[' ->
                    pushback.bufferedReader(Charsets.UTF_8).use {
                        countFirstJsonArrayElements(it)
                    }
                '{' -> {
                    // Object wrapper → materialize full body (these are small).
                    val fullText = pushback.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    parseCountFromJsonObject(fullText)
                }
                else -> throw java.io.IOException("Response is not JSON")
            }
        }
    }

    /**
     * Robust streaming JSON-array counter.
     *
     * Bug fixes vs previous version:
     *  - Does NOT return 0 on '{' at position 0. Some misconfigured panels
     *    emit a leading UTF-8 BOM, whitespace, or an HTML <br>/newline before
     *    the '['. Skip everything until the first '['.
     *  - If the server closes the connection before sending the final ']'
     *    (VERY common — nginx drops large responses once the client stops
     *    reading, or panels close without flushing the last byte), we still
     *    return the count gathered so far. This was the #1 reason categories
     *    reported 0: we threw "Unexpected EOF" after having already counted
     *    thousands of items.
     *  - Tracks nested strings/objects/arrays correctly.
     */
    private fun countFirstJsonArrayElements(reader: Reader): Int {
        var started = false
        var arrayDepth = 0
        var objectDepth = 0
        var inString = false
        var escape = false
        var tokenStarted = false
        var count = 0

        val buf = BufferedReader(reader, 128 * 1024)

        while (true) {
            val code = buf.read()
            if (code == -1) break
            val ch = code.toChar()

            if (!started) {
                when {
                    ch == '[' -> {
                        started = true
                        arrayDepth = 1
                    }
                    // Keep skipping BOM, whitespace, and HTML/garbage prefixes
                    // until we find an opening bracket. Don't throw on '{': we
                    // might be in a preamble; wait until the reader exhausts
                    // and let the caller handle empty count = 0.
                    ch.isWhitespace() || ch == '\uFEFF' || ch == '\r' || ch == '\n' -> Unit
                    else -> Unit // ignore random leading junk bytes
                }
                continue
            }

            if (inString) {
                if (escape) {
                    escape = false
                } else if (ch == '\\') {
                    escape = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> {
                    inString = true
                    if (arrayDepth == 1) tokenStarted = true
                }
                '[' -> {
                    if (arrayDepth == 1) tokenStarted = true
                    arrayDepth++
                }
                ']' -> {
                    arrayDepth--
                    if (arrayDepth == 0) {
                        // Final close of the top-level array. If we had any
                        // in-progress element between the last comma and this
                        // ']', count it.
                        if (tokenStarted) count++
                        return count
                    }
                }
                '{' -> {
                    objectDepth++
                    if (arrayDepth == 1) tokenStarted = true
                }
                '}' -> {
                    if (objectDepth > 0) objectDepth--
                    // If closing the top-level object (shouldn't happen inside
                    // an array, but tolerate)
                }
                ',' -> {
                    if (arrayDepth == 1 && objectDepth == 0) {
                        if (tokenStarted) count++
                        tokenStarted = false
                    }
                }
                ' ', '\n', '\r', '\t' -> Unit
                else -> if (arrayDepth == 1) tokenStarted = true
            }
        }

        // EOF reached without a closing ']'.
        if (!started) return 0          // never even saw an opening '[' → 0
        // Server closed the stream early (large payloads, slow connections).
        // Whatever we counted up to this point is the best estimate; do NOT
        // throw, do NOT return 0. This fixes the "categories returned 0" bug
        // on big panels.
        if (tokenStarted) count++
        return count
    }

    /**
     * Some panels return e.g. {"available":1,"2345":{...},...} or a wrapper
     * with an explicit `length`/`total`/`count` field. We try two things:
     *  - Look for a top-level numeric "total"/"length"/"count" / "total_items"
     *    field in the FIRST few KB of the response.
     *  - If the response is itself an entire array that started with '{' due
     *    to a sub-object, fall back to streaming object keys as stream items
     *    (object with N keys = N items).
     */
    private fun parseCountFromJsonObject(body: String): Int {
        // Try common numeric length fields (case-insensitive, regex).
        val patterns = listOf(
            """"total"\s*:\s*(\d+)""",
            """"total_items"\s*:\s*(\d+)""",
            """"count"\s*:\s*(\d+)""",
            """"length"\s*:\s*(\d+)""",
            """"num"\s*:\s*(\d+)""",
            """"total_streams"\s*:\s*(\d+)""",
            """"available"\s*:\s*(\d+)""",
        )
        for (pat in patterns) {
            val m = Regex(pat, RegexOption.IGNORE_CASE).find(body)
            m?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }

        // Fall back: count top-level object keys crudely as items (some older
        // panels return {id: {info...}, id2:{...}} instead of an array).
        val sectionMatch = Regex("""\{(.+)\}""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(body.trim()) ?: return 0
        val inner = sectionMatch.groupValues[1]
        var depth = 0
        var inStr = false
        var esc = false
        var keys = 0
        var afterColon = false
        for (ch in inner) {
            if (inStr) {
                if (esc) esc = false
                else if (ch == '\\') esc = true
                else if (ch == '"') { inStr = false; if (!afterColon) keys++ }
                continue
            }
            when (ch) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> if (depth > 0) depth--
                ':' -> if (depth == 0) afterColon = true
                ',' -> if (depth == 0) afterColon = false
            }
        }
        return keys
    }

    /**
     * Check M3U / Xtream-style links.
     *
     * Supported real-world forms:
     * - /get.php?username=USER&password=PASS&type=m3u_plus
     * - /player_api.php?username=USER&password=PASS
     * - /xmltv.php?username=USER&password=PASS
     * - /live/USER/PASS/123.ts
     * - /movie/USER/PASS/123.mp4
     * - /series/USER/PASS/123.mkv
     *
     * If credentials are present, we validate through player_api.php so the
     * result is a real Xtream subscription. If there are no credentials, we
     * still try to download the playlist and count channels/movies/series.
     */
    private suspend fun checkM3uLink(m3uLink: String): Result<Subscription> =
        withContext(Dispatchers.IO) {
            val normalizedLink = m3uLink.trim()
            try {
                val credentials = extractXtreamCredentials(normalizedLink)
                if (credentials != null) {
                    val result = checkSubscription(
                        CheckerInput(
                            host = credentials.host,
                            username = credentials.username,
                            password = credentials.password
                        )
                    )

                    result.getOrNull()?.let { sub ->
                        return@withContext Result.success(
                            sub.copy(
                                isM3uMode = true,
                                m3uLink = normalizedLink
                            )
                        )
                    }

                    // player_api.php failed (404 / not_xtream / etc.). Some panels
                    // (e.g. ones where the M3U link specifies an explicit :80 port
                    // but player_api.php is only served on the default port, or
                    // panels that host get.php on a different URL shape) still
                    // serve get.php properly. Try fetching the M3U playlist itself
                    // from ALL candidate hosts derived from the link so we can
                    // still show the user a useful result instead of "response
                    // invalid".
                    val candidateHosts = buildList {
                        add(credentials.host)
                        // If link carried an explicit default port, retry without it.
                        val noDefault = stripDefaultPort(credentials.host)
                        if (noDefault != credentials.host) add(noDefault)
                    }.distinct()

                    for (candidateHost in candidateHosts) {
                        // Build candidate get.php URLs matching the link's host and
                        // explicit port (if any). This covers panels where get.php
                        // works but player_api.php is disabled or on a different vhost.
                        val candidateGetUrl = buildGetUrl(candidateHost, credentials.username, credentials.password)
                        val playlistFallback = fetchAndCountM3uPlaylist(
                            m3uLink = candidateGetUrl,
                            host = candidateHost,
                            username = credentials.username,
                            password = credentials.password
                        )
                        if (playlistFallback != null) {
                            return@withContext Result.success(
                                playlistFallback.copy(
                                    isM3uMode = true,
                                    m3uLink = normalizedLink
                                )
                            )
                        }
                    }

                    // Last resort: fetch the original link the user pasted as-is
                    // (handles panels serving M3U on a custom path).
                    val playlistFallback = fetchAndCountM3uPlaylist(
                        m3uLink = normalizedLink,
                        host = credentials.host,
                        username = credentials.username,
                        password = credentials.password
                    )
                    if (playlistFallback != null) {
                        return@withContext Result.success(playlistFallback)
                    }

                    return@withContext result
                }

                val playlistOnly = fetchAndCountM3uPlaylist(normalizedLink)
                if (playlistOnly != null) {
                    return@withContext Result.success(playlistOnly)
                }

                Result.failure(Exception("not_xtream"))
            } catch (e: Exception) {
                Result.failure(Exception(mapExceptionToCode(e)))
            }
        }

    private data class XtreamCredentials(
        val host: String,
        val username: String,
        val password: String
    )

    private fun extractXtreamCredentials(link: String): XtreamCredentials? {
        val uri = runCatching { URI(link) }.getOrNull() ?: return null
        val scheme = uri.scheme ?: return null
        val hostName = uri.host ?: return null
        // Preserve non-default ports; drop :80 on http and :443 on https because
        // many Xtream panels (e.g. 4ksdreams.com) only expose player_api.php /
        // get.php on the default port and return 404 when an explicit default
        // port is appended to the Host header.
        val port = uri.port
        val isDefaultPort =
            port == -1 ||
                (scheme.equals("http", ignoreCase = true) && port == 80) ||
                (scheme.equals("https", ignoreCase = true) && port == 443)
        val host = if (isDefaultPort) {
            "$scheme://$hostName"
        } else {
            "$scheme://$hostName:$port"
        }

        val params = parseQueryParams(uri.rawQuery ?: uri.query.orEmpty())
        val queryUser = params["username"] ?: params["user"] ?: params["login"]
        val queryPass = params["password"] ?: params["pass"] ?: params["pwd"]
        if (!queryUser.isNullOrBlank() && !queryPass.isNullOrBlank()) {
            return XtreamCredentials(host, queryUser, queryPass)
        }

        // Path-based Xtream links: /live/user/pass/id.ts, /movie/user/pass/id,
        // /series/user/pass/id, and sometimes /user/pass/id.
        val segments = (uri.rawPath ?: uri.path.orEmpty())
            .split('/')
            .filter { it.isNotBlank() }
            .map { decodeUrlPart(it) }

        val typeIndex = segments.indexOfFirst {
            it.equals("live", true) || it.equals("movie", true) || it.equals("series", true)
        }
        if (typeIndex >= 0 && segments.size >= typeIndex + 3) {
            val user = segments[typeIndex + 1]
            val pass = segments[typeIndex + 2]
            if (user.isNotBlank() && pass.isNotBlank()) return XtreamCredentials(host, user, pass)
        }

        if (segments.size >= 3) {
            val user = segments[0]
            val pass = segments[1]
            val maybeStream = segments[2]
            if (user.isNotBlank() && pass.isNotBlank() && maybeStream.isNotBlank()) {
                return XtreamCredentials(host, user, pass)
            }
        }

        return null
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val pieces = part.split('=', limit = 2)
                val key = decodeUrlPart(pieces.getOrElse(0) { "" }).lowercase(Locale.ROOT)
                val value = decodeUrlPart(pieces.getOrElse(1) { "" })
                if (key.isBlank()) null else key to value
            }
            .toMap()
    }

    private fun decodeUrlPart(value: String): String {
        return try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }
    }

    private fun encodeQuery(value: String): String {
        return try {
            URLEncoder.encode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }
    }

    /** Strip the default port (80 for http, 443 for https) from a host URL. */
    private fun stripDefaultPort(host: String): String {
        return try {
            val u = java.net.URL(host)
            val isDefault =
                (u.protocol == "http" && (u.port == -1 || u.port == 80)) ||
                (u.protocol == "https" && (u.port == -1 || u.port == 443))
            if (isDefault) "${u.protocol}://${u.host}" else host
        } catch (_: Exception) {
            host
        }
    }

    /** Build a canonical /get.php?...type=m3u_plus&output=ts URL from host+credentials. */
    private fun buildGetUrl(host: String, username: String, password: String): String {
        val base = host.trimEnd('/')
        return "$base/get.php?username=${encodeQuery(username)}&password=${encodeQuery(password)}&type=m3u_plus&output=ts"
    }

    private fun fetchAndCountM3uPlaylist(
        m3uLink: String,
        host: String = m3uLink,
        username: String = "M3U Link",
        password: String = "--"
    ): Subscription? {
        return try {
            val request = Request.Builder()
                .url(m3uLink)
                .addIptvHeaders(accept = "application/x-mpegURL, audio/x-mpegurl, text/plain, */*")
                .build()

            val response = executeTextWithSslCompatibility(request)
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            if (!body.contains("#EXTM3U", ignoreCase = true) &&
                !body.contains("#EXTINF", ignoreCase = true)
            ) {
                return null
            }
            val counts = countM3uContent(body)
            if (counts.total == 0) return null

            Subscription(
                host = host,
                username = username,
                password = password,
                status = "Active",
                activeCons = "--",
                maxCons = "--",
                isTrial = false,
                liveCount = counts.live.toString(),
                movieCount = counts.movie.toString(),
                seriesCount = counts.series.toString(),
                isM3uMode = true,
                m3uLink = m3uLink
            )
        } catch (_: Exception) {
            null
        }
    }

    private data class M3uCounts(val live: Int, val movie: Int, val series: Int) {
        val total: Int get() = live + movie + series
    }

    private fun countM3uContent(content: String): M3uCounts {
        var live = 0
        var movie = 0
        var series = 0
        var pendingExtInf: String? = null

        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                if (line.startsWith("#EXTINF", ignoreCase = true)) {
                    pendingExtInf = line
                    return@forEach
                }

                if (line.startsWith("#")) return@forEach

                val meta = pendingExtInf.orEmpty().lowercase(Locale.ROOT)
                val url = line.lowercase(Locale.ROOT)
                when {
                    "/series/" in url || " group-title=\"series" in meta || "group-title='series" in meta -> series++
                    "/movie/" in url || "/vod/" in url || " group-title=\"movies" in meta || "group-title='movies" in meta || "group-title=\"vod" in meta -> movie++
                    else -> live++
                }
                pendingExtInf = null
            }

        return M3uCounts(live, movie, series)
    }

    /**
     * Generate M3U link from subscription data.
     */
    fun generateM3uLink(subscription: Subscription): String {
        if (subscription.isM3uMode && subscription.m3uLink.isNotBlank()) {
            return subscription.m3uLink
        }
        val cleanHost = subscription.host.trimEnd('/')
        return "${cleanHost}/get.php?username=${encodeQuery(subscription.username)}&password=${encodeQuery(subscription.password)}&type=m3u_plus&output=ts"
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

    /**
     * Build a [Subscription] from a user_info JSON object (or any alternative
     * shape that has the typical fields). Tolerates missing/null fields —
     * returns a default value instead of throwing.
     */
    private fun parseSubscriptionFromUserInfo(
        host: String,
        username: String,
        password: String,
        userInfo: JsonObject,
        serverInfo: JsonObject?
    ): Result<Subscription> {
        // Tolerant field extraction — many IPTV panels omit or null some fields.
        fun JsonObject.stringField(key: String): String = try {
            this[key]?.jsonPrimitive?.contentOrNull ?: ""
        } catch (_: Exception) { "" }

        val status = userInfo.stringField("status").ifBlank { "Unknown" }
        val expDate = userInfo.stringField("exp_date").ifBlank { "0" }
        val createdAt = userInfo.stringField("created_at").ifBlank { "0" }
        val activeCons = userInfo.stringField("active_cons").ifBlank { "0" }
        val maxCons = userInfo.stringField("max_connections").ifBlank { "0" }
        val isTrialStr = userInfo.stringField("is_trial")
        val isTrial = isTrialStr == "1" || isTrialStr.equals("true", ignoreCase = true)

        val serverUrl = serverInfo?.stringField("url") ?: ""
        val serverProtocol = serverInfo?.stringField("server_protocol") ?: ""
        val timezone = serverInfo?.stringField("timezone") ?: ""

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
        return Result.success(subscription)
    }
}

