package com.browniepoints.app.data.model

import com.browniepoints.app.data.validation.ValidationResult
import com.google.firebase.Timestamp
import org.junit.Test
import org.junit.Assert.*
import java.util.*

class TimeoutTest {

    @Test
    fun `timeout should be valid with correct data`() {
        // Given
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "connection_id",
            startTime = Timestamp.now(),
            duration = Timeout.DEFAULT_DURATION_MS,
            isActive = true,
            createdDate = "2024-01-15"
        )

        // When
        val result = timeout.validate()

        // Then
        assertTrue(result is ValidationResult.Success)
    }

    @Test
    fun `timeout should be invalid with empty id`() {
        // Given
        val timeout = Timeout(
            id = "",
            userId = "user_id",
            connectionId = "connection_id",
            createdDate = "2024-01-15"
        )

        // When
        val result = timeout.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Timeout ID cannot be empty"))
    }

    @Test
    fun `timeout should be invalid with empty user id`() {
        // Given
        val timeout = Timeout(
            id = "timeout_id",
            userId = "",
            connectionId = "connection_id",
            createdDate = "2024-01-15"
        )

        // When
        val result = timeout.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("User ID cannot be empty"))
    }

    @Test
    fun `timeout should be invalid with empty connection id`() {
        // Given
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "",
            createdDate = "2024-01-15"
        )

        // When
        val result = timeout.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Connection ID cannot be empty"))
    }

    @Test
    fun `timeout should be invalid with negative duration`() {
        // Given
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "connection_id",
            duration = -1000L,
            createdDate = "2024-01-15"
        )

        // When
        val result = timeout.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Duration must be positive"))
    }

    @Test
    fun `timeout should be invalid with empty created date`() {
        // Given
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "connection_id",
            createdDate = ""
        )

        // When
        val result = timeout.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Created date cannot be empty"))
    }

    @Test
    fun `timeout should be invalid with incorrect date format`() {
        // Given
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "connection_id",
            createdDate = "01/15/2024" // Wrong format
        )

        // When
        val result = timeout.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Created date must be in YYYY-MM-DD format"))
    }

    @Test
    fun `getEndTime should calculate correct end time`() {
        // Given
        val startTime = Timestamp(Date(1000000L))
        val duration = 30 * 60 * 1000L // 30 minutes
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "connection_id",
            startTime = startTime,
            duration = duration
        )

        // When
        val endTime = timeout.getEndTime()

        // Then
        val expectedEndTime = startTime.toDate().time + duration
        assertEquals(expectedEndTime, endTime.toDate().time)
    }

    @Test
    fun `hasExpired should return true for expired timeout`() {
        // Given - timeout started 31 minutes ago
        val startTime = Timestamp(Date(System.currentTimeMillis() - (31 * 60 * 1000)))
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "connection_id",
            startTime = startTime,
            duration = Timeout.DEFAULT_DURATION_MS // 30 minutes
        )

        // When
        val hasExpired = timeout.hasExpired()

        // Then
        assertTrue(hasExpired)
    }

    @Test
    fun `hasExpired should return false for active timeout`() {
        // Given - timeout started 15 minutes ago
        val startTime = Timestamp(Date(System.currentTimeMillis() - (15 * 60 * 1000)))
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "connection_id",
            startTime = startTime,
            duration = Timeout.DEFAULT_DURATION_MS // 30 minutes
        )

        // When
        val hasExpired = timeout.hasExpired()

        // Then
        assertFalse(hasExpired)
    }

    @Test
    fun `getRemainingTimeMs should return correct remaining time`() {
        // Given - timeout started 10 minutes ago
        val startTime = Timestamp(Date(System.currentTimeMillis() - (10 * 60 * 1000)))
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "connection_id",
            startTime = startTime,
            duration = Timeout.DEFAULT_DURATION_MS // 30 minutes
        )

        // When
        val remainingTime = timeout.getRemainingTimeMs()

        // Then
        // Should be approximately 20 minutes remaining (allow for small timing differences)
        val expectedRemaining = 20 * 60 * 1000L
        assertTrue(remainingTime > expectedRemaining - 1000) // Within 1 second
        assertTrue(remainingTime <= expectedRemaining + 1000)
    }

    @Test
    fun `getRemainingTimeMs should return zero for expired timeout`() {
        // Given - timeout started 35 minutes ago
        val startTime = Timestamp(Date(System.currentTimeMillis() - (35 * 60 * 1000)))
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "connection_id",
            startTime = startTime,
            duration = Timeout.DEFAULT_DURATION_MS // 30 minutes
        )

        // When
        val remainingTime = timeout.getRemainingTimeMs()

        // Then
        assertEquals(0L, remainingTime)
    }

    @Test
    fun `getRemainingTimeMinutes should return correct remaining minutes`() {
        // Given - timeout started 10 minutes ago
        val startTime = Timestamp(Date(System.currentTimeMillis() - (10 * 60 * 1000)))
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "connection_id",
            startTime = startTime,
            duration = Timeout.DEFAULT_DURATION_MS // 30 minutes
        )

        // When
        val remainingMinutes = timeout.getRemainingTimeMinutes()

        // Then
        // Should be approximately 20 minutes remaining
        assertTrue(remainingMinutes >= 19 && remainingMinutes <= 20)
    }

    @Test
    fun `belongsTo should return true for correct user`() {
        // Given
        val userId = "test_user_id"
        val timeout = Timeout(
            id = "timeout_id",
            userId = userId,
            connectionId = "connection_id"
        )

        // When
        val belongsToUser = timeout.belongsTo(userId)

        // Then
        assertTrue(belongsToUser)
    }

    @Test
    fun `belongsTo should return false for different user`() {
        // Given
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user1",
            connectionId = "connection_id"
        )

        // When
        val belongsToUser = timeout.belongsTo("user2")

        // Then
        assertFalse(belongsToUser)
    }

    @Test
    fun `isForToday should return true for today's date`() {
        // Given
        val todayString = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "connection_id",
            createdDate = todayString
        )

        // When
        val isForToday = timeout.isForToday()

        // Then
        assertTrue(isForToday)
    }

    @Test
    fun `isForToday should return false for different date`() {
        // Given
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "connection_id",
            createdDate = "2023-01-01"
        )

        // When
        val isForToday = timeout.isForToday()

        // Then
        assertFalse(isForToday)
    }

    @Test
    fun `markAsExpired should return timeout with isActive false`() {
        // Given
        val timeout = Timeout(
            id = "timeout_id",
            userId = "user_id",
            connectionId = "connection_id",
            isActive = true
        )

        // When
        val expiredTimeout = timeout.markAsExpired()

        // Then
        assertFalse(expiredTimeout.isActive)
        assertEquals(timeout.id, expiredTimeout.id)
        assertEquals(timeout.userId, expiredTimeout.userId)
        assertEquals(timeout.connectionId, expiredTimeout.connectionId)
    }

    @Test
    fun `default values should be correct`() {
        // Given
        val timeout = Timeout()

        // When & Then
        assertEquals("", timeout.id)
        assertEquals("", timeout.userId)
        assertEquals("", timeout.connectionId)
        assertEquals(Timeout.DEFAULT_DURATION_MS, timeout.duration)
        assertTrue(timeout.isActive)
        assertNotNull(timeout.startTime)
        assertNotNull(timeout.createdDate)
    }

    @Test
    fun `constants should have correct values`() {
        // Then
        assertEquals(30 * 60 * 1000L, Timeout.DEFAULT_DURATION_MS)
        assertEquals(1, Timeout.MAX_TIMEOUTS_PER_DAY)
    }
}