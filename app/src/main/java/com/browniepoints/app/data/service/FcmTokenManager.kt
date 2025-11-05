package com.browniepoints.app.data.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.browniepoints.app.data.repository.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for managing FCM token registration and updates
 */
@Singleton
class FcmTokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseMessaging: FirebaseMessaging,
    private val notificationRepository: NotificationRepository
) {
    
    companion object {
        private const val TAG = "FcmTokenManager"
        private const val PREFS_NAME = "brownie_points_prefs"
        private const val KEY_PENDING_FCM_TOKEN = "pending_fcm_token"
        private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"
        private const val KEY_LAST_UPDATED_USER = "last_updated_user"
        private const val TOKEN_EXPIRY_HOURS = 24 // Consider token stale after 24 hours
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Initializes FCM token management for the current user
     * Should be called when user signs in
     */
    fun initializeForUser() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user, cannot initialize FCM token")
            return
        }
        
        serviceScope.launch {
            try {
                // Check if we have a pending token to update
                val pendingToken = getPendingToken()
                if (pendingToken != null) {
                    Log.d(TAG, "Found pending FCM token, updating for user: ${currentUser.uid}")
                    updateTokenForUser(currentUser.uid, pendingToken)
                    clearPendingToken()
                } else {
                    // Get current token and update if needed
                    refreshTokenForCurrentUser()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing FCM token for user", e)
            }
        }
    }
    
    /**
     * Refreshes FCM token for the current user
     */
    fun refreshTokenForCurrentUser() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user, cannot refresh FCM token")
            return
        }
        
        serviceScope.launch {
            try {
                val token = firebaseMessaging.token.await()
                Log.d(TAG, "Retrieved current FCM token for refresh")
                
                // Check if we need to update the token
                if (shouldUpdateToken(currentUser.uid, token)) {
                    updateTokenForUser(currentUser.uid, token)
                } else {
                    Log.d(TAG, "FCM token is up to date for user: ${currentUser.uid}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing FCM token", e)
            }
        }
    }
    
    /**
     * Updates FCM token for a specific user with retry logic
     */
    suspend fun updateTokenForUser(userId: String, token: String, attempt: Int = 1): Boolean {
        return try {
            Log.d(TAG, "Updating FCM token for user: $userId (attempt $attempt)")
            
            val result = notificationRepository.updateFcmToken(userId, token)
            if (result.isSuccess) {
                Log.d(TAG, "FCM token updated successfully for user: $userId")
                markTokenAsUpdated(userId, token)
                true
            } else {
                Log.e(TAG, "Failed to update FCM token for user: $userId", result.exceptionOrNull())
                
                // Retry with exponential backoff (max 3 attempts)
                if (attempt < 3) {
                    val delayMs = attempt * 2000L // 2s, 4s, 6s
                    Log.d(TAG, "Retrying FCM token update in ${delayMs}ms")
                    kotlinx.coroutines.delay(delayMs)
                    updateTokenForUser(userId, token, attempt + 1)
                } else {
                    Log.e(TAG, "Failed to update FCM token after 3 attempts")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while updating FCM token for user: $userId", e)
            
            // Retry with exponential backoff (max 3 attempts)
            if (attempt < 3) {
                val delayMs = attempt * 2000L
                Log.d(TAG, "Retrying FCM token update after exception in ${delayMs}ms")
                kotlinx.coroutines.delay(delayMs)
                updateTokenForUser(userId, token, attempt + 1)
            } else {
                Log.e(TAG, "Failed to update FCM token after 3 attempts due to exceptions")
                false
            }
        }
    }
    
    /**
     * Stores FCM token for later update when user signs in
     */
    fun storePendingToken(token: String) {
        try {
            sharedPrefs.edit()
                .putString(KEY_PENDING_FCM_TOKEN, token)
                .putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "FCM token stored as pending for later update")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store pending FCM token", e)
        }
    }
    
    /**
     * Gets pending FCM token if available and not expired
     */
    private fun getPendingToken(): String? {
        return try {
            val token = sharedPrefs.getString(KEY_PENDING_FCM_TOKEN, null)
            val timestamp = sharedPrefs.getLong(KEY_TOKEN_TIMESTAMP, 0)
            
            if (token != null && timestamp > 0) {
                val ageHours = (System.currentTimeMillis() - timestamp) / (1000 * 60 * 60)
                if (ageHours < TOKEN_EXPIRY_HOURS) {
                    Log.d(TAG, "Retrieved pending FCM token (age: ${ageHours}h)")
                    token
                } else {
                    Log.d(TAG, "Pending FCM token is expired (age: ${ageHours}h), discarding")
                    clearPendingToken()
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving pending FCM token", e)
            null
        }
    }
    
    /**
     * Clears pending FCM token
     */
    private fun clearPendingToken() {
        try {
            sharedPrefs.edit()
                .remove(KEY_PENDING_FCM_TOKEN)
                .remove(KEY_TOKEN_TIMESTAMP)
                .apply()
            Log.d(TAG, "Cleared pending FCM token")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing pending FCM token", e)
        }
    }
    
    /**
     * Marks token as updated for a user
     */
    private fun markTokenAsUpdated(userId: String, token: String) {
        try {
            sharedPrefs.edit()
                .putString(KEY_LAST_UPDATED_USER, userId)
                .putString("last_token_$userId", token)
                .putLong("last_update_$userId", System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Marked FCM token as updated for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking token as updated", e)
        }
    }
    
    /**
     * Checks if token should be updated for a user
     */
    private fun shouldUpdateToken(userId: String, currentToken: String): Boolean {
        return try {
            val lastToken = sharedPrefs.getString("last_token_$userId", null)
            val lastUpdate = sharedPrefs.getLong("last_update_$userId", 0)
            
            // Update if token is different or if it's been more than 24 hours
            val tokenChanged = lastToken != currentToken
            val ageHours = (System.currentTimeMillis() - lastUpdate) / (1000 * 60 * 60)
            val isStale = ageHours >= TOKEN_EXPIRY_HOURS
            
            val shouldUpdate = tokenChanged || isStale
            Log.d(TAG, "Token update check for user $userId: changed=$tokenChanged, stale=$isStale (${ageHours}h), shouldUpdate=$shouldUpdate")
            
            shouldUpdate
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if token should be updated", e)
            true // Default to updating on error
        }
    }
    
    /**
     * Cleans up token data when user signs out
     */
    fun cleanupForUser(userId: String) {
        try {
            sharedPrefs.edit()
                .remove("last_token_$userId")
                .remove("last_update_$userId")
                .apply()
            Log.d(TAG, "Cleaned up FCM token data for user: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up token data for user: $userId", e)
        }
    }
    
    /**
     * Gets the current FCM token
     */
    suspend fun getCurrentToken(): Result<String> {
        return try {
            val token = firebaseMessaging.token.await()
            Log.d(TAG, "Retrieved current FCM token")
            Result.success(token)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current FCM token", e)
            Result.failure(e)
        }
    }
}