package com.alhosan.checker.data.repository

import android.util.Log
import com.alhosan.checker.data.model.CheckerInput
import com.alhosan.checker.data.model.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
        // Number of times a single content-count request (live / vod / series)
        // is retried before falling back to "0". Kept small so the counting
        // phase stays fast — a single failed category won't hold up the others.
        private const val MAX_COUNT_RETRIES = 3
        private const val COUNT_RETRY_BACKOFF_MS = 150
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS) // large IPTV panels can return multi-MB JSON
        .writeTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        // Allow live + movies + series counts to run truly in parallel.
        .dispatcher(Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 6
        })
        .connectionSpecs(
            listOf(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS,
                ConnectionSpec.CLEARTEXT
            )
        )
        .build()

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
            .header("Connection", "close")
            .header("Cache-Control", "no-cache")
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
     * [onPartial] is invoked on the calling dispatcher every time one of the
     * three requests finishes so the UI can paint real numbers progressively
     * instead of waiting for the slowest category. Each partial triple always
     * contains the latest known value for every field (pending fields keep
     * the previous value / "…" marker).
     */
    suspend fun fetchContentCounts(
        host: String,
        username: String,
        password: String,
        onPartial: (suspend (live: String, movie: String, series: String) -> Unit)? = null
    ): Triple<String, String, String> = withContext(Dispatchers.IO) {
        try {
            val h = host.trimEnd('/')
            val u = encodeQuery(username)
            val p = encodeQuery(password)
            val liveUrl = "${h}/player_api.php?username=${u}&password=${p}&action=get_live_streams"
            val vodUrl = "${h}/player_api.php?username=${u}&password=${p}&action=get_vod_streams"
            val seriesUrl = "${h}/player_api.php?username=${u}&password=${p}&action=get_series"

            // Run the three category requests fully in parallel and surface
            // each real number the moment it arrives (progressive UI).
            coroutineScope {
                val counts = arrayOf("0", "0", "0")
                val mutex = Mutex()

                suspend fun publish(index: Int, value: String): String {
                    mutex.withLock {
                        counts[index] = value
                    }
                    // Snapshot after the lock so concurrent finishers don't
                    // race when reading sibling fields for the partial update.
                    val snapshot = mutex.withLock {
                        Triple(counts[0], counts[1], counts[2])
                    }
                    onPartial?.invoke(snapshot.first, snapshot.second, snapshot.third)
                    return value
                }

                val liveJob = async { publish(0, fetchArrayCount(liveUrl)) }
                val vodJob = async { publish(1, fetchArrayCount(vodUrl)) }
                val seriesJob = async { publish(2, fetchArrayCount(seriesUrl)) }

                Triple(liveJob.await(), vodJob.await(), seriesJob.await())
            }
        } catch (e: Exception) {
            Triple("0", "0", "0")
        }
    }

    /**
     * Fetch a single category count (live / vod / series).
     *
     * Retries the request up to MAX_COUNT_RETRIES times with a short back-off
     * before falling back to "0". This prevents transient transport failures
     * (SSL resets, momentary timeouts, broken pipes) from producing an
     * inconsistent "0" count that differs between back-to-back checks of the
     * same subscription.
     */
    private fun fetchArrayCount(url: String): String {
        val request = Request.Builder()
            .url(url)
            .addIptvHeaders(accept = "application/json, text/plain, */*")
            .build()
        var lastCount = 0
        repeat(MAX_COUNT_RETRIES) { attempt ->
            try {
                lastCount = executeJsonArrayCountWithCompatibility(request)
                // A successful response always wins — don't retry a success.
                return lastCount.toString()
            } catch (e: Exception) {
                // Retryable transport failure: wait briefly then try again.
                if (attempt < MAX_COUNT_RETRIES - 1) {
                    try {
                        Thread.sleep(COUNT_RETRY_BACKOFF_MS.toLong())
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
        }
        return lastCount.toString()
    }

    /**
     * Count large Xtream arrays without materializing the whole JSON in memory.
     * Some panels return tens of MB for get_live_streams/get_vod_streams; parsing
     * them into JsonElement can trigger an Android OOM and close the app right
     * after the result screen appears. This streams the response and counts the
     * first JSON array's top-level elements instead.
     */
    private fun executeJsonArrayCountWithCompatibility(request: Request): Int {
        return try {
            countJsonArrayWithClient(client, request)
        } catch (first: Exception) {
            if (!isRetryablePanelTransportFailure(first)) throw first
            try {
                countJsonArrayWithClient(insecureClient, request)
            } catch (second: Exception) {
                if (!isRetryablePanelTransportFailure(second)) throw second
                if (request.url.scheme == "https") {
                    val httpRequest = request.newBuilder()
                        .url(request.url.newBuilder().scheme("http").build())
                        .build()
                    countJsonArrayWithClient(client, httpRequest)
                } else {
                    throw second
                }
            }
        }
    }

    private fun countJsonArrayWithClient(okHttpClient: OkHttpClient, request: Request): Int {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return 0
            val body = response.body ?: return 0
            body.charStream().use { reader ->
                return countFirstJsonArrayElements(reader)
            }
        }
    }

    private fun countFirstJsonArrayElements(reader: Reader): Int {
        var started = false
        var arrayDepth = 0
        var objectDepth = 0
        var inString = false
        var escape = false
        var tokenStarted = false
        var count = 0

        while (true) {
            val code = reader.read()
            if (code == -1) break
            val ch = code.toChar()

            if (!started) {
                if (ch == '[') {
                    started = true
                    arrayDepth = 1
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
                    arrayDepth++
                    if (arrayDepth == 1) tokenStarted = true
                }
                ']' -> {
                    if (arrayDepth == 1) {
                        if (tokenStarted) count++
                        return count
                    }
                    arrayDepth--
                }
                '{' -> {
                    objectDepth++
                    if (arrayDepth == 1) tokenStarted = true
                }
                '}' -> if (objectDepth > 0) objectDepth--
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

        return count
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

                    // Some panels allow get.php playlist download but block/disable
                    // player_api.php. If that happens, still give the user a useful
                    // real M3U result by counting the playlist itself.
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
        val host = "$scheme://$hostName${if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""}"

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
