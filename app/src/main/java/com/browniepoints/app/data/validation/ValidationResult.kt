package com.browniepoints.app.data.validation

/**
 * Sealed class representing the result of a validation operation
 */
sealed class ValidationResult {
    /**
     * Validation passed successfully
     */
    object Success : ValidationResult()
    
    /**
     * Validation failed with specific errors
     * @param errors List of validation error messages
     */
    data class Error(val errors: List<String>) : ValidationResult() {
        constructor(error: String) : this(listOf(error))
        
        /**
         * Get the first error message
         */
        val firstError: String get() = errors.firstOrNull() ?: "Unknown validation error"
        
        /**
         * Get all errors as a single formatted string
         */
        val allErrors: String get() = errors.joinToString(", ")
    }
    
    /**
     * Check if validation was successful
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Check if validation failed
     */
    val isError: Boolean get() = this is Error
    
    /**
     * Get error messages if validation failed, empty list otherwise
     */
    val errorMessages: List<String> get() = when (this) {
        is Success -> emptyList()
        is Error -> errors
    }
}

/**
 * Extension function to combine multiple validation results
 */
fun List<ValidationResult>.combine(): ValidationResult {
    val errors = mutableListOf<String>()
    
    forEach { result ->
        if (result is ValidationResult.Error) {
            errors.addAll(result.errors)
        }
    }
    
    return if (errors.isEmpty()) {
        ValidationResult.Success
    } else {
        ValidationResult.Error(errors)
    }
}

/**
 * Extension function to add additional validation
 */
infix fun ValidationResult.and(other: ValidationResult): ValidationResult {
    return listOf(this, other).combine()
}