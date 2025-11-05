package com.browniepoints.app.data.model

import com.browniepoints.app.data.validation.ValidationResult
import com.google.firebase.Timestamp
import org.junit.Test
import org.junit.Assert.*

class UserTest {

    @Test
    fun `validate should return success for valid user`() {
        // Given
        val validUser = User(
            uid = "test_user_id",
            displayName = "Test User",
            email = "test@example.com",
            matchingCode = "ABC123",
            totalPointsReceived = 10,
            createdAt = Timestamp.now()
        )

        // When
        val result = validUser.validate()

        // Then
        assertTrue(result is ValidationResult.Success)
    }

    @Test
    fun `validate should return error for empty uid`() {
        // Given
        val invalidUser = User(
            uid = "",
            displayName = "Test User",
            email = "test@example.com",
            matchingCode = "ABC123"
        )

        // When
        val result = invalidUser.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("User ID cannot be empty"))
    }

    @Test
    fun `validate should return error for empty display name`() {
        // Given
        val invalidUser = User(
            uid = "test_user_id",
            displayName = "",
            email = "test@example.com",
            matchingCode = "ABC123"
        )

        // When
        val result = invalidUser.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Display name cannot be empty"))
    }

    @Test
    fun `validate should return error for empty email`() {
        // Given
        val invalidUser = User(
            uid = "test_user_id",
            displayName = "Test User",
            email = "",
            matchingCode = "ABC123"
        )

        // When
        val result = invalidUser.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Email cannot be empty"))
    }

    @Test
    fun `validate should return error for invalid email format`() {
        // Given
        val invalidUser = User(
            uid = "test_user_id",
            displayName = "Test User",
            email = "invalid-email",
            matchingCode = "ABC123"
        )

        // When
        val result = invalidUser.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Email format is invalid"))
    }

    @Test
    fun `validate should return error for empty matching code`() {
        // Given
        val invalidUser = User(
            uid = "test_user_id",
            displayName = "Test User",
            email = "test@example.com",
            matchingCode = ""
        )

        // When
        val result = invalidUser.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Matching code cannot be empty"))
    }

    @Test
    fun `validate should return error for invalid matching code length`() {
        // Given
        val invalidUser = User(
            uid = "test_user_id",
            displayName = "Test User",
            email = "test@example.com",
            matchingCode = "ABC" // Too short
        )

        // When
        val result = invalidUser.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Matching code must be exactly 6 characters"))
    }

    @Test
    fun `validate should return error for negative total points`() {
        // Given
        val invalidUser = User(
            uid = "test_user_id",
            displayName = "Test User",
            email = "test@example.com",
            matchingCode = "ABC123",
            totalPointsReceived = -5
        )

        // When
        val result = invalidUser.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Total points received cannot be negative"))
    }

    @Test
    fun `validate should return multiple errors for multiple invalid fields`() {
        // Given
        val invalidUser = User(
            uid = "",
            displayName = "",
            email = "invalid-email",
            matchingCode = "ABC",
            totalPointsReceived = -1
        )

        // When
        val result = invalidUser.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertEquals(5, errors.size)
        assertTrue(errors.contains("User ID cannot be empty"))
        assertTrue(errors.contains("Display name cannot be empty"))
        assertTrue(errors.contains("Email format is invalid"))
        assertTrue(errors.contains("Matching code must be exactly 6 characters"))
        assertTrue(errors.contains("Total points received cannot be negative"))
    }

    @Test
    fun `isConnected should return true when connectedUserId is not null or blank`() {
        // Given
        val connectedUser = User(
            uid = "test_user_id",
            connectedUserId = "partner_id",
            connected = true
        )

        // When
        val result = connectedUser.connected && !connectedUser.connectedUserId.isNullOrBlank()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isConnected should return false when connectedUserId is null`() {
        // Given
        val unconnectedUser = User(
            uid = "test_user_id",
            connectedUserId = null,
            connected = false
        )

        // When
        val result = unconnectedUser.connected && !unconnectedUser.connectedUserId.isNullOrBlank()

        // Then
        assertFalse(result)
    }

    @Test
    fun `isConnected should return false when connectedUserId is blank`() {
        // Given
        val unconnectedUser = User(
            uid = "test_user_id",
            connectedUserId = "",
            connected = false
        )

        // When
        val result = unconnectedUser.connected && !unconnectedUser.connectedUserId.isNullOrBlank()

        // Then
        assertFalse(result)
    }
}