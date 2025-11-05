package com.browniepoints.app.data.validation

import com.browniepoints.app.data.model.*

/**
 * Service class for comprehensive data integrity validation
 * Provides centralized validation for all data models and business rules
 */
object DataIntegrityValidator {
    
    /**
     * Validates a complete user profile for creation or update
     */
    fun validateUserProfile(user: User, isUpdate: Boolean = false): ValidationResult {
        val validations = mutableListOf<ValidationResult>()
        
        // Basic model validation
        validations.add(user.validate())
        
        // Additional business rule validations
        if (!isUpdate) {
            // For new users, ensure all required fields are present
            validations.add(ValidationUtils.validateNotBlank(user.uid, "User ID"))
            validations.add(ValidationUtils.validateNotBlank(user.displayName, "Display name"))
            validations.add(ValidationUtils.validateNotBlank(user.email, "Email"))
            validations.add(ValidationUtils.validateNotBlank(user.matchingCode, "Matching code"))
        }
        
        // Validate display name length and content
        validations.add(ValidationUtils.validateLength(user.displayName, "Display name", 1, 50))
        validations.add(ValidationUtils.validateSafeText(user.displayName, "Display name"))
        
        return validations.combine()
    }
    
    /**
     * Validates a connection between two users
     */
    fun validateConnection(connection: Connection): ValidationResult {
        val validations = mutableListOf<ValidationResult>()
        
        // Basic model validation
        validations.add(connection.validate())
        
        // Additional business rule validations
        validations.add(ValidationUtils.validateUserId(connection.user1Id))
        validations.add(ValidationUtils.validateUserId(connection.user2Id))
        
        return validations.combine()
    }
    
    /**
     * Validates a transaction between users
     */
    fun validateTransaction(transaction: Transaction): ValidationResult {
        val validations = mutableListOf<ValidationResult>()
        
        // Basic model validation
        validations.add(transaction.validate())
        
        // Additional business rule validations
        validations.add(ValidationUtils.validateUserId(transaction.senderId))
        validations.add(ValidationUtils.validateUserId(transaction.receiverId))
        validations.add(ValidationUtils.validateConnectionId(transaction.connectionId))
        
        // Validate transaction type consistency
        when (transaction.type) {
            TransactionType.GIVE -> {
                if (transaction.points <= 0) {
                    validations.add(ValidationResult.Error("Give transactions must have positive points"))
                }
            }
            TransactionType.DEDUCT -> {
                if (transaction.points >= 0) {
                    validations.add(ValidationResult.Error("Deduct transactions must have negative points"))
                }
            }
        }
        
        return validations.combine()
    }
    
    /**
     * Validates a timeout request
     */
    fun validateTimeout(timeout: Timeout): ValidationResult {
        val validations = mutableListOf<ValidationResult>()
        
        // Basic model validation
        validations.add(timeout.validate())
        
        // Additional business rule validations
        validations.add(ValidationUtils.validateUserId(timeout.userId))
        validations.add(ValidationUtils.validateConnectionId(timeout.connectionId))
        
        // Validate timeout is for today (business rule)
        if (timeout.isActive && !timeout.isForToday()) {
            validations.add(ValidationResult.Error("Active timeouts must be for the current date"))
        }
        
        return validations.combine()
    }
    
    /**
     * Validates that a user can perform a specific action
     */
    fun validateUserAction(
        userId: String,
        action: UserAction,
        targetUserId: String? = null,
        connectionId: String? = null
    ): ValidationResult {
        val validations = mutableListOf<ValidationResult>()
        
        // Validate user ID
        validations.add(ValidationUtils.validateUserId(userId))
        
        when (action) {
            UserAction.GIVE_POINTS, UserAction.DEDUCT_POINTS -> {
                if (targetUserId == null) {
                    validations.add(ValidationResult.Error("Target user required for point transactions"))
                } else {
                    validations.add(ValidationUtils.validateUserId(targetUserId))
                    validations.add(ValidationUtils.validateDifferentUsers(userId, targetUserId, "Transaction users"))
                }
                
                if (connectionId == null) {
                    validations.add(ValidationResult.Error("Connection ID required for point transactions"))
                } else {
                    validations.add(ValidationUtils.validateConnectionId(connectionId))
                }
            }
            
            UserAction.REQUEST_TIMEOUT -> {
                if (connectionId == null) {
                    validations.add(ValidationResult.Error("Connection ID required for timeout requests"))
                } else {
                    validations.add(ValidationUtils.validateConnectionId(connectionId))
                }
            }
            
            UserAction.CREATE_CONNECTION -> {
                if (targetUserId == null) {
                    validations.add(ValidationResult.Error("Target user required for connection creation"))
                } else {
                    validations.add(ValidationUtils.validateUserId(targetUserId))
                    validations.add(ValidationUtils.validateDifferentUsers(userId, targetUserId, "Connection users"))
                }
            }
        }
        
        return validations.combine()
    }
    
    /**
     * Validates business constraints for point transactions
     */
    fun validatePointTransactionConstraints(
        senderBalance: Int,
        points: Int,
        transactionType: TransactionType
    ): ValidationResult {
        val validations = mutableListOf<ValidationResult>()
        
        when (transactionType) {
            TransactionType.GIVE -> {
                // No balance check needed for giving points (users can give regardless of their balance)
                validations.add(ValidationUtils.validatePointAmount(points))
            }
            
            TransactionType.DEDUCT -> {
                // Validate deduction amount (should be negative)
                validations.add(ValidationUtils.validateDeductionAmount(points))
                
                // Note: We allow negative balances for relationship conflicts
                // This is intentional design for couple dynamics
            }
        }
        
        return validations.combine()
    }
    
    /**
     * Validates timeout constraints (daily limit, duration, etc.)
     */
    fun validateTimeoutConstraints(
        userId: String,
        connectionId: String,
        existingTimeoutsToday: Int,
        duration: Long = Timeout.DEFAULT_DURATION_MS
    ): ValidationResult {
        val validations = mutableListOf<ValidationResult>()
        
        // Check daily timeout limit
        if (existingTimeoutsToday >= Timeout.MAX_TIMEOUTS_PER_DAY) {
            validations.add(ValidationResult.Error("Daily timeout limit reached. You can request ${Timeout.MAX_TIMEOUTS_PER_DAY} timeout per day."))
        }
        
        // Validate duration
        validations.add(ValidationUtils.validateTimeoutDuration(duration))
        
        // Validate user and connection
        validations.add(ValidationUtils.validateUserId(userId))
        validations.add(ValidationUtils.validateConnectionId(connectionId))
        
        return validations.combine()
    }
}

/**
 * Enum representing different user actions that require validation
 */
enum class UserAction {
    GIVE_POINTS,
    DEDUCT_POINTS,
    REQUEST_TIMEOUT,
    CREATE_CONNECTION
}