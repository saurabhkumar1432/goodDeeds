package com.browniepoints.app.data.model

import com.browniepoints.app.data.validation.ValidationResult
import com.browniepoints.app.data.validation.ValidationUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.text.SimpleDateFormat
import java.util.*

data class Timeout(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val connectionId: String = "",
    val startTime: Timestamp = Timestamp.now(),
    val duration: Long = DEFAULT_DURATION_MS, // 30 minutes in milliseconds
    @PropertyName("active")
    val isActive: Boolean = true,
    val createdDate: String = getCurrentDateString() // YYYY-MM-DD format for daily tracking
) {
    companion object {
        const val DEFAULT_DURATION_MS = 30 * 60 * 1000L // 30 minutes
        const val MAX_TIMEOUTS_PER_DAY = 1
        
        private fun getCurrentDateString(): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return dateFormat.format(Date())
        }
    }
    
    /**
     * Calculates the end time of the timeout
     */
    fun getEndTime(): Timestamp {
        val endTimeMillis = startTime.toDate().time + duration
        return Timestamp(Date(endTimeMillis))
    }
    
    /**
     * Checks if the timeout has expired
     */
    fun hasExpired(): Boolean {
        val currentTime = System.currentTimeMillis()
        val endTime = startTime.toDate().time + duration
        return currentTime >= endTime
    }
    
    /**
     * Gets remaining time in milliseconds
     */
    fun getRemainingTimeMs(): Long {
        val currentTime = System.currentTimeMillis()
        val endTime = startTime.toDate().time + duration
        return maxOf(0L, endTime - currentTime)
    }
    
    /**
     * Gets remaining time in minutes
     */
    fun getRemainingTimeMinutes(): Int {
        return (getRemainingTimeMs() / (60 * 1000)).toInt()
    }
    
    /**
     * Validates the Timeout data model for data integrity
     * @return ValidationResult indicating if the timeout data is valid
     */
    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        
        if (id.isBlank()) {
            errors.add("Timeout ID cannot be empty")
        }
        
        if (userId.isBlank()) {
            errors.add("User ID cannot be empty")
        }
        
        if (connectionId.isBlank()) {
            errors.add("Connection ID cannot be empty")
        }
        
        val durationValidation = ValidationUtils.validateTimeoutDuration(duration)
        if (durationValidation is ValidationResult.Error) {
            errors.addAll(durationValidation.errors)
        }
        
        if (createdDate.isBlank()) {
            errors.add("Created date cannot be empty")
        }
        
        val dateValidation = ValidationUtils.validateDateFormat(createdDate)
        if (dateValidation is ValidationResult.Error) {
            errors.addAll(dateValidation.errors)
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    /**
     * Checks if this timeout belongs to a specific user
     */
    fun belongsTo(userId: String): Boolean = this.userId == userId
    
    /**
     * Checks if this timeout is for today
     */
    fun isForToday(): Boolean = createdDate == getCurrentDateString()
    
    /**
     * Creates an expired version of this timeout
     */
    fun markAsExpired(): Timeout = copy(isActive = false)
}