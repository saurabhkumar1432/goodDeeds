package com.browniepoints.app.data.error

import com.browniepoints.app.data.validation.ValidationResult

/**
 * Utility class for handling validation errors and converting them to user-friendly messages
 */
object ValidationErrorHandler {
    
    /**
     * Converts validation errors to user-friendly messages
     */
    fun getErrorMessage(validationResult: ValidationResult): String {
        return when (validationResult) {
            is ValidationResult.Success -> ""
            is ValidationResult.Error -> {
                // Return the first error for UI display, or combine if multiple
                if (validationResult.errors.size == 1) {
                    validationResult.firstError
                } else {
                    "Multiple validation errors: ${validationResult.allErrors}"
                }
            }
        }
    }
    
    /**
     * Gets all error messages as a list for detailed error display
     */
    fun getAllErrorMessages(validationResult: ValidationResult): List<String> {
        return validationResult.errorMessages
    }
    
    /**
     * Checks if a validation result contains specific error types
     */
    fun hasErrorType(validationResult: ValidationResult, errorKeyword: String): Boolean {
        return validationResult.errorMessages.any { it.contains(errorKeyword, ignoreCase = true) }
    }
    
    /**
     * Creates a formatted error message for UI display
     */
    fun formatErrorForUI(validationResult: ValidationResult, fieldName: String = ""): String {
        if (validationResult.isSuccess) return ""
        
        val prefix = if (fieldName.isNotBlank()) "$fieldName: " else ""
        return "$prefix${getErrorMessage(validationResult)}"
    }
    
    /**
     * Combines multiple validation results and formats for UI
     */
    fun formatMultipleErrors(
        validationResults: Map<String, ValidationResult>,
        separator: String = "\n"
    ): String {
        return validationResults
            .filter { it.value.isError }
            .map { (fieldName, result) -> formatErrorForUI(result, fieldName) }
            .joinToString(separator)
    }
    
    /**
     * Creates an AppError.ValidationError from validation result
     */
    fun createValidationError(
        validationResult: ValidationResult,
        field: String? = null
    ): AppError.ValidationError {
        return AppError.ValidationError(
            errorMessage = getErrorMessage(validationResult),
            field = field
        )
    }
    
    /**
     * Creates a validation exception with detailed error information
     */
    fun createValidationException(
        validationResult: ValidationResult,
        operation: String = "validation"
    ): ValidationException {
        return ValidationException(
            message = "Validation failed for $operation: ${getErrorMessage(validationResult)}",
            errors = validationResult.errorMessages
        )
    }
    
    /**
     * Converts ValidationResult to appropriate AppError
     */
    fun toAppError(validationResult: ValidationResult, field: String? = null): AppError? {
        return if (validationResult.isError) {
            createValidationError(validationResult, field)
        } else {
            null
        }
    }
}

/**
 * Custom exception for validation failures
 */
class ValidationException(
    message: String,
    val errors: List<String> = emptyList(),
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Gets the first validation error
     */
    val firstError: String get() = errors.firstOrNull() ?: message ?: "Unknown validation error"
    
    /**
     * Gets all errors as a formatted string
     */
    val allErrors: String get() = errors.joinToString(", ")
    
    /**
     * Checks if this exception contains a specific error type
     */
    fun hasErrorType(errorKeyword: String): Boolean {
        return errors.any { it.contains(errorKeyword, ignoreCase = true) } ||
                (message?.contains(errorKeyword, ignoreCase = true) == true)
    }
}