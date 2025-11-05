package com.browniepoints.app.data.service

import android.util.Log
import com.browniepoints.app.data.model.Timeout
import com.browniepoints.app.data.repository.TimeoutRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeoutManager @Inject constructor(
    private val timeoutRepository: TimeoutRepository,
    private val timeoutNotificationService: TimeoutNotificationService
) {
    companion object {
        private const val TAG = "TimeoutManager"
        private const val CLEANUP_INTERVAL_MS = 60_000L // 1 minute
        private const val EXPIRATION_CHECK_INTERVAL_MS = 10_000L // 10 seconds
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Map to track active timeout timers by connection ID
    private val activeTimers = mutableMapOf<String, Job>()
    
    // StateFlow to track timeout status for connections
    private val _timeoutStatuses = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val timeoutStatuses: StateFlow<Map<String, Boolean>> = _timeoutStatuses.asStateFlow()

    init {
        startPeriodicCleanup()
    }

    /**
     * Starts monitoring a timeout for automatic expiration
     * @param timeout The timeout to monitor
     */
    fun startTimeoutMonitoring(timeout: Timeout) {
        Log.d(TAG, "Starting timeout monitoring for: ${timeout.id}")
        
        // Cancel any existing timer for this connection
        stopTimeoutMonitoring(timeout.connectionId)
        
        // Start new timer
        val timerJob = scope.launch {
            try {
                val remainingTime = timeout.getRemainingTimeMs()
                if (remainingTime > 0) {
                    Log.d(TAG, "Timeout ${timeout.id} will expire in ${remainingTime}ms")
                    
                    // Update timeout status
                    updateTimeoutStatus(timeout.connectionId, true)
                    
                    // Wait for timeout to expire
                    delay(remainingTime)
                    
                    // Expire the timeout
                    Log.d(TAG, "Timeout ${timeout.id} has expired, marking as inactive")
                    timeoutRepository.expireTimeout(timeout.id)
                    
                    // Send expiration notification
                    val notificationResult = timeoutNotificationService.sendTimeoutExpirationNotification(timeout)
                    if (notificationResult.isFailure) {
                        Log.w(TAG, "Failed to send timeout expiration notification", notificationResult.exceptionOrNull())
                    }
                    
                    // Update timeout status
                    updateTimeoutStatus(timeout.connectionId, false)
                    
                    // Remove from active timers
                    activeTimers.remove(timeout.connectionId)
                } else {
                    Log.d(TAG, "Timeout ${timeout.id} has already expired")
                    // Immediately expire if already past expiration time
                    timeoutRepository.expireTimeout(timeout.id)
                    updateTimeoutStatus(timeout.connectionId, false)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Timeout monitoring cancelled for: ${timeout.id}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring timeout: ${timeout.id}", e)
            }
        }
        
        activeTimers[timeout.connectionId] = timerJob
    }

    /**
     * Stops monitoring a timeout for a connection
     * @param connectionId The connection ID to stop monitoring
     */
    fun stopTimeoutMonitoring(connectionId: String) {
        activeTimers[connectionId]?.let { job ->
            Log.d(TAG, "Stopping timeout monitoring for connection: $connectionId")
            job.cancel()
            activeTimers.remove(connectionId)
            updateTimeoutStatus(connectionId, false)
        }
    }

    /**
     * Observes timeout status for a specific connection
     * @param connectionId The connection ID to observe
     * @return Flow<Boolean> indicating if timeout is active
     */
    fun observeTimeoutStatus(connectionId: String): Flow<Boolean> {
        return combine(
            timeoutStatuses,
            timeoutRepository.observeTimeoutStatus(connectionId)
        ) { localStatuses, repoStatus ->
            // Use repository status as source of truth, but also consider local status
            repoStatus || (localStatuses[connectionId] == true)
        }.distinctUntilChanged()
    }

    /**
     * Observes the active timeout for a connection with automatic monitoring
     * @param connectionId The connection ID to observe
     * @return Flow<Timeout?> the active timeout or null
     */
    fun observeActiveTimeoutWithMonitoring(connectionId: String): Flow<Timeout?> {
        return timeoutRepository.observeActiveTimeout(connectionId)
            .onEach { timeout ->
                if (timeout != null && timeout.isActive && !timeout.hasExpired()) {
                    // Start monitoring this timeout
                    startTimeoutMonitoring(timeout)
                } else {
                    // Stop monitoring if no active timeout
                    stopTimeoutMonitoring(connectionId)
                }
            }
    }

    /**
     * Checks if transactions should be disabled for a connection
     * @param connectionId The connection ID to check
     * @return Flow<Boolean> true if transactions should be disabled
     */
    fun observeTransactionsDisabled(connectionId: String): Flow<Boolean> {
        return observeTimeoutStatus(connectionId)
    }

    /**
     * Gets remaining time for active timeout in a connection
     * @param connectionId The connection ID to check
     * @return Flow<Long> remaining time in milliseconds, 0 if no active timeout
     */
    fun observeRemainingTime(connectionId: String): Flow<Long> {
        return timeoutRepository.observeActiveTimeout(connectionId)
            .map { timeout ->
                timeout?.getRemainingTimeMs() ?: 0L
            }
            .distinctUntilChanged()
    }

    /**
     * Manually expires a timeout and stops monitoring
     * @param timeoutId The timeout ID to expire
     * @param connectionId The connection ID
     */
    suspend fun expireTimeout(timeoutId: String, connectionId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Manually expiring timeout: $timeoutId")
            
            // Stop monitoring
            stopTimeoutMonitoring(connectionId)
            
            // Expire in repository
            timeoutRepository.expireTimeout(timeoutId).getOrThrow()
            
            Log.d(TAG, "Timeout expired successfully: $timeoutId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error expiring timeout: $timeoutId", e)
            Result.failure(e)
        }
    }

    /**
     * Starts periodic cleanup of expired timeouts
     */
    private fun startPeriodicCleanup() {
        scope.launch {
            while (isActive) {
                try {
                    Log.d(TAG, "Running periodic timeout cleanup")
                    timeoutRepository.cleanupExpiredTimeouts()
                    
                    // Also clean up any stale local timers
                    cleanupStaleTimers()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error during periodic cleanup", e)
                }
                
                delay(CLEANUP_INTERVAL_MS)
            }
        }
    }

    /**
     * Cleans up stale local timers that may be out of sync
     */
    private suspend fun cleanupStaleTimers() {
        val connectionsToCleanup = mutableListOf<String>()
        
        activeTimers.keys.forEach { connectionId ->
            try {
                val activeTimeout = timeoutRepository.getActiveTimeout(connectionId).getOrNull()
                if (activeTimeout == null || activeTimeout.hasExpired()) {
                    connectionsToCleanup.add(connectionId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking timeout status for cleanup: $connectionId", e)
            }
        }
        
        connectionsToCleanup.forEach { connectionId ->
            Log.d(TAG, "Cleaning up stale timer for connection: $connectionId")
            stopTimeoutMonitoring(connectionId)
        }
    }

    /**
     * Updates the local timeout status for a connection
     */
    private fun updateTimeoutStatus(connectionId: String, isActive: Boolean) {
        val currentStatuses = _timeoutStatuses.value.toMutableMap()
        if (isActive) {
            currentStatuses[connectionId] = true
        } else {
            currentStatuses.remove(connectionId)
        }
        _timeoutStatuses.value = currentStatuses
    }

    /**
     * Cleanup method to be called when the service is destroyed
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up TimeoutManager")
        activeTimers.values.forEach { it.cancel() }
        activeTimers.clear()
        scope.cancel()
    }
}