package com.browniepoints.app.data.model

import com.browniepoints.app.data.validation.ValidationResult
import com.browniepoints.app.data.validation.ValidationUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * Transaction types for point exchanges between partners
 */
enum class TransactionType {
    GIVE,    // Positive points (giving brownie points)
    DEDUCT   // Negative points (deducting for conflicts)
}

data class Transaction(
    @DocumentId
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val points: Int = 0,
    val message: String? = null,
    val timestamp: Timestamp = Timestamp.now(),
    val connectionId: String = "",
    val type: TransactionType = TransactionType.GIVE
) {
    companion object {
        const val MIN_POINTS = 1
        const val MAX_POINTS = 10
        const val MAX_MESSAGE_LENGTH = 200
    }
    
    /**
     * Validates the Transaction data model for data integrity
     * @return ValidationResult indicating if the transaction data is valid
     */
    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (id.isBlank()) {
            errors.add("Transaction ID cannot be empty")
        }
        
        if (senderId.isBlank()) {
            errors.add("Sender ID cannot be empty")
        }
        
        if (receiverId.isBlank()) {
            errors.add("Receiver ID cannot be empty")
        }
        
        if (senderId == receiverId) {
            errors.add("Sender and receiver cannot be the same user")
        }
        
        // Validate points based on transaction type using ValidationUtils
        val pointsValidation = when (type) {
            TransactionType.GIVE -> {
                if (points >= MIN_POINTS && points <= MAX_POINTS) {
                    ValidationResult.Success
                } else {
                    ValidationResult.Error("Points for giving must be between $MIN_POINTS and $MAX_POINTS")
                }
            }
            TransactionType.DEDUCT -> {
                if (points <= -MIN_POINTS && points >= -MAX_POINTS) {
                    ValidationResult.Success
                } else {
                    ValidationResult.Error("Points for deduction must be between -$MIN_POINTS and -$MAX_POINTS")
                }
            }
        }
        
        if (pointsValidation is ValidationResult.Error) {
            errors.addAll(pointsValidation.errors)
        }
        
        if (!message.isNullOrBlank() && message.length > MAX_MESSAGE_LENGTH) {
            errors.add("Message cannot exceed $MAX_MESSAGE_LENGTH characters")
        }
        
        if (connectionId.isBlank()) {
            errors.add("Connection ID cannot be empty")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    /**
     * Checks if the transaction has a message
     */
    fun hasMessage(): Boolean = !message.isNullOrBlank()
    
    /**
     * Checks if a specific user is the sender of this transaction
     */
    fun isSentBy(userId: String): Boolean = senderId == userId
    
    /**
     * Checks if a specific user is the receiver of this transaction
     */
    fun isReceivedBy(userId: String): Boolean = receiverId == userId
    
    /**
     * Checks if this is a positive transaction (giving points)
     */
    fun isPositive(): Boolean = type == TransactionType.GIVE && points > 0
    
    /**
     * Checks if this is a negative transaction (deducting points)
     */
    fun isNegative(): Boolean = type == TransactionType.DEDUCT && points < 0
    
    /**
     * Gets the absolute value of points for display purposes
     */
    fun getAbsolutePoints(): Int = kotlin.math.abs(points)
}