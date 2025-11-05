package com.browniepoints.app.data.model

import com.browniepoints.app.data.validation.ValidationResult
import com.google.firebase.Timestamp
import org.junit.Test
import org.junit.Assert.*

class TransactionTest {

    @Test
    fun `validate should return success for valid transaction`() {
        // Given
        val validTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = 5,
            message = "Great job!",
            timestamp = Timestamp.now(),
            connectionId = "connection_id"
        )

        // When
        val result = validTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Success)
    }

    @Test
    fun `validate should return error for empty transaction id`() {
        // Given
        val invalidTransaction = Transaction(
            id = "",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = 5,
            connectionId = "connection_id"
        )

        // When
        val result = invalidTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Transaction ID cannot be empty"))
    }

    @Test
    fun `validate should return error for empty sender id`() {
        // Given
        val invalidTransaction = Transaction(
            id = "transaction_id",
            senderId = "",
            receiverId = "receiver_id",
            points = 5,
            connectionId = "connection_id"
        )

        // When
        val result = invalidTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Sender ID cannot be empty"))
    }

    @Test
    fun `validate should return error for empty receiver id`() {
        // Given
        val invalidTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "",
            points = 5,
            connectionId = "connection_id"
        )

        // When
        val result = invalidTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Receiver ID cannot be empty"))
    }

    @Test
    fun `validate should return error when sender and receiver are the same`() {
        // Given
        val invalidTransaction = Transaction(
            id = "transaction_id",
            senderId = "same_user_id",
            receiverId = "same_user_id",
            points = 5,
            connectionId = "connection_id"
        )

        // When
        val result = invalidTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Sender and receiver cannot be the same user"))
    }

    @Test
    fun `validate should return error for points below minimum`() {
        // Given
        val invalidTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = 0, // Below minimum
            connectionId = "connection_id"
        )

        // When
        val result = invalidTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Points must be between ${Transaction.MIN_POINTS} and ${Transaction.MAX_POINTS}"))
    }

    @Test
    fun `validate should return error for points above maximum`() {
        // Given
        val invalidTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = 15, // Above maximum
            connectionId = "connection_id"
        )

        // When
        val result = invalidTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Points must be between ${Transaction.MIN_POINTS} and ${Transaction.MAX_POINTS}"))
    }

    @Test
    fun `validate should return error for message exceeding max length`() {
        // Given
        val longMessage = "a".repeat(Transaction.MAX_MESSAGE_LENGTH + 1)
        val invalidTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = 5,
            message = longMessage,
            connectionId = "connection_id"
        )

        // When
        val result = invalidTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Message cannot exceed ${Transaction.MAX_MESSAGE_LENGTH} characters"))
    }

    @Test
    fun `validate should return error for empty connection id`() {
        // Given
        val invalidTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = 5,
            connectionId = ""
        )

        // When
        val result = invalidTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Connection ID cannot be empty"))
    }

    @Test
    fun `validate should accept null message`() {
        // Given
        val validTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = 5,
            message = null,
            connectionId = "connection_id"
        )

        // When
        val result = validTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Success)
    }

    @Test
    fun `validate should accept empty message`() {
        // Given
        val validTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = 5,
            message = "",
            connectionId = "connection_id"
        )

        // When
        val result = validTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Success)
    }

    @Test
    fun `hasMessage should return true when message is not null or blank`() {
        // Given
        val transactionWithMessage = Transaction(
            id = "transaction_id",
            message = "Great job!"
        )

        // When
        val result = transactionWithMessage.hasMessage()

        // Then
        assertTrue(result)
    }

    @Test
    fun `hasMessage should return false when message is null`() {
        // Given
        val transactionWithoutMessage = Transaction(
            id = "transaction_id",
            message = null
        )

        // When
        val result = transactionWithoutMessage.hasMessage()

        // Then
        assertFalse(result)
    }

    @Test
    fun `hasMessage should return false when message is blank`() {
        // Given
        val transactionWithBlankMessage = Transaction(
            id = "transaction_id",
            message = "   "
        )

        // When
        val result = transactionWithBlankMessage.hasMessage()

        // Then
        assertFalse(result)
    }

    @Test
    fun `isSentBy should return true when user is sender`() {
        // Given
        val userId = "test_user_id"
        val transaction = Transaction(
            id = "transaction_id",
            senderId = userId,
            receiverId = "other_user_id"
        )

        // When
        val result = transaction.isSentBy(userId)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isSentBy should return false when user is not sender`() {
        // Given
        val userId = "test_user_id"
        val transaction = Transaction(
            id = "transaction_id",
            senderId = "other_user_id",
            receiverId = userId
        )

        // When
        val result = transaction.isSentBy(userId)

        // Then
        assertFalse(result)
    }

    @Test
    fun `isReceivedBy should return true when user is receiver`() {
        // Given
        val userId = "test_user_id"
        val transaction = Transaction(
            id = "transaction_id",
            senderId = "other_user_id",
            receiverId = userId
        )

        // When
        val result = transaction.isReceivedBy(userId)

        // Then
        assertTrue(result)
    }

    @Test
    fun `isReceivedBy should return false when user is not receiver`() {
        // Given
        val userId = "test_user_id"
        val transaction = Transaction(
            id = "transaction_id",
            senderId = userId,
            receiverId = "other_user_id"
        )

        // When
        val result = transaction.isReceivedBy(userId)

        // Then
        assertFalse(result)
    }

    // Tests for new couple features - Point deduction functionality

    @Test
    fun `validate should return success for valid deduction transaction`() {
        // Given
        val validDeductionTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = -3, // Negative for deduction
            message = "Argument about chores",
            timestamp = Timestamp.now(),
            connectionId = "connection_id",
            type = TransactionType.DEDUCT
        )

        // When
        val result = validDeductionTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Success)
    }

    @Test
    fun `validate should return error for deduction with positive points`() {
        // Given
        val invalidDeductionTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = 3, // Should be negative for deduction
            connectionId = "connection_id",
            type = TransactionType.DEDUCT
        )

        // When
        val result = invalidDeductionTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Points for deduction must be between -${Transaction.MIN_POINTS} and -${Transaction.MAX_POINTS}"))
    }

    @Test
    fun `validate should return error for deduction points below minimum`() {
        // Given
        val invalidDeductionTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = -15, // Below minimum (too negative)
            connectionId = "connection_id",
            type = TransactionType.DEDUCT
        )

        // When
        val result = invalidDeductionTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Points for deduction must be between -${Transaction.MIN_POINTS} and -${Transaction.MAX_POINTS}"))
    }

    @Test
    fun `validate should return error for give transaction with negative points`() {
        // Given
        val invalidGiveTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = -3, // Should be positive for giving
            connectionId = "connection_id",
            type = TransactionType.GIVE
        )

        // When
        val result = invalidGiveTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Error)
        val errors = (result as ValidationResult.Error).errors
        assertTrue(errors.contains("Points for giving must be between ${Transaction.MIN_POINTS} and ${Transaction.MAX_POINTS}"))
    }

    @Test
    fun `isPositive should return true for give transaction with positive points`() {
        // Given
        val giveTransaction = Transaction(
            id = "transaction_id",
            points = 5,
            type = TransactionType.GIVE
        )

        // When
        val result = giveTransaction.isPositive()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isPositive should return false for deduct transaction`() {
        // Given
        val deductTransaction = Transaction(
            id = "transaction_id",
            points = -3,
            type = TransactionType.DEDUCT
        )

        // When
        val result = deductTransaction.isPositive()

        // Then
        assertFalse(result)
    }

    @Test
    fun `isNegative should return true for deduct transaction with negative points`() {
        // Given
        val deductTransaction = Transaction(
            id = "transaction_id",
            points = -3,
            type = TransactionType.DEDUCT
        )

        // When
        val result = deductTransaction.isNegative()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isNegative should return false for give transaction`() {
        // Given
        val giveTransaction = Transaction(
            id = "transaction_id",
            points = 5,
            type = TransactionType.GIVE
        )

        // When
        val result = giveTransaction.isNegative()

        // Then
        assertFalse(result)
    }

    @Test
    fun `getAbsolutePoints should return absolute value for positive points`() {
        // Given
        val giveTransaction = Transaction(
            id = "transaction_id",
            points = 5
        )

        // When
        val result = giveTransaction.getAbsolutePoints()

        // Then
        assertEquals(5, result)
    }

    @Test
    fun `getAbsolutePoints should return absolute value for negative points`() {
        // Given
        val deductTransaction = Transaction(
            id = "transaction_id",
            points = -3
        )

        // When
        val result = deductTransaction.getAbsolutePoints()

        // Then
        assertEquals(3, result)
    }

    @Test
    fun `default transaction type should be GIVE`() {
        // Given
        val transaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = 5,
            connectionId = "connection_id"
        )

        // When & Then
        assertEquals(TransactionType.GIVE, transaction.type)
    }

    @Test
    fun `transaction with DEDUCT type should be valid with negative points`() {
        // Given
        val deductTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = -5,
            message = "Disagreement",
            connectionId = "connection_id",
            type = TransactionType.DEDUCT
        )

        // When
        val result = deductTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Success)
        assertTrue(deductTransaction.isNegative())
        assertEquals(5, deductTransaction.getAbsolutePoints())
    }

    @Test
    fun `transaction with GIVE type should be valid with positive points`() {
        // Given
        val giveTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = 5,
            message = "Great job!",
            connectionId = "connection_id",
            type = TransactionType.GIVE
        )

        // When
        val result = giveTransaction.validate()

        // Then
        assertTrue(result is ValidationResult.Success)
        assertTrue(giveTransaction.isPositive())
        assertEquals(5, giveTransaction.getAbsolutePoints())
    }

    @Test
    fun `validate should handle edge case deduction values correctly`() {
        // Test minimum valid deduction (-1)
        val minDeductTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = -Transaction.MIN_POINTS,
            connectionId = "connection_id",
            type = TransactionType.DEDUCT
        )

        val minResult = minDeductTransaction.validate()
        assertTrue(minResult is ValidationResult.Success)

        // Test maximum valid deduction (-10)
        val maxDeductTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = -Transaction.MAX_POINTS,
            connectionId = "connection_id",
            type = TransactionType.DEDUCT
        )

        val maxResult = maxDeductTransaction.validate()
        assertTrue(maxResult is ValidationResult.Success)
    }

    @Test
    fun `validate should handle edge case give values correctly`() {
        // Test minimum valid give (1)
        val minGiveTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = Transaction.MIN_POINTS,
            connectionId = "connection_id",
            type = TransactionType.GIVE
        )

        val minResult = minGiveTransaction.validate()
        assertTrue(minResult is ValidationResult.Success)

        // Test maximum valid give (10)
        val maxGiveTransaction = Transaction(
            id = "transaction_id",
            senderId = "sender_id",
            receiverId = "receiver_id",
            points = Transaction.MAX_POINTS,
            connectionId = "connection_id",
            type = TransactionType.GIVE
        )

        val maxResult = maxGiveTransaction.validate()
        assertTrue(maxResult is ValidationResult.Success)
    }
}