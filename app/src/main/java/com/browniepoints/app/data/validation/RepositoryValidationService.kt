package com.browniepoints.app.data.validation

import com.browniepoints.app.data.error.AppError
import com.browniepoints.app.data.error.ValidationErrorHandler
import com.browniepoints.app.data.model.*

/**
 * Service for validating data before repository operations
 * Ensures data integrity and business rule compliance before Firestore operations
 */
class RepositoryValidationService {
    
    /**
     * Validates user data before creation or update operations
     */
    fun validateUserForRepository(user: User, isUpdate: Boolean = false): Result<Unit> {
        val validationResult = DataIntegrityValidator.validateUserProfile(user, isUpdate)
        
        return if (validationResult.isSuccess) {
            Result.success(Unit)
        } else {
            val error = ValidationErrorHandler.createValidationError(validationResult, "User")
            Result.failure(error)
        }
    }
    
    /**
     * Validates connection data before creation or update operations
     */
    fun validateConnectionForRepository(connection: Connection): Result<Unit> {
        val validationResult = DataIntegrityValidator.validateConnection(connection)
        
        return if (validationResult.isSuccess) {
            Result.success(Unit)
        } else {
            val error = ValidationErrorHandler.createValidationError(validationResult, "Connection")
            Result.failure(error)
        }
    }
    
    /**
     * Validates transaction data before creation
     */
    fun validateTransactionForRepository(
        transaction: Transaction,
        senderBalance: Int = 0
    ): Result<Unit> {
        val validations = mutableListOf<ValidationResult>()
        
        // Basic transaction validation
        validations.add(DataIntegrityValidator.validateTransaction(transaction))
        
        // Business constraint validation
        validations.add(
            DataIntegrityValidator.validatePointTransactionConstraints(
                senderBalance = senderBalance,
                points = transaction.points,
                transactionType = transaction.type
            )
        )
        
        val combinedResult = validations.combine()
        
        return if (combinedResult.isSuccess) {
            Result.success(Unit)
        } else {
            val error = ValidationErrorHandler.createValidationError(combinedResult, "Transaction")
            Result.failure(error)
        }
    }
    
    /**
     * Validates timeout data before creation
     */
    fun validateTimeoutForRepository(
        timeout: Timeout,
        existingTimeoutsToday: Int = 0
    ): Result<Unit> {
        val validations = mutableListOf<ValidationResult>()
        
        // Basic timeout validation
        validations.add(DataIntegrityValidator.validateTimeout(timeout))
        
        // Business constraint validation
        validations.add(
            DataIntegrityValidator.validateTimeoutConstraints(
                userId = timeout.userId,
                connectionId = timeout.connectionId,
                existingTimeoutsToday = existingTimeoutsToday,
                duration = timeout.duration
            )
        )
        
        val combinedResult = validations.combine()
        
        return if (combinedResult.isSuccess) {
            Result.success(Unit)
        } else {
            val error = ValidationErrorHandler.createValidationError(combinedResult, "Timeout")
            Result.failure(error)
        }
    }
    
    /**
     * Validates user action permissions before allowing operations
     */
    fun validateUserActionPermissions(
        userId: String,
        action: UserAction,
        targetUserId: String? = null,
        connectionId: String? = null
    ): Result<Unit> {
        val validationResult = DataIntegrityValidator.validateUserAction(
            userId = userId,
            action = action,
            targetUserId = targetUserId,
            connectionId = connectionId
        )
        
        return if (validationResult.isSuccess) {
            Result.success(Unit)
        } else {
            val error = ValidationErrorHandler.createValidationError(validationResult, "User Action")
            Result.failure(error)
        }
    }
    
    /**
     * Validates matching code format and sanitizes it
     */
    fun validateAndSanitizeMatchingCode(code: String?): Result<String> {
        val sanitizedCode = ValidationUtils.sanitizeMatchingCode(code)
        val validationResult = ValidationUtils.validateMatchingCode(sanitizedCode)
        
        return if (validationResult.isSuccess) {
            Result.success(sanitizedCode)
        } else {
            val error = ValidationErrorHandler.createValidationError(validationResult, "Matching Code")
            Result.failure(error)
        }
    }
    
    /**
     * Validates and sanitizes message content
     */
    fun validateAndSanitizeMessage(message: String?): Result<String?> {
        if (message.isNullOrBlank()) {
            return Result.success(null)
        }
        
        val sanitizedMessage = ValidationUtils.sanitizeText(message)
        val validationResult = ValidationUtils.validateMessage(sanitizedMessage)
        
        return if (validationResult.isSuccess) {
            Result.success(sanitizedMessage)
        } else {
            val error = ValidationErrorHandler.createValidationError(validationResult, "Message")
            Result.failure(error)
        }
    }
    
    /**
     * Validates point amount based on transaction type
     */
    fun validatePointAmount(points: Int, isDeduction: Boolean): Result<Unit> {
        val validationResult = ValidationUtils.validateTransactionPoints(points, isDeduction)
        
        return if (validationResult.isSuccess) {
            Result.success(Unit)
        } else {
            val error = ValidationErrorHandler.createValidationError(validationResult, "Points")
            Result.failure(error)
        }
    }
    
    /**
     * Validates that users are different (for connections and transactions)
     */
    fun validateDifferentUsers(userId1: String?, userId2: String?): Result<Unit> {
        val validationResult = ValidationUtils.validateDifferentUsers(userId1, userId2)
        
        return if (validationResult.isSuccess) {
            Result.success(Unit)
        } else {
            val error = ValidationErrorHandler.createValidationError(validationResult, "Users")
            Result.failure(error)
        }
    }
    
    /**
     * Comprehensive validation for creating a new user profile
     */
    fun validateNewUserProfile(
        uid: String,
        displayName: String,
        email: String,
        matchingCode: String
    ): Result<User> {
        val validations = mutableListOf<ValidationResult>()
        
        validations.add(ValidationUtils.validateUserId(uid))
        validations.add(ValidationUtils.validateNotBlank(displayName, "Display name"))
        validations.add(ValidationUtils.validateLength(displayName, "Display name", 1, 50))
        validations.add(ValidationUtils.validateSafeText(displayName, "Display name"))
        validations.add(ValidationUtils.validateEmail(email))
        validations.add(ValidationUtils.validateMatchingCode(matchingCode))
        
        val combinedResult = validations.combine()
        
        return if (combinedResult.isSuccess) {
            val user = User(
                uid = uid,
                displayName = displayName.trim(),
                email = email.trim(),
                matchingCode = matchingCode.uppercase().trim()
            )
            Result.success(user)
        } else {
            val error = ValidationErrorHandler.createValidationError(combinedResult, "User Profile")
            Result.failure(error)
        }
    }
}