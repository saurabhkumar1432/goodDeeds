package com.browniepoints.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.repository.AuthRepository
import com.browniepoints.app.data.service.GoogleSignInService
import com.browniepoints.app.domain.usecase.NotificationUseCase
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing authentication state and operations
 * Handles Google Sign-In flow and authentication UI state
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val googleSignInService: GoogleSignInService,
    private val notificationUseCase: NotificationUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableStateFlow<NavigationEvent?>(null)
    val navigationEvent: StateFlow<NavigationEvent?> = _navigationEvent.asStateFlow()

    init {
        // Observe authentication state changes
        viewModelScope.launch {
            combine(
                authRepository.currentUser,
                authRepository.isSignedIn
            ) { currentUser, isSignedIn ->
                Log.d(TAG, "Auth state changed: isSignedIn=$isSignedIn, currentUser=${currentUser?.uid}")
                
                val newState = _uiState.value.copy(
                    currentUser = currentUser,
                    isSignedIn = isSignedIn,
                    isLoading = false
                )
                _uiState.value = newState
                
                Log.d(TAG, "Updated UI state: isAuthenticated=${newState.isAuthenticated}")
                
                // Navigation is handled by BrowniePointsNavigation based on auth state
                // No need to emit navigation events here
            }.collect { /* Empty collector - state updates happen in combine block */ }
        }
    }

    /**
     * Handles Google Sign-In result from the activity
     */
    fun handleGoogleSignInResult(task: Task<GoogleSignInAccount>) {
        viewModelScope.launch {
            Log.d(TAG, "Handling Google Sign-In result")
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            // Get ID token from Google Sign-In result
            googleSignInService.getIdTokenFromSignInResult(task)
                .onSuccess { idToken ->
                    Log.d(TAG, "Got ID token, signing in with Firebase")
                    // Use the existing signInWithGoogle method which properly handles the auth flow
                    authRepository.signInWithGoogle(idToken)
                        .onSuccess { user ->
                            Log.d(TAG, "Sign-in successful: ${user.uid}")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = null,
                                userProfile = user
                            )
                            
                            // Initialize FCM token after successful sign-in
                            initializeFcmToken()
                        }
                        .onFailure { exception ->
                            Log.e(TAG, "Firebase sign-in failed", exception)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = exception.message ?: "Firebase sign-in failed"
                            )
                        }
                }
                .onFailure { exception ->
                    Log.e(TAG, "Google Sign-In failed", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Google Sign-In failed"
                    )
                }
        }
    }

    /**
     * Initiates Google Sign-In process with the provided ID token (legacy method)
     */
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            Log.d(TAG, "Starting Google Sign-In with ID token")
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            authRepository.signInWithGoogle(idToken)
                .onSuccess { user ->
                    Log.d(TAG, "Sign-in successful: ${user.uid}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        userProfile = user
                    )
                    
                    // Initialize FCM token after successful sign-in
                    initializeFcmToken()
                }
                .onFailure { exception ->
                    Log.e(TAG, "Sign-in failed", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Sign-in failed"
                    )
                }
        }
    }

    /**
     * Loads the user profile after successful authentication
     */
    private suspend fun loadUserProfile() {
        val currentUser = authRepository.getCurrentUser()
        if (currentUser != null) {
            // The user profile is already loaded during sign-in process in AuthRepository
            // We just need to initialize FCM token
            initializeFcmToken()
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = null
            )
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Failed to load user profile"
            )
        }
    }

    /**
     * Signs out the current user
     */
    fun signOut() {
        viewModelScope.launch {
            Log.d(TAG, "Starting sign out")
            _uiState.value = _uiState.value.copy(isLoading = true)

            googleSignInService.signOut()
                .onSuccess {
                    Log.d(TAG, "Sign out successful")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null,
                        userProfile = null
                    )
                    _navigationEvent.value = NavigationEvent.NavigateToSignIn
                }
                .onFailure { exception ->
                    Log.e(TAG, "Sign out failed", exception)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Sign-out failed"
                    )
                }
        }
    }

    /**
     * Clears any error messages from the UI state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Clears navigation events after they've been handled
     */
    fun clearNavigationEvent() {
        _navigationEvent.value = null
    }

    /**
     * Gets the current Firebase user
     */
    fun getCurrentUser(): FirebaseUser? {
        return authRepository.getCurrentUser()
    }

    /**
     * Checks if Google Play Services is available
     */
    fun isGooglePlayServicesAvailable(): Boolean {
        return googleSignInService.isGooglePlayServicesAvailable()
    }

    /**
     * Gets the Google Sign-In client for launching the sign-in intent
     */
    fun getGoogleSignInClient() = googleSignInService.googleSignInClient

    /**
     * Initializes FCM token for the current user
     * Called after successful authentication
     */
    private fun initializeFcmToken() {
        viewModelScope.launch {
            notificationUseCase.initializeFcmToken()
                .onFailure { exception ->
                    // Log the error but don't show it to user as it's not critical for sign-in
                    Log.w(TAG, "Failed to initialize FCM token", exception)
                }
        }
    }
}

/**
 * Data class representing the authentication UI state
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val currentUser: FirebaseUser? = null,
    val userProfile: User? = null,
    val error: String? = null
) {
    /**
     * Indicates if the authentication process is in progress
     */
    val isAuthenticating: Boolean = isLoading

    /**
     * Indicates if there's an error that should be displayed to the user
     */
    val hasError: Boolean = error != null

    /**
     * Indicates if the user is authenticated and has a complete profile
     */
    val isAuthenticated: Boolean = isSignedIn && currentUser != null

    /**
     * Indicates if the user should see the sign-in screen
     */
    val shouldShowSignIn: Boolean = !isLoading && !isSignedIn

    /**
     * Indicates if the user should see the main screen
     */
    val shouldShowMain: Boolean = !isLoading && isAuthenticated
}

/**
 * Sealed class representing navigation events from authentication
 */
sealed class NavigationEvent {
    object NavigateToMain : NavigationEvent()
    object NavigateToSignIn : NavigationEvent()
}