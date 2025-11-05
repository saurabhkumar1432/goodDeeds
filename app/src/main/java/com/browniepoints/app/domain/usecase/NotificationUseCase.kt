package com.browniepoints.app.domain.usecase

import android.util.Log
import com.browniepoints.app.data.repository.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject

class NotificationUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val firebaseAuth: FirebaseAuth
) {
    
    companion object {
        private const val TAG = "NotificationUseCase"
    }

    /**
     * Initializes FCM token for the current user
     * Should be called after successful authentication
     */
    suspend fun initializeFcmToken(): Result<Unit> {
        return try {
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "No authenticated user found")
                return Result.failure(Exception("User not authenticated"))
            }

            // Get current FCM token
            val tokenResult = notificationRepository.getCurrentFcmToken()
            
            if (tokenResult.isSuccess) {
                val token = tokenResult.getOrNull()!!
                // Update the token in Firestore
                val updateResult = notificationRepository.updateFcmToken(currentUser.uid, token)
                
                if (updateResult.isSuccess) {
                    Log.d(TAG, "FCM token initialized successfully for user: ${currentUser.uid}")
                    Result.success(Unit)
                } else {
                    Log.e(TAG, "Failed to update FCM token in Firestore", updateResult.exceptionOrNull())
                    Result.failure(updateResult.exceptionOrNull() ?: Exception("Failed to update FCM token"))
                }
            } else {
                Log.e(TAG, "Failed to get current FCM token", tokenResult.exceptionOrNull())
                Result.failure(tokenResult.exceptionOrNull() ?: Exception("Failed to get FCM token"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FCM token", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a notification to a specific user
     */
    suspend fun sendNotificationToUser(
        receiverId: String,
        title: String,
        message: String,
        data: Map<String, String> = emptyMap()
    ): Result<Unit> {
        return notificationRepository.sendNotification(receiverId, title, message, data)
    }

    /**
     * Updates FCM token for the current user
     */
    suspend fun updateCurrentUserFcmToken(token: String): Result<Unit> {
        val currentUser = firebaseAuth.currentUser
        return if (currentUser != null) {
            notificationRepository.updateFcmToken(currentUser.uid, token)
        } else {
            Result.failure(Exception("User not authenticated"))
        }
    }
}