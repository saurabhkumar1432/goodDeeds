package com.browniepoints.app.data.service

import android.util.Log
import com.browniepoints.app.data.model.Notification
import com.browniepoints.app.data.model.NotificationType
import com.browniepoints.app.data.model.Timeout
import com.browniepoints.app.data.model.Transaction
import com.browniepoints.app.data.repository.InAppNotificationRepository
import com.browniepoints.app.data.repository.UserRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that integrates all notification systems and handles notification lifecycle
 */
@Singleton
class NotificationIntegrationService @Inject constructor(
    private val inAppNotificationRepository: InAppNotificationRepository,
    private val inAppNotificationManager: InAppNotificationManager,
    private val transactionNotificationService: TransactionNotificationService,
    private val timeoutNotificationService: TimeoutNotificationService,
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth
) {
    
    companion object {
        private const val TAG = "NotificationIntegrationService"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Handles a new transaction and sends all appropriate notifications
     */
    fun handleTransactionNotification(transaction: Transaction) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Handling transaction notification for transaction: ${transaction.id}")
                
                // Send transaction notification (both in-app and push)
                val result = transactionNotificationService.sendTransactionNotification(transaction)
                
                if (result.isSuccess) {
                    Log.d(TAG, "Transaction notification sent successfully")
                    
                    // If this is for the current user, show overlay notification
                    val currentUser = firebaseAuth.currentUser
                    if (currentUser?.uid == transaction.receiverId) {
                        showTransactionOverlayNotification(transaction)
                    }
                } else {
                    Log.e(TAG, "Failed to send transaction notification", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling transaction notification", e)
            }
        }
    }
    
    /**
     * Handles timeout notifications
     */
    fun handleTimeoutNotification(timeout: Timeout, isExpiration: Boolean = false) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Handling timeout notification for timeout: ${timeout.id}, isExpiration: $isExpiration")
                
                val result = if (isExpiration) {
                    timeoutNotificationService.sendTimeoutExpirationNotification(timeout)
                } else {
                    timeoutNotificationService.sendTimeoutRequestNotification(timeout)
                }
                
                if (result.isSuccess) {
                    Log.d(TAG, "Timeout notification sent successfully")
                    
                    // Show overlay notification for current user
                    val currentUser = firebaseAuth.currentUser
                    if (currentUser != null) {
                        showTimeoutOverlayNotification(timeout, currentUser.uid, isExpiration)
                    }
                } else {
                    Log.e(TAG, "Failed to send timeout notification", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling timeout notification", e)
            }
        }
    }
    
    /**
     * Handles connection notifications
     */
    fun handleConnectionNotification(senderId: String, receiverId: String, isAccepted: Boolean) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Handling connection notification: sender=$senderId, receiver=$receiverId, accepted=$isAccepted")
                
                // Get sender user info
                val senderResult = userRepository.getUser(senderId)
                if (senderResult.isFailure) {
                    Log.e(TAG, "Failed to get sender user info", senderResult.exceptionOrNull())
                    return@launch
                }
                
                val sender = senderResult.getOrNull()
                if (sender == null) {
                    Log.e(TAG, "Sender user not found: $senderId")
                    return@launch
                }
                
                // Create and send notification
                val notificationType = if (isAccepted) NotificationType.CONNECTION_ACCEPTED else NotificationType.CONNECTION_REQUEST
                val title = if (isAccepted) "Connection Accepted" else "Connection Request"
                val message = if (isAccepted) {
                    "${sender.displayName} accepted your connection request"
                } else {
                    "${sender.displayName} wants to connect with you"
                }
                
                val notification = Notification(
                    receiverId = receiverId,
                    senderId = senderId,
                    senderName = sender.displayName,
                    senderPhotoUrl = sender.photoUrl,
                    type = notificationType,
                    title = title,
                    message = message,
                    createdAt = Timestamp.now()
                )
                
                // Send in-app notification
                val inAppResult = inAppNotificationRepository.createNotification(notification)
                if (inAppResult.isSuccess) {
                    Log.d(TAG, "Connection in-app notification created successfully")
                    
                    // Show overlay if for current user
                    val currentUser = firebaseAuth.currentUser
                    if (currentUser?.uid == receiverId) {
                        inAppNotificationManager.showOverlayNotification(notification)
                    }
                } else {
                    Log.e(TAG, "Failed to create connection in-app notification", inAppResult.exceptionOrNull())
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling connection notification", e)
            }
        }
    }
    
    /**
     * Shows overlay notification for transaction
     */
    private suspend fun showTransactionOverlayNotification(transaction: Transaction) {
        try {
            // Get sender info
            val senderResult = userRepository.getUser(transaction.senderId)
            if (senderResult.isFailure) {
                Log.e(TAG, "Failed to get sender info for overlay notification")
                return
            }
            
            val sender = senderResult.getOrNull() ?: return
            
            // Create notification for overlay
            val notificationType = when (transaction.type) {
                com.browniepoints.app.data.model.TransactionType.GIVE -> NotificationType.POINTS_RECEIVED
                com.browniepoints.app.data.model.TransactionType.DEDUCT -> NotificationType.POINTS_DEDUCTED
            }
            
            val title = when (transaction.type) {
                com.browniepoints.app.data.model.TransactionType.GIVE -> "Brownie Points Received!"
                com.browniepoints.app.data.model.TransactionType.DEDUCT -> "Brownie Points Deducted"
            }
            
            val notification = Notification(
                receiverId = transaction.receiverId,
                senderId = transaction.senderId,
                senderName = sender.displayName,
                senderPhotoUrl = sender.photoUrl,
                type = notificationType,
                title = title,
                message = createTransactionMessage(transaction, sender.displayName),
                points = transaction.points,
                transactionMessage = transaction.message,
                createdAt = transaction.timestamp
            )
            
            inAppNotificationManager.showOverlayNotification(notification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing transaction overlay notification", e)
        }
    }
    
    /**
     * Shows overlay notification for timeout
     */
    private suspend fun showTimeoutOverlayNotification(timeout: Timeout, currentUserId: String, isExpiration: Boolean) {
        try {
            val notification = if (isExpiration) {
                Notification(
                    receiverId = currentUserId,
                    senderId = "system",
                    senderName = "System",
                    type = NotificationType.TIMEOUT_EXPIRED,
                    title = "Timeout Expired",
                    message = "Your timeout has expired. You can now exchange points again.",
                    createdAt = Timestamp.now()
                )
            } else {
                // Get requester info
                val requesterResult = userRepository.getUser(timeout.userId)
                val requester = requesterResult.getOrNull()
                
                if (timeout.userId == currentUserId) {
                    // User requested timeout themselves
                    Notification(
                        receiverId = currentUserId,
                        senderId = timeout.userId,
                        senderName = requester?.displayName ?: "You",
                        type = NotificationType.TIMEOUT_REQUESTED,
                        title = "Timeout Requested",
                        message = "You have requested a 30-minute timeout. Point exchanges are now disabled.",
                        createdAt = Timestamp.now()
                    )
                } else {
                    // Partner requested timeout
                    Notification(
                        receiverId = currentUserId,
                        senderId = timeout.userId,
                        senderName = requester?.displayName ?: "Your partner",
                        type = NotificationType.TIMEOUT_PARTNER_REQUEST,
                        title = "Partner Requested Timeout",
                        message = "${requester?.displayName ?: "Your partner"} has requested a timeout. Point exchanges are now disabled for 30 minutes.",
                        createdAt = Timestamp.now()
                    )
                }
            }
            
            inAppNotificationManager.showOverlayNotification(notification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing timeout overlay notification", e)
        }
    }
    
    /**
     * Creates transaction message for notifications
     */
    private fun createTransactionMessage(transaction: Transaction, senderName: String): String {
        val absolutePoints = kotlin.math.abs(transaction.points)
        val pointsText = "$absolutePoints brownie point${if (absolutePoints > 1) "s" else ""}"
        
        return when (transaction.type) {
            com.browniepoints.app.data.model.TransactionType.GIVE -> {
                val baseMessage = "$senderName gave you $pointsText"
                if (!transaction.message.isNullOrBlank()) {
                    "$baseMessage: \"${transaction.message}\""
                } else {
                    "$baseMessage!"
                }
            }
            com.browniepoints.app.data.model.TransactionType.DEDUCT -> {
                val baseMessage = "$senderName deducted $pointsText from you"
                if (!transaction.message.isNullOrBlank()) {
                    "$baseMessage. Reason: \"${transaction.message}\""
                } else {
                    "$baseMessage."
                }
            }
        }
    }
    
    /**
     * Processes FCM notification data and shows appropriate overlay
     */
    fun processFcmNotification(data: Map<String, String>) {
        serviceScope.launch {
            try {
                val type = data["type"] ?: return@launch
                val senderId = data["senderId"] ?: return@launch
                val senderName = data["senderName"] ?: return@launch
                
                val currentUser = firebaseAuth.currentUser ?: return@launch
                
                Log.d(TAG, "Processing FCM notification: type=$type, sender=$senderName")
                
                val notification = when (type.lowercase()) {
                    "points_received" -> {
                        val points = data["points"]?.toIntOrNull() ?: 0
                        val message = data["message"] ?: ""
                        
                        Notification(
                            receiverId = currentUser.uid,
                            senderId = senderId,
                            senderName = senderName,
                            type = NotificationType.POINTS_RECEIVED,
                            title = "Brownie Points Received!",
                            message = if (message.isNotBlank()) {
                                "$senderName gave you $points brownie point${if (points != 1) "s" else ""}: \"$message\""
                            } else {
                                "$senderName gave you $points brownie point${if (points != 1) "s" else ""}!"
                            },
                            points = points,
                            transactionMessage = message.takeIf { it.isNotBlank() },
                            createdAt = Timestamp.now()
                        )
                    }
                    
                    "points_deducted" -> {
                        val points = data["points"]?.toIntOrNull() ?: 0
                        val message = data["message"] ?: ""
                        val absolutePoints = kotlin.math.abs(points)
                        
                        Notification(
                            receiverId = currentUser.uid,
                            senderId = senderId,
                            senderName = senderName,
                            type = NotificationType.POINTS_DEDUCTED,
                            title = "Brownie Points Deducted",
                            message = if (message.isNotBlank()) {
                                "$senderName deducted $absolutePoints brownie point${if (absolutePoints != 1) "s" else ""} from you. Reason: \"$message\""
                            } else {
                                "$senderName deducted $absolutePoints brownie point${if (absolutePoints != 1) "s" else ""} from you."
                            },
                            points = points,
                            transactionMessage = message.takeIf { it.isNotBlank() },
                            createdAt = Timestamp.now()
                        )
                    }
                    
                    "timeout_requested" -> {
                        Notification(
                            receiverId = currentUser.uid,
                            senderId = senderId,
                            senderName = senderName,
                            type = NotificationType.TIMEOUT_REQUESTED,
                            title = "Timeout Requested",
                            message = "You have requested a 30-minute timeout. Point exchanges are now disabled.",
                            createdAt = Timestamp.now()
                        )
                    }
                    
                    "timeout_partner_request" -> {
                        Notification(
                            receiverId = currentUser.uid,
                            senderId = senderId,
                            senderName = senderName,
                            type = NotificationType.TIMEOUT_PARTNER_REQUEST,
                            title = "Partner Requested Timeout",
                            message = "$senderName has requested a timeout. Point exchanges are now disabled for 30 minutes.",
                            createdAt = Timestamp.now()
                        )
                    }
                    
                    "timeout_expired" -> {
                        Notification(
                            receiverId = currentUser.uid,
                            senderId = "system",
                            senderName = "System",
                            type = NotificationType.TIMEOUT_EXPIRED,
                            title = "Timeout Expired",
                            message = "Your timeout has expired. You can now exchange points again.",
                            createdAt = Timestamp.now()
                        )
                    }
                    
                    else -> null
                }
                
                notification?.let { notif ->
                    inAppNotificationManager.showOverlayNotification(notif)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing FCM notification", e)
            }
        }
    }
}