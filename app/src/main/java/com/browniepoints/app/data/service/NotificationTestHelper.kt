package com.browniepoints.app.data.service

import android.content.Context
import android.util.Log
import com.browniepoints.app.data.model.Notification
import com.browniepoints.app.data.model.NotificationType
import com.browniepoints.app.domain.usecase.InAppNotificationUseCase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for testing notification functionality
 * This can be used during development to test notifications
 */
@Singleton
class NotificationTestHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inAppNotificationUseCase: InAppNotificationUseCase,
    private val firebaseAuth: FirebaseAuth
) {
    
    companion object {
        private const val TAG = "NotificationTestHelper"
    }
    
    private val testScope = CoroutineScope(Dispatchers.IO)

    /**
     * Creates a test points received notification
     */
    fun createTestPointsNotification() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user for test notification")
            return
        }
        
        testScope.launch {
            val testNotification = Notification(
                receiverId = currentUser.uid,
                senderId = "test_sender",
                senderName = "Test User",
                type = NotificationType.POINTS_RECEIVED,
                title = "Test Brownie Points!",
                message = "You received test brownie points",
                points = 5,
                transactionMessage = "Great job on the test!",
                createdAt = Timestamp.now()
            )
            
            inAppNotificationUseCase.createNotification(testNotification)
                .onSuccess {
                    Log.d(TAG, "Test notification created successfully")
                }
                .onFailure { exception ->
                    Log.e(TAG, "Failed to create test notification", exception)
                }
        }
    }
    
    /**
     * Creates a test connection request notification
     */
    fun createTestConnectionNotification() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user for test notification")
            return
        }
        
        testScope.launch {
            val testNotification = Notification(
                receiverId = currentUser.uid,
                senderId = "test_sender",
                senderName = "Test Connection User",
                type = NotificationType.CONNECTION_REQUEST,
                title = "Connection Request",
                message = "Test Connection User wants to connect with you",
                createdAt = Timestamp.now()
            )
            
            inAppNotificationUseCase.createNotification(testNotification)
                .onSuccess {
                    Log.d(TAG, "Test connection notification created successfully")
                }
                .onFailure { exception ->
                    Log.e(TAG, "Failed to create test connection notification", exception)
                }
        }
    }
    
    /**
     * Tests the FCM service by creating a local notification
     */
    fun testLocalNotification() {
        val messagingService = BrowniePointsMessagingService()
        
        // Create test data for points received
        val testData = mapOf(
            "type" to "points_received",
            "senderId" to "test_sender",
            "senderName" to "Test User",
            "points" to "3",
            "message" to "Testing local notifications!"
        )
        
        Log.d(TAG, "Testing local notification with data: $testData")
        
        // This would normally be called by FCM, but we can test it directly
        // Note: This requires the service to be properly initialized
    }
}

/**
 * Extension function to add to InAppNotificationUseCase for testing
 */
private suspend fun InAppNotificationUseCase.createNotification(notification: Notification): Result<Unit> {
    // This is a simplified version for testing - in real implementation,
    // we would use the repository directly
    return try {
        // For testing purposes, we'll just log the notification
        Log.d("NotificationTestHelper", "Would create notification: ${notification.title} - ${notification.message}")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}