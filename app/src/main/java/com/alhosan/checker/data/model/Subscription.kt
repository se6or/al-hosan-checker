package com.alhosan.checker.data.model

import kotlinx.serialization.Serializable

/**
 * Data model representing an Xtream subscription check result.
 * Matches the Rust core's SubscriptionResult structure.
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
    val liveCount: String = "?",
    val movieCount: String = "?",
    val seriesCount: String = "?",
    val serverUrl: String = "",
    val serverProtocol: String = "",
    val timezone: String = "",
    val error: String = ""
) {
    /** Whether the subscription is active */
    val isActive: Boolean get() = status.equals("Active", ignoreCase = true)

    /** Display-friendly connection info */
    val connectionInfo: String get() = "$activeCons / $maxCons"

    /** Whether there was an error */
    val hasError: Boolean get() = error.isNotEmpty()

    /** Formatted trial status */
    val trialText: String get() = if (isTrial) "نعم" else "لا"
}

/**
 * Input for the checker
 */
data class CheckerInput(
    val host: String,
    val username: String,
    val password: String
) {
    val isValid: Boolean get() = host.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

/**
 * UI state for the checker flow
 */
sealed interface CheckerState {
    data object Idle : CheckerState
    data object Loading : CheckerState
    data class Success(val subscription: Subscription) : CheckerState
    data class Error(val message: String) : CheckerState
}
