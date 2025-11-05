package com.browniepoints.app.data.validation

/**
 * Interface for field-specific validators
 */
interface FieldValidator<T> {
    /**
     * Validates a field value
     * @param value The value to validate
     * @return ValidationResult indicating success or failure with error messages
     */
    fun validate(value: T): ValidationResult
}

/**
 * Validator for point amounts
 */
class PointAmountValidator : FieldValidator<Int> {
    override fun validate(value: Int): ValidationResult {
        return ValidationUtils.validatePointAmount(value)
    }
}

/**
 * Validator for message content
 */
class MessageValidator : FieldValidator<String?> {
    override fun validate(value: String?): ValidationResult {
        return ValidationUtils.validateMessage(value)
    }
}

/**
 * Validator for matching codes
 */
class MatchingCodeValidator : FieldValidator<String?> {
    override fun validate(value: String?): ValidationResult {
        return ValidationUtils.validateMatchingCode(value)
    }
}

/**
 * Validator for user IDs
 */
class UserIdValidator : FieldValidator<String?> {
    override fun validate(value: String?): ValidationResult {
        return ValidationUtils.validateUserId(value)
    }
}

/**
 * Validator for connection IDs
 */
class ConnectionIdValidator : FieldValidator<String?> {
    override fun validate(value: String?): ValidationResult {
        return ValidationUtils.validateConnectionId(value)
    }
}

/**
 * Validator for deduction point amounts
 */
class DeductionAmountValidator : FieldValidator<Int> {
    override fun validate(value: Int): ValidationResult {
        return ValidationUtils.validateDeductionAmount(value)
    }
}

/**
 * Validator for timeout duration
 */
class TimeoutDurationValidator : FieldValidator<Long> {
    override fun validate(value: Long): ValidationResult {
        return ValidationUtils.validateTimeoutDuration(value)
    }
}

/**
 * Validator for date format (YYYY-MM-DD)
 */
class DateFormatValidator : FieldValidator<String?> {
    override fun validate(value: String?): ValidationResult {
        return ValidationUtils.validateDateFormat(value)
    }
}

/**
 * Validator for email addresses
 */
class EmailValidator : FieldValidator<String?> {
    override fun validate(value: String?): ValidationResult {
        return ValidationUtils.validateEmail(value)
    }
}

/**
 * Validator for transaction points based on type
 */
class TransactionPointsValidator(private val isDeduction: Boolean) : FieldValidator<Int> {
    override fun validate(value: Int): ValidationResult {
        return ValidationUtils.validateTransactionPoints(value, isDeduction)
    }
}

/**
 * Composite validator that combines multiple validators
 */
class CompositeValidator<T>(
    private val validators: List<FieldValidator<T>>
) : FieldValidator<T> {
    
    override fun validate(value: T): ValidationResult {
        return validators.map { it.validate(value) }.combine()
    }
}

/**
 * Builder class for creating composite validators
 */
class ValidatorBuilder<T> {
    private val validators = mutableListOf<FieldValidator<T>>()
    
    fun add(validator: FieldValidator<T>): ValidatorBuilder<T> {
        validators.add(validator)
        return this
    }
    
    fun build(): FieldValidator<T> {
        return CompositeValidator(validators.toList())
    }
}