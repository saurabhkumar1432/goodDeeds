package com.browniepoints.app.data.service

import android.util.Log
import com.browniepoints.app.data.model.Notification
import com.browniepoints.app.data.model.NotificationType
import com.browniepoints.app.data.model.Timeout
import com.browniepoints.app.data.repository.ConnectionRepository
import com.browniepoints.app.data.repository.InAppNotificationRepository
import com.browniepoints.app.data.repository.NotificationRepository
import com.browniepoints.app.data.repository.UserRepository
import com.google.firebase.Timestamp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for sending timeout-related notifications
 */
@Singleton
class TimeoutNotificationService @Inject constructor(
    private val inAppNotificationRepository: InAppNotificationRepository,
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val connectionRepository: ConnectionRepository
) {
    
    companion object {
        private const val TAG = "TimeoutNotificationService"
    }
    
    /**
     * Sends notifications when a timeout is requested
     */
    suspend fun sendTimeoutRequestNotification(timeout: Timeout): Result<Unit> {
        return try {
            Log.d(TAG, "Sending timeout request notification for timeout: ${timeout.id}")
            
            // Get the user who requested the timeout
            val requesterResult = userRepository.getUser(timeout.userId)
            if (requesterResult.isFailure) {
                Log.e(TAG, "Failed to get requester information", requesterResult.exceptionOrNull())
                return Result.failure(requesterResult.exceptionOrNull() ?: Exception("Failed to get requester information"))
            }
            
            val requester = requesterResult.getOrNull()
            if (requester == null) {
                Log.e(TAG, "Requester not found: ${timeout.userId}")
                return Result.failure(Exception("Requester not found"))
            }
            
            // Get the partner's user ID from the connection
            val connectionResult = connectionRepository.getConnectionById(timeout.connectionId)
            if (connectionResult.isFailure) {
                Log.e(TAG, "Failed to get connection information", connectionResult.exceptionOrNull())
                return Result.failure(connectionResult.exceptionOrNull() ?: Exception("Failed to get connection information"))
            }
            
            val connection = connectionResult.getOrNull()
            if (connection == null) {
                Log.e(TAG, "Connection not found: ${timeout.connectionId}")
                return Result.failure(Exception("Connection not found"))
            }
            
            // Determine the partner's user ID
            val partnerId = if (connection.user1Id == timeout.userId) {
                connection.user2Id
            } else {
                connection.user1Id
            }
            
            // Send notification to the requester (confirmation)
            sendTimeoutConfirmationNotification(timeout, requester.displayName)
            
            // Send notification to the partner
            sendTimeoutPartnerNotification(timeout, requester.displayName, partnerId)
            
            Log.d(TAG, "Timeout request notifications sent successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending timeout request notification", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sends notifications when a timeout expires
     */
    suspend fun sendTimeoutExpirationNotification(timeout: Timeout): Result<Unit> {
        return try {
            Log.d(TAG, "Sending timeout expiration notification for timeout: ${timeout.id}")
            
            // Get the connection to find both users
            val connectionResult = connectionRepository.getConnectionById(timeout.connectionId)
            if (connectionResult.isFailure) {
                Log.e(TAG, "Failed to get connection information", connectionResult.exceptionOrNull())
                return Result.failure(connectionResult.exceptionOrNull() ?: Exception("Failed to get connection information"))
            }
            
            val connection = connectionResult.getOrNull()
            if (connection == null) {
                Log.e(TAG, "Connection not found: ${timeout.connectionId}")
                return Result.failure(Exception("Connection not found"))
            }
            
            // Send expiration notification to both users
            sendTimeoutExpiredNotification(connection.user1Id)
            sendTimeoutExpiredNotification(connection.user2Id)
            
            Log.d(TAG, "Timeout expiration notifications sent successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending timeout expiration notification", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sends confirmation notification to the user who requested the timeout
     */
    private suspend fun sendTimeoutConfirmationNotification(timeout: Timeout, requesterName: String) {
        try {
            val notification = Notification(
                receiverId = timeout.userId,
                senderId = timeout.userId, // Self-notification
                senderName = requesterName,
                senderPhotoUrl = null,
                type = NotificationType.TIMEOUT_REQUESTED,
                title = "Timeout Requested",
                message = "You have requested a 30-minute timeout. Point exchanges are now disabled.",
                points = 0,
                transactionMessage = null,
                isRead = false,
                createdAt = Timestamp.now()
            )
            
            // Send in-app notification
            inAppNotificationRepository.createNotification(notification)
            
            // Send push notification
            notificationRepository.sendNotification(
                receiverId = timeout.userId,
                title = "Timeout Requested",
                message = "You have requested a 30-minute timeout. Point exchanges are now disabled.",
                data = mapOf(
                    "type" to "timeout_requested",
                    "timeoutId" to timeout.id,
                    "connectionId" to timeout.connectionId,
                    "senderName" to requesterName,
                    "senderId" to timeout.userId
                )
            )
            
            Log.d(TAG, "Timeout confirmation notification sent to requester: ${timeout.userId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending timeout confirmation notification", e)
        }
    }
    
    /**
     * Sends notification to the partner when a timeout is requested
     */
    private suspend fun sendTimeoutPartnerNotification(timeout: Timeout, requesterName: String, partnerId: String) {
        try {
            val notification = Notification(
                receiverId = partnerId,
                senderId = timeout.userId,
                senderName = requesterName,
                senderPhotoUrl = null,
                type = NotificationType.TIMEOUT_PARTNER_REQUEST,
                title = "Partner Requested Timeout",
                message = "$requesterName has requested a timeout. Point exchanges are now disabled for 30 minutes.",
                points = 0,
                transactionMessage = null,
                isRead = false,
                createdAt = Timestamp.now()
            )
            
            // Send in-app notification
            inAppNotificationRepository.createNotification(notification)
            
            // Send push notification
            notificationRepository.sendNotification(
                receiverId = partnerId,
                title = "Partner Requested Timeout",
                message = "$requesterName has requested a timeout. Point exchanges are now disabled for 30 minutes.",
                data = mapOf(
                    "type" to "timeout_partner_request",
                    "timeoutId" to timeout.id,
                    "connectionId" to timeout.connectionId,
                    "requesterId" to timeout.userId,
                    "senderName" to requesterName,
                    "senderId" to timeout.userId
                )
            )
            
            Log.d(TAG, "Timeout partner notification sent to: $partnerId")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending timeout partner notification", e)
        }
    }
    
    /**
     * Sends expiration notification to a user
     */
    private suspend fun sendTimeoutExpiredNotification(userId: String) {
        try {
            val notification = Notification(
                receiverId = userId,
                senderId = userId, // System notification
                senderName = "System",
                senderPhotoUrl = null,
                type = NotificationType.TIMEOUT_EXPIRED,
                title = "Timeout Expired",
                message = "Your timeout has expired. You can now exchange points again.",
                points = 0,
                transactionMessage = null,
                isRead = false,
                createdAt = Timestamp.now()
            )
            
            // Send in-app notification
            inAppNotificationRepository.createNotification(notification)
            
            // Send push notification
            notificationRepository.sendNotification(
                receiverId = userId,
                title = "Timeout Expired",
                message = "Your timeout has expired. You can now exchange points again.",
                data = mapOf(
                    "type" to "timeout_expired",
                    "senderName" to "System",
                    "senderId" to "system"
                )
            )
            
            Log.d(TAG, "Timeout expiration notification sent to: $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending timeout expiration notification", e)
        }
    }
}