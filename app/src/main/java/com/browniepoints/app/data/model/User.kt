package com.browniepoints.app.data.model

import com.browniepoints.app.data.validation.ValidationResult
import com.browniepoints.app.data.validation.ValidationUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val matchingCode: String = "",
    val connectedUserId: String? = null,
    val connected: Boolean = false, // Whether user has an active connection
    val totalPointsReceived: Int = 0,
    val fcmToken: String? = null,
    val createdAt: Timestamp = Timestamp.now()
) {
    /**
     * Validates the User data model for data integrity
     * @return ValidationResult indicating if the user data is valid
     */
    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (uid.isBlank()) {
            errors.add("User ID cannot be empty")
        }
        
        if (displayName.isBlank()) {
            errors.add("Display name cannot be empty")
        }
        
        val emailValidation = ValidationUtils.validateEmail(email)
        if (emailValidation is ValidationResult.Error) {
            errors.addAll(emailValidation.errors)
        }
        
        val matchingCodeValidation = ValidationUtils.validateMatchingCode(matchingCode)
        if (matchingCodeValidation is ValidationResult.Error) {
            errors.addAll(matchingCodeValidation.errors)
        }
        
        // Note: totalPointsReceived can be negative due to point deductions in relationship conflicts
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
}

