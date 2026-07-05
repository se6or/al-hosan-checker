package com.alhosan.checker.data.model

import kotlinx.serialization.Serializable

/**
 * Data model representing an Xtream subscription check result.
 * Matches the HTML reference's result data structure.
 */
@Serializable
data class Subscription(
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val status: String = "Unknown",
    val expiry: String = "--",
    val created: String = "--",
    val activeCons: String = "0",
    val maxCons: String = "0",
    val isTrial: Boolean = false,
    val liveCount: String = "0",
    val movieCount: String = "0",
    val seriesCount: String = "0",
    val serverUrl: String = "",
    val serverProtocol: String = "",
    val timezone: String = "",
    val error: String = "",
    val isM3uMode: Boolean = false,
    val m3uLink: String = ""
) {
    val isActive: Boolean get() = status.equals("Active", ignoreCase = true)
    val hasError: Boolean get() = error.isNotEmpty()
}

/**
 * Input for the checker - supports both Xtream and M3U modes
 */
data class CheckerInput(
    val host: String,
    val username: String,
    val password: String,
    val isM3uMode: Boolean = false,
    val m3uLink: String = ""
) {
    val isValid: Boolean
        get() = if (isM3uMode) {
            m3uLink.isNotBlank() && m3uLink.length > 10 && m3uLink.startsWith("http")
        } else {
            host.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        }
}

/**
 * Check mode: Xtream or M3U Link
 */
enum class CheckMode { XTREAM, M3U }

/**
 * Language: Arabic or English
 */
enum class AppLang { AR, EN }

/**
 * Progress phase during checking
 */
enum class ProgressPhase(val step: Int) {
    IDLE(0),
    CONNECTING(1),
    VERIFYING(2),
    COUNTING(3),
    FINALIZING(4)
}

/**
 * UI state for the checker flow
 */
sealed interface CheckerState {
    data object Idle : CheckerState
    data class Loading(val phase: ProgressPhase = ProgressPhase.CONNECTING) : CheckerState
    data class Success(val subscription: Subscription) : CheckerState
    data class Error(val message: String) : CheckerState
}

/**
 * History item for saved subscriptions
 */
@Serializable
data class HistoryItem(
    val host: String,
    val user: String,
    val pass: String,
    val status: String,
    val created: String,
    val expiry: String,
    val activeCons: String,
    val maxCons: String,
    val content: String,
    val trial: String,
    val time: String,
    val isActive: Boolean,
    val isM3uMode: Boolean = false,
    val m3uLink: String = ""
)
