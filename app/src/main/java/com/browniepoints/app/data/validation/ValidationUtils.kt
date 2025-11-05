package com.browniepoints.app.data.validation

import android.util.Patterns
import java.util.regex.Pattern

/**
 * Utility class containing common validation functions
 */
object ValidationUtils {
    
    // Constants for validation rules
    const val MIN_POINTS = 1
    const val MAX_POINTS = 10
    const val MAX_MESSAGE_LENGTH = 200
    const val MATCHING_CODE_LENGTH = 6
    
    // Regex patterns
    private val MATCHING_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{6}$")
    private val SAFE_TEXT_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\p{P}\\p{Z}]*$")
    
    /**
     * Validates that a value is not null
     */
    fun validateNotNull(value: Any?, fieldName: String): ValidationResult {
        return if (value != null) {
            ValidationResult.Success
        } else {
            ValidationResult.Error("$fieldName cannot be null")
        }
    }
    
    /**
     * Validates that a string is not null or blank
     */
    fun validateNotBlank(value: String?, fieldName: String): ValidationResult {
        return when {
            value == null -> ValidationResult.Error("$fieldName cannot be null")
            value.isBlank() -> ValidationResult.Error("$fieldName cannot be empty")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Validates string length constraints
     */
    fun validateLength(
        value: String?,
        fieldName: String,
        minLength: Int = 0,
        maxLength: Int = Int.MAX_VALUE
    ): ValidationResult {
        if (value == null) {
            return ValidationResult.Error("$fieldName cannot be null")
        }
        
        return when {
            value.length < minLength -> ValidationResult.Error("$fieldName must be at least $minLength characters")
            value.length > maxLength -> ValidationResult.Error("$fieldName cannot exceed $maxLength characters")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Validates numeric range constraints
     */
    fun validateRange(
        value: Int,
        fieldName: String,
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE
    ): ValidationResult {
        return when {
            value < min -> ValidationResult.Error("$fieldName must be at least $min")
            value > max -> ValidationResult.Error("$fieldName cannot exceed $max")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Validates point amounts for giving (1-10 range)
     */
    fun validatePointAmount(points: Int): ValidationResult {
        return validateRange(points, "Points", MIN_POINTS, MAX_POINTS)
    }
    
    /**
     * Validates point amounts for deduction (-10 to -1 range)
     */
    fun validateDeductionAmount(points: Int): ValidationResult {
        return validateRange(points, "Deduction points", -MAX_POINTS, -MIN_POINTS)
    }
    
    /**
     * Validates point amounts based on transaction type
     */
    fun validateTransactionPoints(points: Int, isDeduction: Boolean): ValidationResult {
        return if (isDeduction) {
            validateDeductionAmount(points)
        } else {
            validatePointAmount(points)
        }
    }
    
    /**
     * Validates message content and length
     */
    fun validateMessage(message: String?): ValidationResult {
        // Message is optional, so null/empty is valid
        if (message.isNullOrBlank()) {
            return ValidationResult.Success
        }
        
        val lengthValidation = validateLength(message, "Message", maxLength = MAX_MESSAGE_LENGTH)
        val contentValidation = validateSafeText(message, "Message")
        
        return lengthValidation and contentValidation
    }
    
    /**
     * Validates matching code format
     */
    fun validateMatchingCode(code: String?): ValidationResult {
        if (code == null) {
            return ValidationResult.Error("Matching code cannot be null")
        }
        
        val trimmedCode = code.trim().uppercase()
        
        return when {
            trimmedCode.length != MATCHING_CODE_LENGTH -> {
                ValidationResult.Error("Matching code must be exactly $MATCHING_CODE_LENGTH characters")
            }
            !MATCHING_CODE_PATTERN.matcher(trimmedCode).matches() -> {
                ValidationResult.Error("Matching code can only contain letters and numbers")
            }
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Validates that text contains only safe characters (prevents injection attacks)
     */
    fun validateSafeText(text: String?, fieldName: String): ValidationResult {
        if (text == null) {
            return ValidationResult.Error("$fieldName cannot be null")
        }
        
        return if (SAFE_TEXT_PATTERN.matcher(text).matches()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error("$fieldName contains invalid characters")
        }
    }
    
    /**
     * Validates user ID format (Firebase UID)
     */
    fun validateUserId(userId: String?): ValidationResult {
        if (userId.isNullOrBlank()) {
            return ValidationResult.Error("User ID cannot be empty")
        }
        
        return when {
            userId.length < 10 -> ValidationResult.Error("Invalid user ID format")
            userId.length > 128 -> ValidationResult.Error("User ID too long")
            !userId.matches(Regex("^[a-zA-Z0-9_-]+$")) -> ValidationResult.Error("User ID contains invalid characters")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Validates connection ID format
     */
    fun validateConnectionId(connectionId: String?): ValidationResult {
        if (connectionId.isNullOrBlank()) {
            return ValidationResult.Error("Connection ID cannot be empty")
        }
        
        return when {
            connectionId.length < 10 -> ValidationResult.Error("Invalid connection ID format")
            connectionId.length > 128 -> ValidationResult.Error("Connection ID too long")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Sanitizes text input by trimming and removing potentially harmful characters
     */
    fun sanitizeText(text: String?): String {
        return text?.trim()
            ?.replace(Regex("[<>\"'&]"), "") // Remove potentially harmful characters
            ?.take(MAX_MESSAGE_LENGTH) // Ensure length limit
            ?: ""
    }
    
    /**
     * Sanitizes and validates matching code
     */
    fun sanitizeMatchingCode(code: String?): String {
        return code?.trim()?.uppercase()?.replace(Regex("[^A-Z0-9]"), "") ?: ""
    }
    
    /**
     * Validates timeout duration (must be positive)
     */
    fun validateTimeoutDuration(duration: Long): ValidationResult {
        return when {
            duration <= 0 -> ValidationResult.Error("Timeout duration must be positive")
            duration > 24 * 60 * 60 * 1000 -> ValidationResult.Error("Timeout duration cannot exceed 24 hours")
            else -> ValidationResult.Success
        }
    }
    
    /**
     * Validates date format (YYYY-MM-DD)
     */
    fun validateDateFormat(date: String?): ValidationResult {
        if (date.isNullOrBlank()) {
            return ValidationResult.Error("Date cannot be empty")
        }
        
        val datePattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        return if (datePattern.matches(date)) {
            ValidationResult.Success
        } else {
            ValidationResult.Error("Date must be in YYYY-MM-DD format")
        }
    }
    
    /**
     * Validates email format
     */
    fun validateEmail(email: String?): ValidationResult {
        if (email.isNullOrBlank()) {
            return ValidationResult.Error("Email cannot be empty")
        }
        
        return if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error("Invalid email format")
        }
    }
    
    /**
     * Validates that two user IDs are different (for connections and transactions)
     */
    fun validateDifferentUsers(userId1: String?, userId2: String?, fieldName: String = "Users"): ValidationResult {
        return when {
            userId1.isNullOrBlank() -> ValidationResult.Error("First user ID cannot be empty")
            userId2.isNullOrBlank() -> ValidationResult.Error("Second user ID cannot be empty")
            userId1 == userId2 -> ValidationResult.Error("$fieldName cannot be the same")
            else -> ValidationResult.Success
        }
    }
}