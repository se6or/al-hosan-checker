package com.alhosan.checker.viewmodel

import android.app.Application
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val _progressPercent = MutableStateFlow(0)
    val progressPercent: StateFlow<Int> = _progressPercent.asStateFlow()

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
                _progressPercent.value = 35
                delay(500)

                // Phase 2: Verifying (75%)
                _progressPhase.value = ProgressPhase.VERIFYING
                _progressPercent.value = 75
                delay(300)

                // Actual API call
                val result = repository.checkSubscription(input)

                // Phase 4: Finalizing (100%)
                _progressPhase.value = ProgressPhase.FINALIZING
                _progressPercent.value = 100
                delay(300)

                _isChecking.value = false
                _progressPhase.value = ProgressPhase.IDLE
                _progressPercent.value = 0

                result.fold(
                    onSuccess = { subscription ->
                        if (subscription.hasError) {
                            _state.value = CheckerState.Error(subscription.error)
                        } else {
                            _state.value = CheckerState.Success(subscription)
                            showToast(_lang.value.tD)

                            // Fetch content counts in background
                            if (!subscription.isM3uMode) {
                                fetchContentCounts(subscription)
                            }
                        }
                    },
                    onFailure = { exception ->
                        val msg = when (exception.message) {
                            "auth_failed" -> _lang.value.tErrAuth
                            "not_xtream" -> _lang.value.tErrNotXtream
                            "timeout" -> _lang.value.tErrTimeout
                            "network" -> _lang.value.tErrNetwork
                            "http_error" -> _lang.value.tErrNetwork
                            else -> _lang.value.tErrNetwork
                        }
                        _state.value = CheckerState.Error(msg)
                    }
                )
            } catch (e: Exception) {
                _isChecking.value = false
                _progressPhase.value = ProgressPhase.IDLE
                _progressPercent.value = 0
                _state.value = CheckerState.Error(_lang.value.tErrNetwork)
            }
        }
    }

    /**
     * Fetch content counts in background (matches HTML reference's fetchContentCounts)
     */
    private fun fetchContentCounts(subscription: Subscription) {
        viewModelScope.launch {
            _isCounting.value = true
            try {
                val (live, movie, series) = repository.fetchContentCounts(
                    subscription.host,
                    subscription.username,
                    subscription.password
                )
                val currentSub = (_state.value as? CheckerState.Success)?.subscription
                if (currentSub != null) {
                    _state.value = CheckerState.Success(
                        currentSub.copy(
                            liveCount = live,
                            movieCount = movie,
                            seriesCount = series
                        )
                    )
                }
            } catch (_: Exception) { }
            _isCounting.value = false
        }
    }

    fun resetState() {
        _state.value = CheckerState.Idle
    }

    /**
     * Save current subscription to history (matches HTML reference's saveToMemory)
     */
    fun saveToHistory() {
        val sub = (_state.value as? CheckerState.Success)?.subscription ?: return
        val hash = sub.host + sub.username
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
            m3uLink = sub.m3uLink
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
            isTrial = item.trial == _lang.value.yes,
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
}
