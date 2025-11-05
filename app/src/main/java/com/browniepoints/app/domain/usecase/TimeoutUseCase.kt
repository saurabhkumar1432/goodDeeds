package com.browniepoints.app.domain.usecase

import android.util.Log
import com.browniepoints.app.data.model.Timeout
import com.browniepoints.app.data.repository.TimeoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TimeoutUseCase @Inject constructor(
    private val timeoutRepository: TimeoutRepository
) {
    companion object {
        private const val TAG = "TimeoutUseCase"
    }

    /**
     * Requests a timeout for a user in a connection
     * Validates daily allowance before creating timeout
     */
    suspend fun requestTimeout(
        userId: String,
        connectionId: String
    ): Result<Timeout> {
        return try {
            Log.d(TAG, "Requesting timeout for user: $userId, connection: $connectionId")
            
            // Check if user can request timeout today
            val canRequest = timeoutRepository.canRequestTimeout(userId).getOrThrow()
            if (!canRequest) {
                Log.w(TAG, "User $userId has already used their daily timeout allowance")
                return Result.failure(TimeoutException.DailyLimitExceeded("You have already used your daily timeout allowance"))
            }
            
            // Check if there's already an active timeout for this connection
            val activeTimeout = timeoutRepository.getActiveTimeout(connectionId).getOrThrow()
            if (activeTimeout != null) {
                Log.w(TAG, "Connection $connectionId already has an active timeout")
                return Result.failure(TimeoutException.TimeoutAlreadyActive("A timeout is already active for this connection"))
            }
            
            // Create the timeout
            val timeout = timeoutRepository.createTimeout(userId, connectionId).getOrThrow()
            Log.d(TAG, "Timeout created successfully: ${timeout.id}")
            
            Result.success(timeout)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting timeout", e)
            when (e) {
                is TimeoutException -> Result.failure(e)
                else -> Result.failure(TimeoutException.UnknownError("Failed to request timeout: ${e.message}"))
            }
        }
    }

    /**
     * Checks if a user can request a timeout today
     */
    suspend fun canRequestTimeout(userId: String): Result<Boolean> {
        return timeoutRepository.canRequestTimeout(userId)
    }

    /**
     * Gets the active timeout for a connection
     */
    suspend fun getActiveTimeout(connectionId: String): Result<Timeout?> {
        return timeoutRepository.getActiveTimeout(connectionId)
    }

    /**
     * Observes the active timeout for a connection
     * Returns null when no timeout is active or when timeout expires
     */
    fun observeActiveTimeout(connectionId: String): Flow<Timeout?> {
        return timeoutRepository.observeActiveTimeout(connectionId)
    }

    /**
     * Observes timeout status for a connection
     * Returns true if any timeout is active, false otherwise
     */
    fun observeTimeoutStatus(connectionId: String): Flow<Boolean> {
        return timeoutRepository.observeTimeoutStatus(connectionId)
    }

    /**
     * Gets remaining time for active timeout in a connection
     * Returns 0 if no active timeout
     */
    fun observeRemainingTime(connectionId: String): Flow<Long> {
        return observeActiveTimeout(connectionId).map { timeout ->
            timeout?.getRemainingTimeMs() ?: 0L
        }
    }

    /**
     * Checks if transactions should be disabled for a connection
     * Returns true if there's an active, non-expired timeout
     */
    fun observeTransactionsDisabled(connectionId: String): Flow<Boolean> {
        return observeTimeoutStatus(connectionId)
    }

    /**
     * Manually expires a timeout (for testing or admin purposes)
     */
    suspend fun expireTimeout(timeoutId: String): Result<Unit> {
        return timeoutRepository.expireTimeout(timeoutId)
    }

    /**
     * Gets timeout history for a user
     */
    suspend fun getTimeoutHistory(userId: String): Result<List<Timeout>> {
        return timeoutRepository.getTimeoutHistory(userId)
    }

    /**
     * Gets today's timeout count for a user
     */
    suspend fun getTodayTimeoutCount(userId: String): Result<Int> {
        return timeoutRepository.getTodayTimeoutCount(userId)
    }

    /**
     * Performs cleanup of expired timeouts
     * Should be called periodically to maintain data consistency
     */
    suspend fun cleanupExpiredTimeouts(): Result<Unit> {
        return try {
            Log.d(TAG, "Performing timeout cleanup")
            timeoutRepository.cleanupExpiredTimeouts().getOrThrow()
            Log.d(TAG, "Timeout cleanup completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error during timeout cleanup", e)
            Result.failure(e)
        }
    }

    /**
     * Synchronizes timeout state between partners in a connection
     */
    suspend fun synchronizePartnerTimeoutState(connectionId: String): Result<Unit> {
        return timeoutRepository.synchronizePartnerTimeoutState(connectionId)
    }

    /**
     * Validates timeout request parameters
     */
    private fun validateTimeoutRequest(userId: String, connectionId: String): Result<Unit> {
        return try {
            if (userId.isBlank()) {
                return Result.failure(TimeoutException.InvalidRequest("User ID cannot be empty"))
            }
            if (connectionId.isBlank()) {
                return Result.failure(TimeoutException.InvalidRequest("Connection ID cannot be empty"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(TimeoutException.InvalidRequest("Invalid timeout request: ${e.message}"))
        }
    }
}

/**
 * Custom exceptions for timeout operations
 */
sealed class TimeoutException(message: String) : Exception(message) {
    class DailyLimitExceeded(message: String) : TimeoutException(message)
    class TimeoutAlreadyActive(message: String) : TimeoutException(message)
    class InvalidRequest(message: String) : TimeoutException(message)
    class UnknownError(message: String) : TimeoutException(message)
}