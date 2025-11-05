package com.browniepoints.app.data.service

import android.util.Log
import com.browniepoints.app.data.error.AppError
import com.browniepoints.app.data.error.toAppError
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class RetryManager @Inject constructor(
    private val networkMonitorService: NetworkMonitorService
) {
    
    companion object {
        private const val TAG = "RetryManager"
        private const val DEFAULT_MAX_RETRIES = 3
        private const val DEFAULT_BASE_DELAY_MS = 1000L
        private const val DEFAULT_MAX_DELAY_MS = 10000L
        private const val BACKOFF_MULTIPLIER = 2.0
    }
    
    /**
     * Executes an operation with exponential backoff retry logic
     */
    suspend fun <T> executeWithRetry(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        shouldRetry: (AppError) -> Boolean = ::defaultShouldRetry,
        operation: suspend () -> T
    ): Result<T> {
        var lastError: AppError? = null
        
        repeat(maxRetries + 1) { attempt ->
            try {
                // Check network connectivity for network-dependent operations
                if (attempt > 0 && !networkMonitorService.isCurrentlyOnline()) {
                    Log.d(TAG, "Skipping retry attempt $attempt - network offline")
                    delay(calculateDelay(attempt, baseDelayMs, maxDelayMs))
                    return@repeat
                }
                
                val result = operation()
                
                if (attempt > 0) {
                    Log.d(TAG, "Operation succeeded on retry attempt $attempt")
                }
                
                return Result.success(result)
                
            } catch (e: Exception) {
                val appError = e.toAppError()
                lastError = appError
                
                Log.w(TAG, "Operation failed on attempt $attempt: ${appError.message}", e)
                
                // Don't retry if this is the last attempt or if we shouldn't retry this error
                if (attempt == maxRetries || !shouldRetry(appError)) {
                    Log.d(TAG, "Not retrying - attempt $attempt/$maxRetries, shouldRetry: ${shouldRetry(appError)}")
                    return Result.failure(lastError ?: AppError.UnknownError("Operation failed after retries"))
                }
                
                val delayMs = calculateDelay(attempt + 1, baseDelayMs, maxDelayMs)
                Log.d(TAG, "Retrying in ${delayMs}ms (attempt ${attempt + 1}/$maxRetries)")
                delay(delayMs)
            }
        }
        
        return Result.failure(lastError ?: AppError.UnknownError("Operation failed after retries"))
    }
    
    /**
     * Executes an operation with simple retry logic (no exponential backoff)
     */
    suspend fun <T> executeWithSimpleRetry(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        delayMs: Long = DEFAULT_BASE_DELAY_MS,
        shouldRetry: (AppError) -> Boolean = ::defaultShouldRetry,
        operation: suspend () -> T
    ): Result<T> {
        return executeWithRetry(
            maxRetries = maxRetries,
            baseDelayMs = delayMs,
            maxDelayMs = delayMs,
            shouldRetry = shouldRetry,
            operation = operation
        )
    }
    
    /**
     * Executes an operation that requires network connectivity
     */
    suspend fun <T> executeWithNetworkRetry(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        operation: suspend () -> T
    ): Result<T> {
        return executeWithRetry(
            maxRetries = maxRetries,
            shouldRetry = ::shouldRetryNetworkOperation,
            operation = operation
        )
    }
    
    private fun calculateDelay(attempt: Int, baseDelayMs: Long, maxDelayMs: Long): Long {
        val exponentialDelay = (baseDelayMs * BACKOFF_MULTIPLIER.pow(attempt - 1)).toLong()
        return minOf(exponentialDelay, maxDelayMs)
    }
    
    private fun defaultShouldRetry(error: AppError): Boolean {
        return when (error) {
            is AppError.NetworkError,
            is AppError.NoInternetError,
            is AppError.FirestoreError,
            is AppError.TimeoutError,
            is AppError.TransactionFailedError -> true
            
            is AppError.AuthenticationError,
            is AppError.UserNotSignedInError,
            is AppError.DocumentNotFoundError,
            is AppError.PermissionDeniedError,
            is AppError.ValidationError,
            is AppError.ConnectionError,
            is AppError.InvalidMatchingCodeError,
            is AppError.InsufficientPointsError -> false
            
            is AppError.UnknownError -> true // Retry unknown errors
        }
    }
    
    private fun shouldRetryNetworkOperation(error: AppError): Boolean {
        return when (error) {
            is AppError.NetworkError,
            is AppError.NoInternetError,
            is AppError.TimeoutError -> networkMonitorService.isCurrentlyOnline()
            
            is AppError.FirestoreError -> true // Firestore errors are often transient
            
            else -> false
        }
    }
}

/**
 * Extension function to easily add retry logic to suspend functions
 */
suspend fun <T> (suspend () -> T).withRetry(
    retryManager: RetryManager,
    maxRetries: Int = 3,
    baseDelayMs: Long = 1000L,
    maxDelayMs: Long = 10000L,
    shouldRetry: (AppError) -> Boolean = { error ->
        when (error) {
            is AppError.NetworkError,
            is AppError.NoInternetError,
            is AppError.FirestoreError,
            is AppError.TimeoutError,
            is AppError.TransactionFailedError,
            is AppError.UnknownError -> true
            else -> false
        }
    }
): Result<T> {
    return retryManager.executeWithRetry(
        maxRetries = maxRetries,
        baseDelayMs = baseDelayMs,
        maxDelayMs = maxDelayMs,
        shouldRetry = shouldRetry,
        operation = this
    )
}