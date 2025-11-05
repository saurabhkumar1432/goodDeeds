package com.browniepoints.app.data.repository

import com.browniepoints.app.data.model.Timeout
import kotlinx.coroutines.flow.Flow

interface TimeoutRepository {
    /**
     * Creates a new timeout for a user
     * @param userId The user requesting the timeout
     * @param connectionId The connection ID for the couple
     * @return Result containing the created Timeout or error
     */
    suspend fun createTimeout(
        userId: String,
        connectionId: String
    ): Result<Timeout>
    
    /**
     * Checks if a user can request a timeout today
     * @param userId The user ID to check
     * @return true if the user can request a timeout, false otherwise
     */
    suspend fun canRequestTimeout(userId: String): Result<Boolean>
    
    /**
     * Gets the active timeout for a connection (if any)
     * @param connectionId The connection ID
     * @return Result containing the active Timeout or null if none exists
     */
    suspend fun getActiveTimeout(connectionId: String): Result<Timeout?>
    
    /**
     * Observes the active timeout for a connection
     * @param connectionId The connection ID
     * @return Flow of active Timeout or null
     */
    fun observeActiveTimeout(connectionId: String): Flow<Timeout?>
    
    /**
     * Marks a timeout as expired/inactive
     * @param timeoutId The timeout ID to expire
     * @return Result indicating success or failure
     */
    suspend fun expireTimeout(timeoutId: String): Result<Unit>
    
    /**
     * Gets timeout history for a user
     * @param userId The user ID
     * @return Result containing list of user's timeouts
     */
    suspend fun getTimeoutHistory(userId: String): Result<List<Timeout>>
    
    /**
     * Gets today's timeout count for a user
     * @param userId The user ID
     * @return Result containing the count of timeouts requested today
     */
    suspend fun getTodayTimeoutCount(userId: String): Result<Int>
    
    /**
     * Observes timeout status for a connection (for both partners)
     * @param connectionId The connection ID
     * @return Flow indicating if any timeout is active for the connection
     */
    fun observeTimeoutStatus(connectionId: String): Flow<Boolean>
    
    /**
     * Cleans up expired timeouts (marks them as inactive)
     * This should be called periodically to maintain data consistency
     */
    suspend fun cleanupExpiredTimeouts(): Result<Unit>
    
    /**
     * Synchronizes timeout state between partners in a connection
     * Ensures both partners see the same timeout status
     * @param connectionId The connection ID to synchronize
     * @return Result indicating success or failure
     */
    suspend fun synchronizePartnerTimeoutState(connectionId: String): Result<Unit>
}