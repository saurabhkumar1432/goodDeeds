package com.browniepoints.app.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.repository.AuthRepository
import com.browniepoints.app.data.service.GoogleSignInService
import com.browniepoints.app.domain.usecase.NotificationUseCase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
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
class AuthViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var authRepository: AuthRepository

    @Mock
    private lateinit var googleSignInService: GoogleSignInService

    @Mock
    private lateinit var notificationUseCase: NotificationUseCase

    @Mock
    private lateinit var firebaseUser: FirebaseUser

    private lateinit var authViewModel: AuthViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        
        // Mock repository flows
        whenever(authRepository.currentUser).thenReturn(flowOf(null))
        whenever(authRepository.isSignedIn).thenReturn(flowOf(false))
        
        authViewModel = AuthViewModel(authRepository, googleSignInService, notificationUseCase)
    }

    @Test
    fun `initial state should be correct`() = runTest {
        // Given
        advanceUntilIdle()

        // When
        val state = authViewModel.uiState.value

        // Then
        assertFalse(state.isLoading)
        assertFalse(state.isSignedIn)
        assertNull(state.currentUser)
        assertNull(state.userProfile)
        assertNull(state.error)
    }

    @Test
    fun `signInWithGoogle should update state to loading and then success`() = runTest {
        // Given
        val idToken = "test_id_token"
        val user = User(
            uid = "test_user_id",
            displayName = "Test User",
            email = "test@example.com",
            matchingCode = "ABC123",
            createdAt = Timestamp.now()
        )

        whenever(authRepository.signInWithGoogle(idToken)).thenReturn(Result.success(user))
        whenever(notificationUseCase.initializeFcmToken()).thenReturn(Result.success(Unit))

        // When
        authViewModel.signInWithGoogle(idToken)
        advanceUntilIdle()

        // Then
        val state = authViewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(user, state.userProfile)
        verify(authRepository).signInWithGoogle(idToken)
        verify(notificationUseCase).initializeFcmToken()
    }

    @Test
    fun `signInWithGoogle should update state to error when authentication fails`() = runTest {
        // Given
        val idToken = "invalid_token"
        val errorMessage = "Authentication failed"
        val exception = Exception(errorMessage)

        whenever(authRepository.signInWithGoogle(idToken)).thenReturn(Result.failure(exception))

        // When
        authViewModel.signInWithGoogle(idToken)
        advanceUntilIdle()

        // Then
        val state = authViewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(errorMessage, state.error)
        assertNull(state.userProfile)
        verify(authRepository).signInWithGoogle(idToken)
        verify(notificationUseCase, never()).initializeFcmToken()
    }

    @Test
    fun `signOut should update state correctly on success`() = runTest {
        // Given
        whenever(authRepository.signOut()).thenReturn(Result.success(Unit))

        // When
        authViewModel.signOut()
        advanceUntilIdle()

        // Then
        val state = authViewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNull(state.userProfile)
        verify(authRepository).signOut()
    }

    @Test
    fun `signOut should update state to error when sign out fails`() = runTest {
        // Given
        val errorMessage = "Sign-out failed"
        val exception = Exception(errorMessage)
        whenever(authRepository.signOut()).thenReturn(Result.failure(exception))

        // When
        authViewModel.signOut()
        advanceUntilIdle()

        // Then
        val state = authViewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(errorMessage, state.error)
        verify(authRepository).signOut()
    }

    @Test
    fun `clearError should clear error from state`() = runTest {
        // Given - set an error first
        val idToken = "invalid_token"
        val exception = Exception("Test error")
        whenever(authRepository.signInWithGoogle(idToken)).thenReturn(Result.failure(exception))
        
        authViewModel.signInWithGoogle(idToken)
        advanceUntilIdle()
        
        // Verify error is set
        assertNotNull(authViewModel.uiState.value.error)

        // When
        authViewModel.clearError()

        // Then
        assertNull(authViewModel.uiState.value.error)
    }

    @Test
    fun `getCurrentUser should return current Firebase user`() {
        // Given
        whenever(authRepository.getCurrentUser()).thenReturn(firebaseUser)

        // When
        val result = authViewModel.getCurrentUser()

        // Then
        assertEquals(firebaseUser, result)
        verify(authRepository).getCurrentUser()
    }

    @Test
    fun `authentication state changes should update UI state`() = runTest {
        // Given
        val user = User(uid = "test_user_id", displayName = "Test User")
        whenever(authRepository.currentUser).thenReturn(flowOf(firebaseUser))
        whenever(authRepository.isSignedIn).thenReturn(flowOf(true))

        // When
        val newViewModel = AuthViewModel(authRepository, googleSignInService, notificationUseCase)
        advanceUntilIdle()

        // Then
        val state = newViewModel.uiState.value
        assertTrue(state.isSignedIn)
        assertEquals(firebaseUser, state.currentUser)
        assertFalse(state.isLoading)
    }
}