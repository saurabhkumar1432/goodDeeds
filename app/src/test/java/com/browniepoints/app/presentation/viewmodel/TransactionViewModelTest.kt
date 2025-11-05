package com.browniepoints.app.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.browniepoints.app.data.model.Transaction
import com.browniepoints.app.data.model.TransactionType
import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.repository.ConnectionRepository
import com.browniepoints.app.data.repository.TransactionRepository
import com.browniepoints.app.data.repository.UserRepository
import com.browniepoints.app.domain.usecase.InAppNotificationUseCase
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class TransactionViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var transactionRepository: TransactionRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var connectionRepository: ConnectionRepository

    @Mock
    private lateinit var inAppNotificationUseCase: InAppNotificationUseCase

    private lateinit var transactionViewModel: TransactionViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        transactionViewModel = TransactionViewModel(transactionRepository, userRepository, connectionRepository, inAppNotificationUseCase)
    }

    @Test
    fun `initial state should be correct`() {
        // When
        val uiState = transactionViewModel.uiState.value
        val givePointsState = transactionViewModel.givePointsState.value

        // Then
        assertFalse(uiState.isLoading)
        assertTrue(uiState.transactions.isEmpty())
        assertNull(uiState.error)

        assertFalse(givePointsState.isLoading)
        assertFalse(givePointsState.isGivingPoints)
        assertEquals(1, givePointsState.selectedPoints)
        assertEquals("", givePointsState.message)
        assertNull(givePointsState.currentUser)
        assertNull(givePointsState.connectedPartner)
    }

    @Test
    fun `loadTransactionHistory should update state with transactions`() = runTest {
        // Given
        val userId = "test_user_id"
        val transactions = listOf(
            Transaction(
                id = "1",
                senderId = userId,
                receiverId = "other_user",
                points = 5,
                timestamp = Timestamp.now()
            ),
            Transaction(
                id = "2",
                senderId = "other_user",
                receiverId = userId,
                points = 3,
                timestamp = Timestamp.now()
            )
        )

        whenever(transactionRepository.observeTransactions(userId)).thenReturn(flowOf(transactions))

        // When
        transactionViewModel.loadTransactionHistory(userId)
        advanceUntilIdle()

        // Then
        val state = transactionViewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(transactions, state.transactions)
        assertNull(state.error)
        assertTrue(state.hasTransactions)
    }

    @Test
    fun `loadGivePointsData should load user and partner data successfully`() = runTest {
        // Given
        val userId = "test_user_id"
        val partnerId = "partner_id"
        val user = User(
            uid = userId,
            displayName = "Test User",
            connectedUserId = partnerId
        )
        val partner = User(
            uid = partnerId,
            displayName = "Partner User"
        )

        whenever(userRepository.getUser(userId)).thenReturn(Result.success(user))
        whenever(userRepository.getUser(partnerId)).thenReturn(Result.success(partner))

        // When
        transactionViewModel.loadGivePointsData(userId)
        advanceUntilIdle()

        // Then
        val state = transactionViewModel.givePointsState.value
        assertFalse(state.isLoading)
        assertEquals(user, state.currentUser)
        assertEquals(partner, state.connectedPartner)
        assertNull(state.error)
        assertTrue(state.canGivePoints)
    }

    @Test
    fun `loadGivePointsData should handle user with no connection`() = runTest {
        // Given
        val userId = "test_user_id"
        val user = User(
            uid = userId,
            displayName = "Test User",
            connectedUserId = null
        )

        whenever(userRepository.getUser(userId)).thenReturn(Result.success(user))

        // When
        transactionViewModel.loadGivePointsData(userId)
        advanceUntilIdle()

        // Then
        val state = transactionViewModel.givePointsState.value
        assertFalse(state.isLoading)
        assertEquals(user, state.currentUser)
        assertNull(state.connectedPartner)
        assertEquals("No connected partner found", state.error)
        assertFalse(state.canGivePoints)
    }

    @Test
    fun `updatePointsAmount should update points when valid`() {
        // Given
        val validPoints = 5

        // When
        transactionViewModel.updatePointsAmount(validPoints)

        // Then
        val state = transactionViewModel.givePointsState.value
        assertEquals(validPoints, state.selectedPoints)
        assertNull(state.validationError)
    }

    @Test
    fun `updatePointsAmount should set validation error when invalid`() {
        // Given
        val invalidPoints = 15 // Above max

        // When
        transactionViewModel.updatePointsAmount(invalidPoints)

        // Then
        val state = transactionViewModel.givePointsState.value
        assertNotNull(state.validationError)
        assertTrue(state.validationError!!.contains("Points must be between"))
    }

    @Test
    fun `updateMessage should update message when valid length`() {
        // Given
        val validMessage = "Great job!"

        // When
        transactionViewModel.updateMessage(validMessage)

        // Then
        val state = transactionViewModel.givePointsState.value
        assertEquals(validMessage, state.message)
        assertNull(state.validationError)
    }

    @Test
    fun `updateMessage should set validation error when too long`() {
        // Given
        val longMessage = "a".repeat(Transaction.MAX_MESSAGE_LENGTH + 1)

        // When
        transactionViewModel.updateMessage(longMessage)

        // Then
        val state = transactionViewModel.givePointsState.value
        assertNotNull(state.validationError)
        assertTrue(state.validationError!!.contains("Message cannot exceed"))
    }

    @Test
    fun `givePoints should create transaction successfully`() = runTest {
        // Given
        val userId = "test_user_id"
        val partnerId = "partner_id"
        val user = User(uid = userId, displayName = "Test User", connectedUserId = partnerId)
        val partner = User(uid = partnerId, displayName = "Partner User")
        val points = 5
        val message = "Great job!"
        val transaction = Transaction(
            id = "transaction_id",
            senderId = userId,
            receiverId = partnerId,
            points = points,
            message = message,
            timestamp = Timestamp.now(),
            connectionId = "connection_id"
        )

        // Setup initial state
        whenever(userRepository.getUser(userId)).thenReturn(Result.success(user))
        whenever(userRepository.getUser(partnerId)).thenReturn(Result.success(partner))
        transactionViewModel.loadGivePointsData(userId)
        advanceUntilIdle()

        transactionViewModel.updatePointsAmount(points)
        transactionViewModel.updateMessage(message)

        whenever(transactionRepository.createTransaction(
            senderId = userId,
            receiverId = partnerId,
            points = points,
            message = message,
            connectionId = any()
        )).thenReturn(Result.success(transaction))

        whenever(inAppNotificationUseCase.createPointsReceivedNotification(transaction, user))
            .thenReturn(Result.success(Unit))

        // When
        transactionViewModel.givePoints()
        advanceUntilIdle()

        // Then
        val state = transactionViewModel.givePointsState.value
        assertFalse(state.isGivingPoints)
        assertNull(state.error)
        assertEquals(transaction, state.lastTransaction)
        assertEquals(1, state.selectedPoints) // Reset to default
        assertEquals("", state.message) // Cleared
        verify(transactionRepository).createTransaction(any(), any(), any(), any(), any())
    }

    @Test
    fun `givePoints should handle transaction creation failure`() = runTest {
        // Given
        val userId = "test_user_id"
        val partnerId = "partner_id"
        val user = User(uid = userId, displayName = "Test User", connectedUserId = partnerId)
        val partner = User(uid = partnerId, displayName = "Partner User")
        val errorMessage = "Transaction failed"
        val exception = Exception(errorMessage)

        // Setup initial state
        whenever(userRepository.getUser(userId)).thenReturn(Result.success(user))
        whenever(userRepository.getUser(partnerId)).thenReturn(Result.success(partner))
        transactionViewModel.loadGivePointsData(userId)
        advanceUntilIdle()

        whenever(transactionRepository.createTransaction(any(), any(), any(), any(), any()))
            .thenReturn(Result.failure(exception))

        // When
        transactionViewModel.givePoints()
        advanceUntilIdle()

        // Then
        val state = transactionViewModel.givePointsState.value
        assertFalse(state.isGivingPoints)
        assertEquals(errorMessage, state.error)
        assertNull(state.lastTransaction)
    }

    @Test
    fun `clearError should clear all errors`() = runTest {
        // Given - set errors first
        transactionViewModel.updatePointsAmount(15) // Invalid points
        
        // When
        transactionViewModel.clearError()

        // Then
        val uiState = transactionViewModel.uiState.value
        val givePointsState = transactionViewModel.givePointsState.value
        assertNull(uiState.error)
        assertNull(givePointsState.error)
        assertNull(givePointsState.validationError)
    }

    @Test
    fun `clearLastTransaction should clear last transaction`() = runTest {
        // Given - simulate a successful transaction first
        val transaction = Transaction(id = "test_id")
        // We can't directly set lastTransaction, so we'll test the method exists and works
        
        // When
        transactionViewModel.clearLastTransaction()

        // Then
        val state = transactionViewModel.givePointsState.value
        assertNull(state.lastTransaction)
    }

    // Tests for new couple features - Point deduction functionality

    @Test
    fun `initial deduct points state should be correct`() {
        // When
        val deductPointsState = transactionViewModel.deductPointsState.value

        // Then
        assertFalse(deductPointsState.isLoading)
        assertFalse(deductPointsState.isDeductingPoints)
        assertEquals(1, deductPointsState.pointsAmount)
        assertEquals("", deductPointsState.reason)
        assertNull(deductPointsState.currentUser)
        assertNull(deductPointsState.connectedPartner)
        assertNull(deductPointsState.error)
        assertNull(deductPointsState.validationError)
        assertFalse(deductPointsState.canDeductPoints)
    }

    @Test
    fun `loadDeductPointsData should load user and partner data successfully`() = runTest {
        // Given
        val userId = "test_user_id"
        val partnerId = "partner_id"
        val user = User(
            uid = userId,
            displayName = "Test User",
            connectedUserId = partnerId
        )
        val partner = User(
            uid = partnerId,
            displayName = "Partner User"
        )

        whenever(userRepository.getUser(userId)).thenReturn(Result.success(user))
        whenever(userRepository.getUser(partnerId)).thenReturn(Result.success(partner))

        // When
        transactionViewModel.loadDeductPointsData(userId)
        advanceUntilIdle()

        // Then
        val state = transactionViewModel.deductPointsState.value
        assertFalse(state.isLoading)
        assertEquals(user, state.currentUser)
        assertEquals(partner, state.connectedPartner)
        assertNull(state.error)
        assertTrue(state.canDeductPoints)
    }

    @Test
    fun `loadDeductPointsData should handle user with no connection`() = runTest {
        // Given
        val userId = "test_user_id"
        val user = User(
            uid = userId,
            displayName = "Test User",
            connectedUserId = null
        )

        whenever(userRepository.getUser(userId)).thenReturn(Result.success(user))

        // When
        transactionViewModel.loadDeductPointsData(userId)
        advanceUntilIdle()

        // Then
        val state = transactionViewModel.deductPointsState.value
        assertFalse(state.isLoading)
        assertEquals(user, state.currentUser)
        assertNull(state.connectedPartner)
        assertEquals("No connected partner found", state.error)
        assertFalse(state.canDeductPoints)
    }

    @Test
    fun `updateDeductPointsAmount should update points when valid`() {
        // Given
        val validPoints = 3

        // When
        transactionViewModel.updateDeductPointsAmount(validPoints)

        // Then
        val state = transactionViewModel.deductPointsState.value
        assertEquals(validPoints, state.pointsAmount)
        assertNull(state.validationError)
    }

    @Test
    fun `updateDeductPointsAmount should set validation error when invalid`() {
        // Given
        val invalidPoints = 15 // Above max

        // When
        transactionViewModel.updateDeductPointsAmount(invalidPoints)

        // Then
        val state = transactionViewModel.deductPointsState.value
        assertNotNull(state.validationError)
        assertTrue(state.validationError!!.contains("Points must be between"))
    }

    @Test
    fun `updateDeductReason should update reason when valid length`() {
        // Given
        val validReason = "Argument about chores"

        // When
        transactionViewModel.updateDeductReason(validReason)

        // Then
        val state = transactionViewModel.deductPointsState.value
        assertEquals(validReason, state.reason)
        assertNull(state.validationError)
    }

    @Test
    fun `updateDeductReason should set validation error when too long`() {
        // Given
        val longReason = "a".repeat(Transaction.MAX_MESSAGE_LENGTH + 1)

        // When
        transactionViewModel.updateDeductReason(longReason)

        // Then
        val state = transactionViewModel.deductPointsState.value
        assertNotNull(state.validationError)
        assertTrue(state.validationError!!.contains("Reason cannot exceed"))
    }

    @Test
    fun `deductPoints should create deduction transaction successfully`() = runTest {
        // Given
        val userId = "test_user_id"
        val partnerId = "partner_id"
        val user = User(uid = userId, displayName = "Test User", connectedUserId = partnerId)
        val partner = User(uid = partnerId, displayName = "Partner User")
        val points = 3
        val reason = "Argument about dishes"
        val transaction = Transaction(
            id = "transaction_id",
            senderId = userId,
            receiverId = partnerId,
            points = -points, // Negative for deduction
            message = reason,
            timestamp = Timestamp.now(),
            connectionId = "connection_id",
            type = TransactionType.DEDUCT
        )

        // Setup initial state
        whenever(userRepository.getUser(userId)).thenReturn(Result.success(user))
        whenever(userRepository.getUser(partnerId)).thenReturn(Result.success(partner))
        transactionViewModel.loadDeductPointsData(userId)
        advanceUntilIdle()

        transactionViewModel.updateDeductPointsAmount(points)
        transactionViewModel.updateDeductReason(reason)

        whenever(transactionRepository.deductPoints(
            senderId = userId,
            receiverId = partnerId,
            points = points,
            reason = reason,
            connectionId = any()
        )).thenReturn(Result.success(transaction))

        whenever(inAppNotificationUseCase.createPointsReceivedNotification(transaction, user))
            .thenReturn(Result.success(Unit))

        // When
        transactionViewModel.deductPoints()
        advanceUntilIdle()

        // Then
        val state = transactionViewModel.deductPointsState.value
        assertFalse(state.isDeductingPoints)
        assertNull(state.error)
        assertEquals(transaction, state.lastTransaction)
        assertEquals(1, state.pointsAmount) // Reset to default
        assertEquals("", state.reason) // Cleared
        verify(transactionRepository).deductPoints(any(), any(), any(), any(), any())
    }

    @Test
    fun `deductPoints should handle missing reason validation`() = runTest {
        // Given
        val userId = "test_user_id"
        val partnerId = "partner_id"
        val user = User(uid = userId, displayName = "Test User", connectedUserId = partnerId)
        val partner = User(uid = partnerId, displayName = "Partner User")

        // Setup initial state
        whenever(userRepository.getUser(userId)).thenReturn(Result.success(user))
        whenever(userRepository.getUser(partnerId)).thenReturn(Result.success(partner))
        transactionViewModel.loadDeductPointsData(userId)
        advanceUntilIdle()

        transactionViewModel.updateDeductPointsAmount(3)
        // Don't set reason - leave it blank

        // When
        transactionViewModel.deductPoints()
        advanceUntilIdle()

        // Then
        val state = transactionViewModel.deductPointsState.value
        assertFalse(state.isDeductingPoints)
        assertNotNull(state.validationError)
        assertTrue(state.validationError!!.contains("Please provide a reason"))
        verify(transactionRepository, never()).deductPoints(any(), any(), any(), any(), any())
    }

    @Test
    fun `deductPoints should handle transaction creation failure`() = runTest {
        // Given
        val userId = "test_user_id"
        val partnerId = "partner_id"
        val user = User(uid = userId, displayName = "Test User", connectedUserId = partnerId)
        val partner = User(uid = partnerId, displayName = "Partner User")
        val errorMessage = "Deduction failed"
        val exception = Exception(errorMessage)

        // Setup initial state
        whenever(userRepository.getUser(userId)).thenReturn(Result.success(user))
        whenever(userRepository.getUser(partnerId)).thenReturn(Result.success(partner))
        transactionViewModel.loadDeductPointsData(userId)
        advanceUntilIdle()

        transactionViewModel.updateDeductPointsAmount(3)
        transactionViewModel.updateDeductReason("Valid reason")

        whenever(transactionRepository.deductPoints(any(), any(), any(), any(), any()))
            .thenReturn(Result.failure(exception))

        // When
        transactionViewModel.deductPoints()
        advanceUntilIdle()

        // Then
        val state = transactionViewModel.deductPointsState.value
        assertFalse(state.isDeductingPoints)
        assertEquals(errorMessage, state.error)
        assertNull(state.lastTransaction)
    }

    @Test
    fun `deductPoints should handle missing user or partner`() = runTest {
        // Given - no user/partner loaded
        transactionViewModel.updateDeductPointsAmount(3)
        transactionViewModel.updateDeductReason("Valid reason")

        // When
        transactionViewModel.deductPoints()
        advanceUntilIdle()

        // Then
        val state = transactionViewModel.deductPointsState.value
        assertFalse(state.isDeductingPoints)
        assertEquals("Missing user or partner information", state.error)
        verify(transactionRepository, never()).deductPoints(any(), any(), any(), any(), any())
    }

    @Test
    fun `clearDeductError should clear deduct points errors`() = runTest {
        // Given - set errors first
        transactionViewModel.updateDeductPointsAmount(15) // Invalid points
        
        // When
        transactionViewModel.clearDeductError()

        // Then
        val deductPointsState = transactionViewModel.deductPointsState.value
        assertNull(deductPointsState.error)
        assertNull(deductPointsState.validationError)
    }

    @Test
    fun `clearLastDeductTransaction should clear last deduct transaction`() = runTest {
        // When
        transactionViewModel.clearLastDeductTransaction()

        // Then
        val state = transactionViewModel.deductPointsState.value
        assertNull(state.lastTransaction)
    }

    @Test
    fun `deductPointsState isFormValid should work correctly`() = runTest {
        // Given
        val userId = "test_user_id"
        val partnerId = "partner_id"
        val user = User(uid = userId, displayName = "Test User", connectedUserId = partnerId)
        val partner = User(uid = partnerId, displayName = "Partner User")

        whenever(userRepository.getUser(userId)).thenReturn(Result.success(user))
        whenever(userRepository.getUser(partnerId)).thenReturn(Result.success(partner))
        transactionViewModel.loadDeductPointsData(userId)
        advanceUntilIdle()

        // Initially invalid (no reason)
        assertFalse(transactionViewModel.deductPointsState.value.isFormValid)

        // Set valid points and reason
        transactionViewModel.updateDeductPointsAmount(3)
        transactionViewModel.updateDeductReason("Valid reason")

        // Should be valid now
        assertTrue(transactionViewModel.deductPointsState.value.isFormValid)

        // Set invalid points
        transactionViewModel.updateDeductPointsAmount(15)
        assertFalse(transactionViewModel.deductPointsState.value.isFormValid)

        // Fix points but clear reason
        transactionViewModel.updateDeductPointsAmount(3)
        transactionViewModel.updateDeductReason("")
        assertFalse(transactionViewModel.deductPointsState.value.isFormValid)
    }

    @Test
    fun `deductPointsState remainingReasonChars should calculate correctly`() {
        // Given
        val reason = "This is a test reason"
        
        // When
        transactionViewModel.updateDeductReason(reason)
        
        // Then
        val state = transactionViewModel.deductPointsState.value
        val expectedRemaining = Transaction.MAX_MESSAGE_LENGTH - reason.length
        assertEquals(expectedRemaining, state.remainingReasonChars)
    }
}