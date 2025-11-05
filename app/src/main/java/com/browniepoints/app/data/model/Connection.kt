package com.browniepoints.app.data.model

import com.browniepoints.app.data.validation.ValidationResult
import com.browniepoints.app.data.validation.ValidationUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class Connection(
    @DocumentId
    val id: String = "",
    val user1Id: String = "",
    val user2Id: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    @PropertyName("active")
    val isActive: Boolean = true
) {
    /**
     * Validates the Connection data model for data integrity
     * @return ValidationResult indicating if the connection data is valid
     */
    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (id.isBlank()) {
            errors.add("Connection ID cannot be empty")
        }
        
        if (user1Id.isBlank()) {
            errors.add("User1 ID cannot be empty")
        }
        
        if (user2Id.isBlank()) {
            errors.add("User2 ID cannot be empty")
        }
        
        val differentUsersValidation = ValidationUtils.validateDifferentUsers(user1Id, user2Id, "Connected users")
        if (differentUsersValidation is ValidationResult.Error) {
            errors.addAll(differentUsersValidation.errors)
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    /**
     * Checks if a specific user is part of this connection
     */
    fun containsUser(userId: String): Boolean {
        return user1Id == userId || user2Id == userId
    }
    
    /**
     * Gets the partner user ID for a given user ID
     */
    fun getPartnerUserId(userId: String): String? {
        return when (userId) {
            user1Id -> user2Id
            user2Id -> user1Id
            else -> null
        }
    }
}