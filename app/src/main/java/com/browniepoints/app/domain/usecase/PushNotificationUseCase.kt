package com.browniepoints.app.domain.usecase

import android.util.Log
import com.browniepoints.app.data.model.Transaction
import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.repository.NotificationRepository
import javax.inject.Inject

/**
 * Use case for sending push notifications
 * Note: In a real implementation, this would be handled by a backend service
 * This is included for completeness and testing purposes
 */
class PushNotificationUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository
) {
    
    companion object {
        private const val TAG = "PushNotificationUseCase"
    }

    /**
     * Sends a push notification for points received
     * Note: This would typically be called from a backend service
     */
    suspend fun sendPointsReceivedNotification(
        transaction: Transaction,
        senderUser: User
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Sending points received push notification")
            
            val title = "Brownie Points Received!"
            val message = if (!transaction.message.isNullOrBlank()) {
                "${senderUser.displayName} gave you ${transaction.points} point${if (transaction.points > 1) "s" else ""}: \"${transaction.message}\""
            } else {
                "${senderUser.displayName} gave you ${transaction.points} brownie point${if (transaction.points > 1) "s" else ""}!"
            }
            
            val data = mapOf(
                "type" to "points_received",
                "senderId" to senderUser.uid,
                "senderName" to senderUser.displayName,
                "points" to transaction.points.toString(),
                "message" to (transaction.message ?: ""),
                "transactionId" to transaction.id
            )
            
            notificationRepository.sendNotification(
                receiverId = transaction.receiverId,
                title = title,
                message = message,
                data = data
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send points received push notification", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a push notification for connection request
     */
    suspend fun sendConnectionRequestNotification(
        receiverId: String,
        senderUser: User
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Sending connection request push notification")
            
            val title = "Connection Request"
            val message = "${senderUser.displayName} wants to connect with you"
            
            val data = mapOf(
                "type" to "connection_request",
                "senderId" to senderUser.uid,
                "senderName" to senderUser.displayName
            )
            
            notificationRepository.sendNotification(
                receiverId = receiverId,
                title = title,
                message = message,
                data = data
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send connection request push notification", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a push notification for connection accepted
     */
    suspend fun sendConnectionAcceptedNotification(
        receiverId: String,
        senderUser: User
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Sending connection accepted push notification")
            
            val title = "Connection Accepted"
            val message = "${senderUser.displayName} accepted your connection request"
            
            val data = mapOf(
                "type" to "connection_accepted",
                "senderId" to senderUser.uid,
                "senderName" to senderUser.displayName
            )
            
            notificationRepository.sendNotification(
                receiverId = receiverId,
                title = title,
                message = message,
                data = data
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send connection accepted push notification", e)
            Result.failure(e)
        }
    }
}