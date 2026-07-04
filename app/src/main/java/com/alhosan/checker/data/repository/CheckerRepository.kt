package com.alhosan.checker.data.repository

import com.alhosan.checker.bridge.RustBridge
import com.alhosan.checker.data.model.CheckerInput
import com.alhosan.checker.data.model.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository layer that bridges the UI with the Rust core.
 * All heavy processing is delegated to Rust via JNI.
 */
class CheckerRepository {

    /**
     * Check a single Xtream subscription using Rust core
     * Runs on IO dispatcher to avoid blocking the main thread
     */
    suspend fun checkSubscription(input: CheckerInput): Result<Subscription> =
        withContext(Dispatchers.IO) {
            if (!input.isValid) {
                return@withContext Result.failure(IllegalArgumentException("جميع الحقول مطلوبة"))
            }

            val host = input.host.trimEnd('/')
            RustBridge.checkXtream(host, input.username, input.password)
        }

    /**
     * Batch check multiple subscriptions using Rust core with rayon parallelism
     */
    suspend fun batchCheck(inputs: List<CheckerInput>): Result<List<Subscription>> =
        withContext(Dispatchers.IO) {
            val accounts = inputs.map { Triple(it.host, it.username, it.password) }
            RustBridge.batchCheck(accounts)
        }

    /**
     * Get Rust core version
     */
    fun getCoreVersion(): String = RustBridge.getVersion()
}
