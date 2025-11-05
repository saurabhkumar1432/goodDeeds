package com.browniepoints.app.data.util

import com.browniepoints.app.data.error.AppError
import com.browniepoints.app.data.error.toAppError
import kotlinx.coroutines.delay
import kotlin.math.pow

object RetryUtil {
    
    suspend fun <T> retryWithExponentialBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        maxDelayMs: Long = 10000,
        backoffMultiplier: Double = 2.0,
        retryCondition: (Throwable) -> Boolean = ::defaultRetryCondition,
        operation: suspend () -> T
    ): Result<T> {
        var lastException: Throwable? = null
        
        for (attempt in 0..maxRetries) {
            try {
                return Result.success(operation())
            } catch (e: Throwable) {
                lastException = e
                
                // Don't retry on the last attempt or if retry condition is not met
                if (attempt == maxRetries || !retryCondition(e)) {
                    break
                }
                
                // Calculate delay with exponential backoff
                val delayMs = minOf(
                    (initialDelayMs * backoffMultiplier.pow(attempt.toDouble())).toLong(),
                    maxDelayMs
                )
                
                delay(delayMs)
            }
        }
        
        return Result.failure(lastException?.toAppError() ?: AppError.UnknownError())
    }
    
    suspend fun <T> retryWithLinearBackoff(
        maxRetries: Int = 3,
        delayMs: Long = 1000,
        retryCondition: (Throwable) -> Boolean = ::defaultRetryCondition,
        operation: suspend () -> T
    ): Result<T> {
        var lastException: Throwable? = null
        
        for (attempt in 0..maxRetries) {
            try {
                return Result.success(operation())
            } catch (e: Throwable) {
                lastException = e
                
                // Don't retry on the last attempt or if retry condition is not met
                if (attempt == maxRetries || !retryCondition(e)) {
                    break
                }
                
                delay(delayMs * (attempt + 1))
            }
        }
        
        return Result.failure(lastException?.toAppError() ?: AppError.UnknownError())
    }
    
    private fun defaultRetryCondition(throwable: Throwable): Boolean {
        return when (throwable) {
            // Retry on network errors
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.SocketTimeoutException -> true
            
            // Retry on specific Firestore errors
            is com.google.firebase.firestore.FirebaseFirestoreException -> {
                when (throwable.code) {
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.INTERNAL -> true
                    else -> false
                }
            }
            
            // Don't retry on authentication errors or permission errors
            is com.google.firebase.auth.FirebaseAuthException -> false
            
            // Retry on other generic exceptions
            else -> true
        }
    }
    
    fun isRetryableError(error: AppError): Boolean {
        return when (error) {
            is AppError.NetworkError,
            is AppError.NoInternetError,
            is AppError.TimeoutError,
            is AppError.UnknownError -> true
            
            is AppError.FirestoreError -> true
            
            is AppError.AuthenticationError,
            is AppError.UserNotSignedInError,
            is AppError.PermissionDeniedError,
            is AppError.ValidationError,
            is AppError.InvalidMatchingCodeError,
            is AppError.DocumentNotFoundError -> false
            
            is AppError.ConnectionError,
            is AppError.InsufficientPointsError,
            is AppError.TransactionFailedError -> false
        }
    }
}