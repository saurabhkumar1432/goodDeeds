package com.browniepoints.app.data.model

import com.browniepoints.app.data.validation.ValidationResult
import com.google.firebase.Timestamp
import org.junit.Test
import org.junit.Assert.*

class ConnectionTest {

    @Test
    fun `validate should return success for valid connection`() {
        // Given
        val validConnection = Connection(
            id = "connection_id",
            user1Id = "user1_id",
            user2Id = "user2_id",
            createdAt = Timestamp.now(),
            isActive = true
        )

        // When
        val result = validConnection.validate()

        // Then
        assertTrue(result is ValidationResult.Success)
    }

    @Test
    fun `validate should return error for empty connection id`() {
        // Given
        val invalidConnection = Connection(
            id = "",
            user1Id = "user1_id",
            user2Id = "user2_id"
        )

        // When
        val result = invalidConnection.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Connection ID cannot be empty"))
    }

    @Test
    fun `validate should return error for empty user1Id`() {
        // Given
        val invalidConnection = Connection(
            id = "connection_id",
            user1Id = "",
            user2Id = "user2_id"
        )

        // When
        val result = invalidConnection.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("User1 ID cannot be empty"))
    }

    @Test
    fun `validate should return error for empty user2Id`() {
        // Given
        val invalidConnection = Connection(
            id = "connection_id",
            user1Id = "user1_id",
            user2Id = ""
        )

        // When
        val result = invalidConnection.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("User2 ID cannot be empty"))
    }

    @Test
    fun `validate should return error when user1Id and user2Id are the same`() {
        // Given
        val invalidConnection = Connection(
            id = "connection_id",
            user1Id = "same_user_id",
            user2Id = "same_user_id"
        )

        // When
        val result = invalidConnection.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("User cannot connect to themselves"))
    }

    @Test
    fun `validate should return multiple errors for multiple invalid fields`() {
        // Given
        val invalidConnection = Connection(
            id = "",
            user1Id = "",
            user2Id = ""
        )

        // When
        val result = invalidConnection.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertEquals(3, errors.size)
        assertTrue(errors.contains("Connection ID cannot be empty"))
        assertTrue(errors.contains("User1 ID cannot be empty"))
        assertTrue(errors.contains("User2 ID cannot be empty"))
    }

    @Test
    fun `containsUser should return true when user is user1`() {
        // Given
        val userId = "test_user_id"
        val connection = Connection(
            id = "connection_id",
            user1Id = userId,
            user2Id = "other_user_id"
        )

        // When
        val result = connection.containsUser(userId)

        // Then
        assertTrue(result)
    }

    @Test
    fun `containsUser should return true when user is user2`() {
        // Given
        val userId = "test_user_id"
        val connection = Connection(
            id = "connection_id",
            user1Id = "other_user_id",
            user2Id = userId
        )

        // When
        val result = connection.containsUser(userId)

        // Then
        assertTrue(result)
    }

    @Test
    fun `containsUser should return false when user is not in connection`() {
        // Given
        val userId = "test_user_id"
        val connection = Connection(
            id = "connection_id",
            user1Id = "user1_id",
            user2Id = "user2_id"
        )

        // When
        val result = connection.containsUser(userId)

        // Then
        assertFalse(result)
    }

    @Test
    fun `getPartnerUserId should return user2Id when given user1Id`() {
        // Given
        val user1Id = "user1_id"
        val user2Id = "user2_id"
        val connection = Connection(
            id = "connection_id",
            user1Id = user1Id,
            user2Id = user2Id
        )

        // When
        val result = connection.getPartnerUserId(user1Id)

        // Then
        assertEquals(user2Id, result)
    }

    @Test
    fun `getPartnerUserId should return user1Id when given user2Id`() {
        // Given
        val user1Id = "user1_id"
        val user2Id = "user2_id"
        val connection = Connection(
            id = "connection_id",
            user1Id = user1Id,
            user2Id = user2Id
        )

        // When
        val result = connection.getPartnerUserId(user2Id)

        // Then
        assertEquals(user1Id, result)
    }

    @Test
    fun `getPartnerUserId should return null when user is not in connection`() {
        // Given
        val connection = Connection(
            id = "connection_id",
            user1Id = "user1_id",
            user2Id = "user2_id"
        )

        // When
        val result = connection.getPartnerUserId("unknown_user_id")

        // Then
        assertNull(result)
    }
}