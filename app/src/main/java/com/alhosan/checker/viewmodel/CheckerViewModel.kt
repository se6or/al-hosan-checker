package com.alhosan.checker.viewmodel

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alhosan.checker.data.model.AppLang
import com.alhosan.checker.data.model.CheckMode
import com.alhosan.checker.data.model.CheckerInput
import com.alhosan.checker.data.model.CheckerState
import com.alhosan.checker.data.model.HistoryItem
import com.alhosan.checker.data.model.ProgressPhase
import com.alhosan.checker.data.model.Subscription
import com.alhosan.checker.data.repository.CheckerRepository
import com.alhosan.checker.ui.i18n.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * ViewModel for the checker feature - manages all app state.
 * Matches the HTML reference's JavaScript state management.
 */
class CheckerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CheckerRepository()
    private val prefs = application.getSharedPreferences("alhosan_prefs", 0)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ─── Input fields ───
    private val _host = MutableStateFlow("")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _m3uLink = MutableStateFlow("")
    val m3uLink: StateFlow<String> = _m3uLink.asStateFlow()

    private val _obscurePassword = MutableStateFlow(true)
    val obscurePassword: StateFlow<Boolean> = _obscurePassword.asStateFlow()

    // ─── Mode & Language ───
    private val _checkMode = MutableStateFlow(CheckMode.XTREAM)
    val checkMode: StateFlow<CheckMode> = _checkMode.asStateFlow()

    private val _lang = MutableStateFlow(AppLang.entries[prefs.getInt("lang", 0).coerceIn(0, AppLang.entries.lastIndex)])
    val lang: StateFlow<AppLang> = _lang.asStateFlow()

    // ─── Checker state ───
    private val _state = MutableStateFlow<CheckerState>(CheckerState.Idle)
    val state: StateFlow<CheckerState> = _state.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    // ─── Progress ───
    private val _progressPhase = MutableStateFlow(ProgressPhase.IDLE)
    val progressPhase: StateFlow<ProgressPhase> = _progressPhase.asStateFlow()

    // ─── Content counts loading ───
    private val _isCounting = MutableStateFlow(false)
    val isCounting: StateFlow<Boolean> = _isCounting.asStateFlow()

    // ─── Toast ───
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // ─── Modal ───
    private val _modalMessage = MutableStateFlow<String?>(null)
    val modalMessage: StateFlow<String?> = _modalMessage.asStateFlow()

    private var modalConfirmCallback: (() -> Unit)? = null

    // ─── History ───
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    // ─── Track whether current result is from a restored history item ───
    // When true, the ResultScreen hides the Save button (matches HTML reference behavior)
    private val _isFromHistory = MutableStateFlow(false)
    val isFromHistory: StateFlow<Boolean> = _isFromHistory.asStateFlow()

    private var lastSavedHash = ""

    init {
        loadHistory()
    }

    // ─── Input updaters ───
    fun updateHost(value: String) { _host.value = value }
    fun updateUsername(value: String) { _username.value = value }
    fun updatePassword(value: String) { _password.value = value }
    fun updateM3uLink(value: String) { _m3uLink.value = value }
    fun togglePasswordVisibility() { _obscurePassword.value = !_obscurePassword.value }

    fun setCheckMode(mode: CheckMode) {
        _checkMode.value = mode
    }

    fun toggleLang() {
        val newLang = if (_lang.value == AppLang.AR) AppLang.EN else AppLang.AR
        _lang.value = newLang
        prefs.edit().putInt("lang", newLang.ordinal).apply()
    }

    // ─── Toast helper ───
    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    // ─── Modal helpers ───
    fun showModal(messageKey: String, onConfirm: () -> Unit) {
        _modalMessage.value = messageKey
        modalConfirmCallback = onConfirm
    }

    fun onModalConfirm() {
        _modalMessage.value = null
        modalConfirmCallback?.invoke()
        modalConfirmCallback = null
    }

    fun onModalCancel() {
        _modalMessage.value = null
        modalConfirmCallback = null
    }

    /**
     * Start the subscription check process.
     * Matches the HTML reference's performCheck() function with progress animation.
     */
    fun checkSubscription() {
        if (_isChecking.value) return
        lastSavedHash = ""  // reset so same subscription can be re-saved after a fresh check

        // ── Network connectivity guard ──────────────────────────────────────
        // Fail fast with a clear message instead of waiting for the 6-second
        // connect timeout when there is no internet at all.
        if (!isNetworkAvailable()) {
            _state.value = CheckerState.Error(_lang.value.diagnosticMessage("network_unreachable"))
            return
        }

        val isM3u = _checkMode.value == CheckMode.M3U

        val input = if (isM3u) {
            CheckerInput(
                host = "",
                username = "",
                password = "",
                isM3uMode = true,
                m3uLink = _m3uLink.value.trim()
            )
        } else {
            CheckerInput(
                host = _host.value.trim(),
                username = _username.value.trim(),
                password = _password.value.trim()
            )
        }

        if (!input.isValid) {
            val msg = if (isM3u) _lang.value.tValM3u else _lang.value.tVal
            _state.value = CheckerState.Error(msg)
            return
        }

        _isChecking.value = true
        _state.value = CheckerState.Loading(ProgressPhase.CONNECTING)
        _isFromHistory.value = false  // fresh check — Save button should be visible

        viewModelScope.launch {
            try {
                // Phase 1: Connecting (35%)
                _progressPhase.value = ProgressPhase.CONNECTING
                delay(500)

                // Phase 2: Verifying (75%)
                _progressPhase.value = ProgressPhase.VERIFYING
                delay(300)

                // Actual API call
                val result = repository.checkSubscription(input)

                // Phase 4: Finalizing (100%)
                _progressPhase.value = ProgressPhase.FINALIZING
                delay(300)

                _isChecking.value = false
                _progressPhase.value = ProgressPhase.IDLE

                result.fold(
                    onSuccess = { subscription ->
                        if (subscription.hasError) {
                            _state.value = CheckerState.Error(subscription.error)
                        } else {
                            _state.value = CheckerState.Success(subscription)
                            showToast(_lang.value.tD)

                            // Fetch content counts in background whenever we have
                            // real Xtream credentials — including links entered
                            // through M3U mode. Playlist-only M3U results already
                            // carry their parsed counts and use password "--".
                            val needsContentCount =
                                subscription.host.startsWith("http") &&
                                    subscription.username.isNotBlank() &&
                                    subscription.username != "M3U Link" &&
                                    subscription.password.isNotBlank() &&
                                    subscription.password != "--" &&
                                    subscription.liveCount == "0" &&
                                    subscription.movieCount == "0" &&
                                    subscription.seriesCount == "0"
                            if (needsContentCount) {
                                fetchContentCounts(subscription)
                            }
                        }
                    },
                    onFailure = { exception ->
                        // Use the new diagnostic system — precise, user-facing message
                        val errorCode = exception.message ?: "unknown"
                        val msg = _lang.value.diagnosticMessage(errorCode)
                        _state.value = CheckerState.Error(msg)
                    }
                )
            } catch (e: Exception) {
                _isChecking.value = false
                _progressPhase.value = ProgressPhase.IDLE
                val errorCode = e.message ?: "unknown"
                _state.value = CheckerState.Error(_lang.value.diagnosticMessage(errorCode))
            }
        }
    }

    /**
     * Fetch content counts in the background with progressive UI updates.
     *
     * Each category (live / movies / series) is requested in parallel and
     * applied to the result as soon as it returns, so the user sees real
     * numbers appear one-by-one (gold count-up → white when all done).
     * When all three finish, a toast confirms the content was counted.
     */
    private fun fetchContentCounts(subscription: Subscription) {
        viewModelScope.launch {
            _isCounting.value = true

            // Start every field as blank ("") = pending. The UI runs the live
            // gold counter while blank, then animates 0 → real count the
            // moment each category's real number arrives. A real "0" from the
            // server snaps to white immediately.
            applyContentCounts("", "", "", force = true)

            try {
                val (live, movie, series) = repository.fetchContentCounts(
                    host = subscription.host,
                    username = subscription.username,
                    password = subscription.password,
                    onField = { field, value ->
                        // Each field's real number is applied INDEPENDENTLY the
                        // instant it arrives — no snapshot, no waiting for
                        // siblings. Channels / movies / series each animate on
                        // their own and turn white as soon as their own count
                        // finishes, regardless of what the others are doing.
                        withContext(Dispatchers.Main.immediate) {
                            applySingleCount(field, value)
                        }
                    }
                )
                // Final apply in case any onField callbacks were missed.
                applyContentCounts(live, movie, series, force = false)
                showToast(_lang.value.tContentCounted)
            } catch (_: Exception) {
                // Exception during fetch — will be cleaned up in finally block below.
            } finally {
                // CRITICAL: ensure no field stays stuck in pending ("") state
                // when counting ends. Failed fields (returned "" after retries)
                // become "0" so the random counter stops and the field turns white.
                // Without this, a failed field runs its random counter forever,
                // showing a random number every time the user looks at it.
                val sub = (_state.value as? CheckerState.Success)?.subscription
                if (sub != null) {
                    val l = if (isPendingCount(sub.liveCount))   "0" else sub.liveCount
                    val m = if (isPendingCount(sub.movieCount))  "0" else sub.movieCount
                    val s = if (isPendingCount(sub.seriesCount)) "0" else sub.seriesCount
                    if (l != sub.liveCount || m != sub.movieCount || s != sub.seriesCount) {
                        applyContentCounts(l, m, s, force = false)
                    }
                }
                _isCounting.value = false
            }
        }
    }

    /**
     * Apply a single field's real count. Called the moment that field's
     * server request finishes — independently of the other two fields.
     */
    private fun applySingleCount(field: CheckerRepository.ContentField, value: String) {
        val currentSub = (_state.value as? CheckerState.Success)?.subscription ?: return
        val newSub = when (field) {
            CheckerRepository.ContentField.LIVE ->
                currentSub.copy(liveCount = mergeCount(currentSub.liveCount, value, force = false))
            CheckerRepository.ContentField.MOVIE ->
                currentSub.copy(movieCount = mergeCount(currentSub.movieCount, value, force = false))
            CheckerRepository.ContentField.SERIES ->
                currentSub.copy(seriesCount = mergeCount(currentSub.seriesCount, value, force = false))
        }
        _state.value = CheckerState.Success(newSub)
    }

    /**
     * Patch the live/movie/series fields of the current Success subscription.
     *
     * Progressive partial updates must never "downgrade" a real number back to
     * a pending marker if an older snapshot arrives slightly out of order.
     * Pass [force] = true for the initial pending state and the final result.
     */
    private fun applyContentCounts(
        live: String,
        movie: String,
        series: String,
        force: Boolean = false
    ) {
        val currentSub = (_state.value as? CheckerState.Success)?.subscription ?: return
        _state.value = CheckerState.Success(
            currentSub.copy(
                liveCount = mergeCount(currentSub.liveCount, live, force),
                movieCount = mergeCount(currentSub.movieCount, movie, force),
                seriesCount = mergeCount(currentSub.seriesCount, series, force)
            )
        )
    }

    private fun isPendingCount(value: String): Boolean {
        val v = value.trim()
        return v.isEmpty() || v == "…" || v == "..." || v == "-" || v == "--"
    }

    private fun isRealCount(value: String): Boolean {
        val v = value.trim()
        return v.isNotEmpty() && v.all { it.isDigit() }
    }

    private fun mergeCount(current: String, incoming: String, force: Boolean): String {
        if (force) return incoming
        // Never replace a finished real count with a still-pending marker.
        if (isPendingCount(incoming) && isRealCount(current)) return current
        // Never downgrade a real positive count to "0" — a partial "0"
        // arriving from a failed/retried category request must NOT overwrite
        // a real number we already received. This prevents the UI counter
        // from restarting after the field already turned white.
        if (isRealCount(current) && isRealCount(incoming)) {
            val cur = current.toIntOrNull() ?: 0
            val inc = incoming.toIntOrNull() ?: 0
            if (cur > 0 && inc == 0) return current
        }
        return incoming
    }

    fun resetState() {
        _state.value = CheckerState.Idle
    }

    /**
     * Save current subscription to history (matches HTML reference's saveToMemory)
     */
    fun saveToHistory() {
        val sub = (_state.value as? CheckerState.Success)?.subscription ?: return
        val hash = "${sub.host}|${sub.username}"
        if (hash == lastSavedHash) {
            showToast(_lang.value.tAlreadySaved)
            return
        }
        lastSavedHash = hash

        val item = HistoryItem(
            host = sub.host,
            user = sub.username,
            pass = sub.password,
            status = if (sub.isActive) _lang.value.on else _lang.value.off,
            created = sub.created,
            expiry = sub.expiry,
            activeCons = sub.activeCons,
            maxCons = sub.maxCons,
            content = "${sub.liveCount} | ${sub.movieCount} | ${sub.seriesCount}",
            trial = if (sub.isTrial) _lang.value.yes else _lang.value.no,
            time = java.text.SimpleDateFormat(
                "yyyy/MM/dd HH:mm",
                java.util.Locale.getDefault()
            ).format(java.util.Date()),
            isActive = sub.isActive,
            isM3uMode = sub.isM3uMode,
            m3uLink = sub.m3uLink,
            isTrial = sub.isTrial  // store boolean to avoid language-mismatch on restore
        )

        val logs = _history.value.toMutableList()
        val exists = logs.indexOfFirst { it.host == item.host && it.user == item.user }
        if (exists != -1) logs.removeAt(exists)
        logs.add(0, item)

        _history.value = logs.take(30)
        saveHistory()
        showToast(_lang.value.tS)
    }

    /**
     * Delete a history item
     */
    fun deleteHistoryItem(index: Int) {
        val logs = _history.value.toMutableList()
        if (index in logs.indices) {
            logs.removeAt(index)
            _history.value = logs
            saveHistory()
            showToast(_lang.value.tDelLog)
        }
    }

    /**
     * Clear all history
     */
    fun clearHistory() {
        _history.value = emptyList()
        saveHistory()
        showToast(_lang.value.tClear)
    }

    /**
     * Restore a history item to result screen
     * Sets the ViewModel state so ResultScreen can display it
     */
    fun restoreHistoryItem(index: Int): Boolean {
        val item = _history.value.getOrNull(index) ?: return false
        val subscription = Subscription(
            host = item.host,
            username = item.user,
            password = item.pass,
            status = if (item.isActive) "Active" else "Disabled",
            expiry = item.expiry,
            created = item.created,
            activeCons = item.activeCons,
            maxCons = item.maxCons,
            isTrial = item.isTrial,  // use stored boolean — avoids language-mismatch bug
            liveCount = item.content.split("|").getOrNull(0)?.trim() ?: "0",
            movieCount = item.content.split("|").getOrNull(1)?.trim() ?: "0",
            seriesCount = item.content.split("|").getOrNull(2)?.trim() ?: "0",
            isM3uMode = item.isM3uMode,
            m3uLink = item.m3uLink
        )
        _state.value = CheckerState.Success(subscription)
        _isFromHistory.value = true  // restored item — hide Save button (matches HTML)
        return true
    }

    /**
     * Generate M3U link from subscription
     */
    fun generateM3uLink(): String {
        val sub = (_state.value as? CheckerState.Success)?.subscription ?: return ""
        return repository.generateM3uLink(sub)
    }

    private fun loadHistory() {
        val stored = prefs.getString("horse_final_v6", null)
        if (stored != null) {
            try {
                _history.value = json.decodeFromString<List<HistoryItem>>(stored)
            } catch (_: Exception) { }
        }
    }

    private fun saveHistory() {
        try {
            val encoded = json.encodeToString(
                kotlinx.serialization.serializer<List<HistoryItem>>(),
                _history.value
            )
            prefs.edit().putString("horse_final_v6", encoded).apply()
        } catch (_: Exception) { }
    }

    /**
     * Returns true if the device has an active internet connection.
     * Works on API 21+ (Lollipop) through API 34+ (Android 14).
     */
    @Suppress("DEPRECATION")
    private fun isNetworkAvailable(): Boolean {
        val cm = getApplication<Application>()
            .getSystemService(ConnectivityManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            cm.activeNetworkInfo?.isConnectedOrConnecting == true
        }
    }
}


