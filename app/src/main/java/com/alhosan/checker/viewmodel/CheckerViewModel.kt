package com.alhosan.checker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alhosan.checker.data.model.CheckerInput
import com.alhosan.checker.data.model.CheckerState
import com.alhosan.checker.data.model.Subscription
import com.alhosan.checker.data.repository.CheckerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the checker feature.
 * Manages UI state and delegates processing to the repository (which uses Rust core).
 */
class CheckerViewModel : ViewModel() {

    private val repository = CheckerRepository()

    private val _state = MutableStateFlow<CheckerState>(CheckerState.Idle)
    val state: StateFlow<CheckerState> = _state.asStateFlow()

    private val _host = MutableStateFlow("")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _obscurePassword = MutableStateFlow(true)
    val obscurePassword: StateFlow<Boolean> = _obscurePassword.asStateFlow()

    fun updateHost(value: String) { _host.value = value }
    fun updateUsername(value: String) { _username.value = value }
    fun updatePassword(value: String) { _password.value = value }
    fun togglePasswordVisibility() { _obscurePassword.value = !_obscurePassword.value }

    /**
     * Start the subscription check process.
     * The heavy HTTP parsing is done in Rust via JNI on the IO thread pool.
     */
    fun checkSubscription() {
        val input = CheckerInput(
            host = _host.value,
            username = _username.value,
            password = _password.value
        )

        if (!input.isValid) {
            _state.value = CheckerState.Error("يرجى ملء جميع الحقول")
            return
        }

        viewModelScope.launch {
            _state.value = CheckerState.Loading

            val result = repository.checkSubscription(input)

            _state.value = result.fold(
                onSuccess = { subscription ->
                    if (subscription.hasError) {
                        CheckerState.Error(subscription.error)
                    } else {
                        CheckerState.Success(subscription)
                    }
                },
                onFailure = { exception ->
                    CheckerState.Error(exception.message ?: "فشل الفحص، تأكد من البيانات")
                }
            )
        }
    }

    /**
     * Reset state back to idle
     */
    fun resetState() {
        _state.value = CheckerState.Idle
    }

    /**
     * Get the Rust core version
     */
    fun getCoreVersion(): String = repository.getCoreVersion()
}
