package com.browniepoints.app.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.browniepoints.app.data.model.Connection
import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.repository.ConnectionRepository
import com.browniepoints.app.data.repository.UserRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
class ConnectionViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var connectionRepository: ConnectionRepository

    @Mock
    private lateinit var firebaseAuth: FirebaseAuth

    @Mock
    private lateinit var firebaseUser: FirebaseUser

    private lateinit var connectionViewModel: ConnectionViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val testUserId = "test_user_id"

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        
        whenever(firebaseAuth.currentUser).thenReturn(firebaseUser)
        whenever(firebaseUser.uid).thenReturn(testUserId)
        whenever(connectionRepository.observeConnection(testUserId)).thenReturn(flowOf(null))
        // Mock suspend function properly
        runBlocking {
            whenever(userRepository.getUser(testUserId)).thenReturn(Result.success(null))
        }
        
        connectionViewModel = ConnectionViewModel(userRepository, connectionRepository, firebaseAuth)
    }

    @Test
    fun `initial state should be correct`() = runTest {
        // Given
        advanceUntilIdle()

        // When
        val state = connectionViewModel.uiState.value

        // Then
        assertNull(state.currentUser)
        assertNull(state.connectedPartner)
        assertNull(state.connection)
        assertEquals("", state.matchingCodeInput)
        assertFalse(state.isLoading)
        assertFalse(state.isConnecting)
        assertFalse(state.isConnected)
    }

    @Test
    fun `should load user data on initialization`() = runTest {
        // Given
        val user = User(
            uid = testUserId,
            displayName = "Test User",
            matchingCode = "ABC123"
        )
        whenever(userRepository.getUser(testUserId)).thenReturn(Result.success(user))

        // When
        val newViewModel = ConnectionViewModel(userRepository, connectionRepository, firebaseAuth)
        advanceUntilIdle()

        // Then
        val state = newViewModel.uiState.value
        assertEquals(user, state.currentUser)
        assertFalse(state.isLoading)
    }

    @Test
    fun `should load partner data when user is connected`() = runTest {
        // Given
        val partnerId = "partner_id"
        val user = User(
            uid = testUserId,
            displayName = "Test User",
            connectedUserId = partnerId
        )
        val partner = User(
            uid = partnerId,
            displayName = "Partner User"
        )

        whenever(userRepository.getUser(testUserId)).thenReturn(Result.success(user))
        whenever(userRepository.getUser(partnerId)).thenReturn(Result.success(partner))

        // When
        val newViewModel = ConnectionViewModel(userRepository, connectionRepository, firebaseAuth)
        advanceUntilIdle()

        // Then
        val state = newViewModel.uiState.value
        assertEquals(user, state.currentUser)
        assertEquals(partner, state.connectedPartner)
    }

    @Test
    fun `connectWithMatchingCode should validate empty code`() {
        // Given
        val emptyCode = ""

        // When
        connectionViewModel.connectWithMatchingCode(emptyCode)

        // Then
        val state = connectionViewModel.uiState.value
        assertEquals("Please enter a matching code", state.error)
        assertFalse(state.isConnecting)
    }

    @Test
    fun `connectWithMatchingCode should validate code length`() {
        // Given
        val shortCode = "ABC"

        // When
        connectionViewModel.connectWithMatchingCode(shortCode)

        // Then
        val state = connectionViewModel.uiState.value
        assertEquals("Matching code must be 6 characters", state.error)
        assertFalse(state.isConnecting)
    }

    @Test
    fun `connectWithMatchingCode should handle invalid matching code`() = runTest {
        // Given
        val invalidCode = "INVALID"
        whenever(connectionRepository.validateMatchingCode(invalidCode))
            .thenReturn(Result.success(null))

        // When
        connectionViewModel.connectWithMatchingCode(invalidCode)
        advanceUntilIdle()

        // Then
        val state = connectionViewModel.uiState.value
        assertEquals("Invalid matching code. Please check and try again.", state.error)
        assertFalse(state.isConnecting)
    }

    @Test
    fun `connectWithMatchingCode should prevent self-connection`() = runTest {
        // Given
        val matchingCode = "ABC123"
        whenever(connectionRepository.validateMatchingCode(matchingCode))
            .thenReturn(Result.success(testUserId)) // Returns own user ID

        // When
        connectionViewModel.connectWithMatchingCode(matchingCode)
        advanceUntilIdle()

        // Then
        val state = connectionViewModel.uiState.value
        assertEquals("You cannot connect to yourself", state.error)
        assertFalse(state.isConnecting)
    }

    @Test
    fun `connectWithMatchingCode should handle already connected partner`() = runTest {
        // Given
        val matchingCode = "ABC123"
        val partnerId = "partner_id"
        val connectedPartner = User(
            uid = partnerId,
            displayName = "Connected Partner",
            connectedUserId = "someone_else"
        )

        whenever(connectionRepository.validateMatchingCode(matchingCode))
            .thenReturn(Result.success(partnerId))
        whenever(userRepository.getUser(partnerId))
            .thenReturn(Result.success(connectedPartner))

        // When
        connectionViewModel.connectWithMatchingCode(matchingCode)
        advanceUntilIdle()

        // Then
        val state = connectionViewModel.uiState.value
        assertEquals("This user is already connected to someone else", state.error)
        assertFalse(state.isConnecting)
    }

    @Test
    fun `connectWithMatchingCode should create connection successfully`() = runTest {
        // Given
        val matchingCode = "ABC123"
        val partnerId = "partner_id"
        val partner = User(
            uid = partnerId,
            displayName = "Partner User",
            connectedUserId = null
        )
        val connection = Connection(
            id = "connection_id",
            user1Id = testUserId,
            user2Id = partnerId,
            createdAt = Timestamp.now()
        )

        whenever(connectionRepository.validateMatchingCode(matchingCode))
            .thenReturn(Result.success(partnerId))
        whenever(userRepository.getUser(partnerId))
            .thenReturn(Result.success(partner))
        whenever(connectionRepository.createConnection(testUserId, partnerId))
            .thenReturn(Result.success(connection))
        whenever(userRepository.getUser(testUserId))
            .thenReturn(Result.success(User(uid = testUserId, connectedUserId = partnerId)))

        // When
        connectionViewModel.connectWithMatchingCode(matchingCode)
        advanceUntilIdle()

        // Then
        val state = connectionViewModel.uiState.value
        assertEquals(connection, state.connection)
        assertFalse(state.isConnecting)
        assertNull(state.error)
        verify(connectionRepository).createConnection(testUserId, partnerId)
    }

    @Test
    fun `connectWithMatchingCode should handle connection creation failure`() = runTest {
        // Given
        val matchingCode = "ABC123"
        val partnerId = "partner_id"
        val partner = User(uid = partnerId, connectedUserId = null)
        val errorMessage = "Connection failed"
        val exception = Exception(errorMessage)

        whenever(connectionRepository.validateMatchingCode(matchingCode))
            .thenReturn(Result.success(partnerId))
        whenever(userRepository.getUser(partnerId))
            .thenReturn(Result.success(partner))
        whenever(connectionRepository.createConnection(testUserId, partnerId))
            .thenReturn(Result.failure(exception))

        // When
        connectionViewModel.connectWithMatchingCode(matchingCode)
        advanceUntilIdle()

        // Then
        val state = connectionViewModel.uiState.value
        assertEquals(errorMessage, state.error)
        assertFalse(state.isConnecting)
    }

    @Test
    fun `clearError should clear error from state`() {
        // Given - set an error first
        connectionViewModel.connectWithMatchingCode("")
        assertNotNull(connectionViewModel.uiState.value.error)

        // When
        connectionViewModel.clearError()

        // Then
        assertNull(connectionViewModel.uiState.value.error)
    }

    @Test
    fun `updateMatchingCodeInput should filter and limit input`() {
        // Given
        val input = "abc123def@#$"
        val expectedOutput = "ABC123" // Should be uppercase, alphanumeric only, max 6 chars

        // When
        connectionViewModel.updateMatchingCodeInput(input)

        // Then
        val state = connectionViewModel.uiState.value
        assertEquals(expectedOutput, state.matchingCodeInput)
    }

    @Test
    fun `updateMatchingCodeInput should handle empty input`() {
        // Given
        val input = ""

        // When
        connectionViewModel.updateMatchingCodeInput(input)

        // Then
        val state = connectionViewModel.uiState.value
        assertEquals("", state.matchingCodeInput)
    }

    @Test
    fun `updateMatchingCodeInput should limit to 6 characters`() {
        // Given
        val input = "ABCDEFGHIJ" // 10 characters

        // When
        connectionViewModel.updateMatchingCodeInput(input)

        // Then
        val state = connectionViewModel.uiState.value
        assertEquals("ABCDEF", state.matchingCodeInput) // Should be limited to 6
    }

    @Test
    fun `should observe connection changes`() = runTest {
        // Given
        val connection = Connection(
            id = "connection_id",
            user1Id = testUserId,
            user2Id = "partner_id"
        )
        whenever(connectionRepository.observeConnection(testUserId))
            .thenReturn(flowOf(connection))

        // When
        val newViewModel = ConnectionViewModel(userRepository, connectionRepository, firebaseAuth)
        advanceUntilIdle()

        // Then
        val state = newViewModel.uiState.value
        assertEquals(connection, state.connection)
    }
}