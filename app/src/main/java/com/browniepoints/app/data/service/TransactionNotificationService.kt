package com.browniepoints.app.data.service

import android.util.Log
import com.browniepoints.app.data.model.Notification
import com.browniepoints.app.data.model.NotificationType
import com.browniepoints.app.data.model.Transaction
import com.browniepoints.app.data.model.TransactionType
import com.browniepoints.app.data.repository.InAppNotificationRepository
import com.browniepoints.app.data.repository.NotificationRepository
import com.browniepoints.app.data.repository.UserRepository
import com.google.firebase.Timestamp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for sending notifications when transactions occur
 */
@Singleton
class TransactionNotificationService @Inject constructor(
    private val inAppNotificationRepository: InAppNotificationRepository,
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository
) {
    
    companion object {
        private const val TAG = "TransactionNotificationService"
    }
    
    /**
     * Sends notifications for a transaction (both in-app and push notifications)
     */
    suspend fun sendTransactionNotification(transaction: Transaction): Result<Unit> {
        return try {
            Log.d(TAG, "Sending notification for transaction: ${transaction.id}")
            
            // Get sender information
            val senderResult = userRepository.getUser(transaction.senderId)
            if (senderResult.isFailure) {
                Log.e(TAG, "Failed to get sender information", senderResult.exceptionOrNull())
                return Result.failure(senderResult.exceptionOrNull() ?: Exception("Failed to get sender information"))
            }
            
            val sender = senderResult.getOrNull()
            if (sender == null) {
                Log.e(TAG, "Sender not found: ${transaction.senderId}")
                return Result.failure(Exception("Sender not found"))
            }
            
            // Create in-app notification
            val notificationType = when (transaction.type) {
                TransactionType.GIVE -> NotificationType.POINTS_RECEIVED
                TransactionType.DEDUCT -> NotificationType.POINTS_DEDUCTED
            }
            
            val notification = Notification(
                receiverId = transaction.receiverId,
                senderId = transaction.senderId,
                senderName = sender.displayName,
                senderPhotoUrl = sender.photoUrl,
                type = notificationType,
                title = createNotificationTitle(transaction, sender.displayName),
                message = createNotificationMessage(transaction, sender.displayName),
                points = transaction.points,
                transactionMessage = transaction.message,
                isRead = false,
                createdAt = Timestamp.now()
            )
            
            // Send in-app notification
            val inAppResult = inAppNotificationRepository.createNotification(notification)
            if (inAppResult.isFailure) {
                Log.e(TAG, "Failed to create in-app notification", inAppResult.exceptionOrNull())
                return inAppResult
            }
            
            // Send push notification
            val pushTitle = createNotificationTitle(transaction, sender.displayName)
            val pushMessage = createNotificationMessage(transaction, sender.displayName)
            val pushData = mapOf(
                "type" to when (transaction.type) {
                    TransactionType.GIVE -> "points_received"
                    TransactionType.DEDUCT -> "points_deducted"
                },
                "transactionId" to transaction.id,
                "senderId" to transaction.senderId,
                "senderName" to sender.displayName,
                "points" to transaction.points.toString(),
                "message" to (transaction.message ?: "")
            )
            
            val pushResult = notificationRepository.sendNotification(
                receiverId = transaction.receiverId,
                title = pushTitle,
                message = pushMessage,
                data = pushData
            )
            
            if (pushResult.isFailure) {
                Log.w(TAG, "Failed to send push notification, but in-app notification was successful", pushResult.exceptionOrNull())
                // Don't fail the entire operation if push notification fails
            }
            
            Log.d(TAG, "Transaction notification sent successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending transaction notification", e)
            Result.failure(e)
        }
    }
    
    /**
     * Creates a notification title based on the transaction type
     */
    private fun createNotificationTitle(transaction: Transaction, senderName: String): String {
        return when (transaction.type) {
            TransactionType.GIVE -> "Brownie Points Received!"
            TransactionType.DEDUCT -> "Brownie Points Deducted"
        }
    }
    
    /**
     * Creates a notification message based on the transaction type
     */
    private fun createNotificationMessage(transaction: Transaction, senderName: String): String {
        val absolutePoints = kotlin.math.abs(transaction.points)
        val pointsText = "$absolutePoints brownie point${if (absolutePoints > 1) "s" else ""}"
        
        return when (transaction.type) {
            TransactionType.GIVE -> {
                val baseMessage = "$senderName gave you $pointsText"
                if (!transaction.message.isNullOrBlank()) {
                    "$baseMessage: \"${transaction.message}\""
                } else {
                    "$baseMessage!"
                }
            }
            TransactionType.DEDUCT -> {
                val baseMessage = "$senderName deducted $pointsText from you"
                if (!transaction.message.isNullOrBlank()) {
                    "$baseMessage. Reason: \"${transaction.message}\""
                } else {
                    "$baseMessage."
                }
            }
        }
    }
}