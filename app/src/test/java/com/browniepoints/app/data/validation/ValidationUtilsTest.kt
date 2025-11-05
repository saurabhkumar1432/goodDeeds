package com.browniepoints.app.data.validation

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ValidationUtils
 * Tests core validation logic for data integrity
 */
class ValidationUtilsTest {
    
    @Test
    fun `validatePointAmount should accept valid range`() {
        // Test valid point amounts (1-10)
        for (points in 1..10) {
            val result = ValidationUtils.validatePointAmount(points)
            assertTrue("Points $points should be valid", result.isSuccess)
        }
    }
    
    @Test
    fun `validatePointAmount should reject invalid range`() {
        // Test invalid point amounts
        val invalidAmounts = listOf(0, -1, 11, 100)
        
        invalidAmounts.forEach { points ->
            val result = ValidationUtils.validatePointAmount(points)
            assertTrue("Points $points should be invalid", result.isError)
        }
    }
    
    @Test
    fun `validateDeductionAmount should accept valid negative range`() {
        // Test valid deduction amounts (-10 to -1)
        for (points in -10..-1) {
            val result = ValidationUtils.validateDeductionAmount(points)
            assertTrue("Deduction points $points should be valid", result.isSuccess)
        }
    }
    
    @Test
    fun `validateDeductionAmount should reject invalid range`() {
        // Test invalid deduction amounts
        val invalidAmounts = listOf(0, 1, -11, -100)
        
        invalidAmounts.forEach { points ->
            val result = ValidationUtils.validateDeductionAmount(points)
            assertTrue("Deduction points $points should be invalid", result.isError)
        }
    }
    
    @Test
    fun `validateTransactionPoints should work for both types`() {
        // Test giving points
        val giveResult = ValidationUtils.validateTransactionPoints(5, false)
        assertTrue("Give transaction should be valid", giveResult.isSuccess)
        
        // Test deducting points
        val deductResult = ValidationUtils.validateTransactionPoints(-5, true)
        assertTrue("Deduct transaction should be valid", deductResult.isSuccess)
        
        // Test invalid combinations
        val invalidGive = ValidationUtils.validateTransactionPoints(-5, false)
        assertTrue("Negative points for give should be invalid", invalidGive.isError)
        
        val invalidDeduct = ValidationUtils.validateTransactionPoints(5, true)
        assertTrue("Positive points for deduct should be invalid", invalidDeduct.isError)
    }
    
    @Test
    fun `validateMatchingCode should accept valid format`() {
        val validCodes = listOf("ABC123", "XYZ789", "123456", "ABCDEF")
        
        validCodes.forEach { code ->
            val result = ValidationUtils.validateMatchingCode(code)
            assertTrue("Code $code should be valid", result.isSuccess)
        }
    }
    
    @Test
    fun `validateMatchingCode should reject invalid format`() {
        val invalidCodes = listOf(
            "abc123", // lowercase
            "ABC12",  // too short
            "ABC1234", // too long
            "ABC-123", // special characters
            "", // empty
            "   " // whitespace only
        )
        
        invalidCodes.forEach { code ->
            val result = ValidationUtils.validateMatchingCode(code)
            assertTrue("Code '$code' should be invalid", result.isError)
        }
    }
    
    @Test
    fun `validateMessage should accept valid messages`() {
        val validMessages = listOf(
            null, // null is valid (optional)
            "", // empty is valid (optional)
            "Great job!",
            "Thanks for helping with dinner",
            "A".repeat(200) // exactly 200 characters
        )
        
        validMessages.forEach { message ->
            val result = ValidationUtils.validateMessage(message)
            assertTrue("Message should be valid: '$message'", result.isSuccess)
        }
    }
    
    @Test
    fun `validateMessage should reject invalid messages`() {
        val tooLongMessage = "A".repeat(201) // 201 characters
        val result = ValidationUtils.validateMessage(tooLongMessage)
        assertTrue("Message over 200 characters should be invalid", result.isError)
    }
    
    @Test
    fun `validateEmail should accept valid emails`() {
        val validEmails = listOf(
            "test@example.com",
            "user.name@domain.co.uk",
            "user+tag@example.org"
        )
        
        validEmails.forEach { email ->
            val result = ValidationUtils.validateEmail(email)
            assertTrue("Email $email should be valid", result.isSuccess)
        }
    }
    
    @Test
    fun `validateEmail should reject invalid emails`() {
        val invalidEmails = listOf(
            "",
            "invalid",
            "@example.com",
            "user@",
            "user space@example.com"
        )
        
        invalidEmails.forEach { email ->
            val result = ValidationUtils.validateEmail(email)
            assertTrue("Email '$email' should be invalid", result.isError)
        }
    }
    
    @Test
    fun `validateDifferentUsers should work correctly`() {
        // Valid different users
        val validResult = ValidationUtils.validateDifferentUsers("user1", "user2")
        assertTrue("Different users should be valid", validResult.isSuccess)
        
        // Invalid same users
        val invalidResult = ValidationUtils.validateDifferentUsers("user1", "user1")
        assertTrue("Same users should be invalid", invalidResult.isError)
        
        // Invalid null users
        val nullResult1 = ValidationUtils.validateDifferentUsers(null, "user2")
        assertTrue("Null user1 should be invalid", nullResult1.isError)
        
        val nullResult2 = ValidationUtils.validateDifferentUsers("user1", null)
        assertTrue("Null user2 should be invalid", nullResult2.isError)
    }
    
    @Test
    fun `validateTimeoutDuration should work correctly`() {
        // Valid durations
        val validDurations = listOf(
            1000L, // 1 second
            30 * 60 * 1000L, // 30 minutes
            60 * 60 * 1000L, // 1 hour
            12 * 60 * 60 * 1000L // 12 hours
        )
        
        validDurations.forEach { duration ->
            val result = ValidationUtils.validateTimeoutDuration(duration)
            assertTrue("Duration $duration should be valid", result.isSuccess)
        }
        
        // Invalid durations
        val invalidDurations = listOf(
            0L, // zero
            -1000L, // negative
            25 * 60 * 60 * 1000L // over 24 hours
        )
        
        invalidDurations.forEach { duration ->
            val result = ValidationUtils.validateTimeoutDuration(duration)
            assertTrue("Duration $duration should be invalid", result.isError)
        }
    }
    
    @Test
    fun `sanitizeMatchingCode should work correctly`() {
        val testCases = mapOf(
            "abc123" to "ABC123",
            "  XYZ789  " to "XYZ789",
            "ab-c1@23" to "ABC123",
            "xyz" to "XYZ"
        )
        
        testCases.forEach { (input, expected) ->
            val result = ValidationUtils.sanitizeMatchingCode(input)
            assertEquals("Sanitized code should match expected", expected, result)
        }
    }
    
    @Test
    fun `sanitizeText should work correctly`() {
        val testCases = mapOf(
            "  Hello World  " to "Hello World",
            "Test<script>" to "Testscript",
            "Message with \"quotes\"" to "Message with quotes",
            "A".repeat(250) to "A".repeat(200) // Should truncate to 200
        )
        
        testCases.forEach { (input, expected) ->
            val result = ValidationUtils.sanitizeText(input)
            assertEquals("Sanitized text should match expected", expected, result)
        }
    }
}