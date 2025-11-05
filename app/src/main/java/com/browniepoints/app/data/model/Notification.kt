package com.browniepoints.app.data.model

import com.browniepoints.app.data.validation.ValidationResult
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * Data class representing an in-app notification
 */
data class Notification(
    @DocumentId
    val id: String = "",
    val receiverId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String? = null,
    val type: NotificationType = NotificationType.POINTS_RECEIVED,
    val title: String = "",
    val message: String = "",
    val points: Int = 0,
    val transactionMessage: String? = null,
    val isRead: Boolean = false,
    val createdAt: Timestamp = Timestamp.now()
) {
    /**
     * Validates the Notification data model
     */
    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (receiverId.isBlank()) {
            errors.add("Receiver ID cannot be empty")
        }
        
        if (senderId.isBlank()) {
            errors.add("Sender ID cannot be empty")
        }
        
        if (senderName.isBlank()) {
            errors.add("Sender name cannot be empty")
        }
        
        if (title.isBlank()) {
            errors.add("Title cannot be empty")
        }
        
        if (message.isBlank()) {
            errors.add("Message cannot be empty")
        }
        
        when (type) {
            NotificationType.POINTS_RECEIVED -> {
                if (points <= 0) {
                    errors.add("Points must be greater than 0 for points received notifications")
                }
            }
            NotificationType.POINTS_DEDUCTED -> {
                if (points >= 0) {
                    errors.add("Points must be negative for points deducted notifications")
                }
            }
            else -> {
                // No point validation needed for other notification types
            }
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    /**
     * Creates a formatted display message for the notification
     */
    fun getDisplayMessage(): String {
        return when (type) {
            NotificationType.POINTS_RECEIVED -> {
                val baseMessage = "$senderName gave you $points brownie point${if (points > 1) "s" else ""}"
                if (!transactionMessage.isNullOrBlank()) {
                    "$baseMessage: \"$transactionMessage\""
                } else {
                    baseMessage
                }
            }
            NotificationType.POINTS_DEDUCTED -> {
                val absolutePoints = kotlin.math.abs(points)
                val baseMessage = "$senderName deducted $absolutePoints brownie point${if (absolutePoints > 1) "s" else ""} from you"
                if (!transactionMessage.isNullOrBlank()) {
                    "$baseMessage: \"$transactionMessage\""
                } else {
                    baseMessage
                }
            }
            NotificationType.CONNECTION_REQUEST -> message
            NotificationType.CONNECTION_ACCEPTED -> message
            NotificationType.TIMEOUT_REQUESTED -> "$senderName has requested a 30-minute timeout"
            NotificationType.TIMEOUT_EXPIRED -> "Your timeout has expired. You can now exchange points again."
            NotificationType.TIMEOUT_PARTNER_REQUEST -> "$senderName has requested a timeout. Point exchanges are now disabled for 30 minutes."
        }
    }
}

/**
 * Enum representing different types of notifications
 */
enum class NotificationType {
    POINTS_RECEIVED,
    POINTS_DEDUCTED,
    CONNECTION_REQUEST,
    CONNECTION_ACCEPTED,
    TIMEOUT_REQUESTED,
    TIMEOUT_EXPIRED,
    TIMEOUT_PARTNER_REQUEST
}