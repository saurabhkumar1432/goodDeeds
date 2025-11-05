package com.browniepoints.app.domain.usecase

import android.util.Log
import com.browniepoints.app.data.model.Notification
import com.browniepoints.app.data.model.NotificationType
import com.browniepoints.app.data.model.Transaction
import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.repository.InAppNotificationRepository
import com.browniepoints.app.data.repository.UserRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class InAppNotificationUseCase @Inject constructor(
    private val inAppNotificationRepository: InAppNotificationRepository,
    private val userRepository: UserRepository
) {
    
    companion object {
        private const val TAG = "InAppNotificationUseCase"
    }

    /**
     * Creates a notification for received points
     */
    suspend fun createPointsReceivedNotification(
        transaction: Transaction,
        senderUser: User
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Creating points received notification for transaction: ${transaction.id}")
            
            val notification = Notification(
                receiverId = transaction.receiverId,
                senderId = transaction.senderId,
                senderName = senderUser.displayName,
                senderPhotoUrl = senderUser.photoUrl,
                type = NotificationType.POINTS_RECEIVED,
                title = "Brownie Points Received!",
                message = "You received ${transaction.points} brownie point${if (transaction.points > 1) "s" else ""}",
                points = transaction.points,
                transactionMessage = transaction.message,
                createdAt = transaction.timestamp
            )
            
            inAppNotificationRepository.createNotification(notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create points received notification", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a notification for connection request
     */
    suspend fun createConnectionRequestNotification(
        receiverId: String,
        senderUser: User
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Creating connection request notification")
            
            val notification = Notification(
                receiverId = receiverId,
                senderId = senderUser.uid,
                senderName = senderUser.displayName,
                senderPhotoUrl = senderUser.photoUrl,
                type = NotificationType.CONNECTION_REQUEST,
                title = "Connection Request",
                message = "${senderUser.displayName} wants to connect with you",
                createdAt = Timestamp.now()
            )
            
            inAppNotificationRepository.createNotification(notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create connection request notification", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a notification for connection accepted
     */
    suspend fun createConnectionAcceptedNotification(
        receiverId: String,
        senderUser: User
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Creating connection accepted notification")
            
            val notification = Notification(
                receiverId = receiverId,
                senderId = senderUser.uid,
                senderName = senderUser.displayName,
                senderPhotoUrl = senderUser.photoUrl,
                type = NotificationType.CONNECTION_ACCEPTED,
                title = "Connection Accepted",
                message = "${senderUser.displayName} accepted your connection request",
                createdAt = Timestamp.now()
            )
            
            inAppNotificationRepository.createNotification(notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create connection accepted notification", e)
            Result.failure(e)
        }
    }

    /**
     * Gets real-time notifications for a user
     */
    fun getNotificationsForUser(userId: String): Flow<List<Notification>> {
        return inAppNotificationRepository.getNotificationsForUser(userId)
    }

    /**
     * Gets unread notifications count for a user
     */
    fun getUnreadNotificationsCount(userId: String): Flow<Int> {
        return inAppNotificationRepository.getUnreadNotificationsCount(userId)
    }

    /**
     * Marks a notification as read
     */
    suspend fun markNotificationAsRead(notificationId: String): Result<Unit> {
        return inAppNotificationRepository.markNotificationAsRead(notificationId)
    }

    /**
     * Marks all notifications as read for a user
     */
    suspend fun markAllNotificationsAsRead(userId: String): Result<Unit> {
        return inAppNotificationRepository.markAllNotificationsAsRead(userId)
    }

    /**
     * Deletes a notification
     */
    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        return inAppNotificationRepository.deleteNotification(notificationId)
    }
}