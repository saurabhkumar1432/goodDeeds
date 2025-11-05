package com.browniepoints.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browniepoints.app.data.model.Connection
import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.repository.ConnectionRepository
import com.browniepoints.app.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing connection-related UI state and operations
 */
@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val connectionRepository: ConnectionRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private val currentUserId = auth.currentUser?.uid

    init {
        loadUserData()
        observeConnection()
    }

    private fun loadUserData() {
        currentUserId?.let { userId ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                try {
                    userRepository.getUser(userId).fold(
                        onSuccess = { user ->
                            _uiState.value = _uiState.value.copy(
                                currentUser = user,
                                isLoading = false,
                                error = if (user == null) "User data not found" else null
                            )
                            
                            // Load partner data if connected
                            user?.connectedUserId?.let { partnerId ->
                                loadPartnerData(partnerId)
                            }
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                error = "Failed to load user data: ${getErrorMessage(error)}",
                                isLoading = false
                            )
                        }
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        error = "Unexpected error loading user data: ${e.message}",
                        isLoading = false
                    )
                }
            }
        } ?: run {
            _uiState.value = _uiState.value.copy(
                error = "User not authenticated",
                isLoading = false
            )
        }
    }

    private fun loadPartnerData(partnerId: String) {
        viewModelScope.launch {
            try {
                userRepository.getUser(partnerId).fold(
                    onSuccess = { partner ->
                        _uiState.value = _uiState.value.copy(
                            connectedPartner = partner,
                            error = if (partner == null) "Partner data not found" else null
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to load partner data: ${getErrorMessage(error)}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Unexpected error loading partner data: ${e.message}"
                )
            }
        }
    }

    private fun observeConnection() {
        currentUserId?.let { userId ->
            viewModelScope.launch {
                connectionRepository.observeConnection(userId).collect { connection ->
                    val currentConnection = _uiState.value.connection
                    
                    // Update connection state
                    _uiState.value = _uiState.value.copy(connection = connection)
                    
                    // Handle connection state changes
                    when {
                        // New connection established
                        currentConnection == null && connection != null -> {
                            val partnerId = connection.getPartnerUserId(userId)
                            partnerId?.let { loadPartnerData(it) }
                        }
                        // Connection lost/disconnected
                        currentConnection != null && connection == null -> {
                            _uiState.value = _uiState.value.copy(connectedPartner = null)
                        }
                        // Connection updated (same connection but potentially different state)
                        currentConnection != null && connection != null && currentConnection.id == connection.id -> {
                            // Check if we need to reload partner data
                            if (_uiState.value.connectedPartner == null) {
                                val partnerId = connection.getPartnerUserId(userId)
                                partnerId?.let { loadPartnerData(it) }
                            }
                        }
                    }
                }
            }
        }
    }

    fun connectWithMatchingCode(matchingCode: String) {
        val trimmedCode = matchingCode.trim()
        
        if (trimmedCode.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a matching code")
            return
        }

        currentUserId?.let { userId ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isConnecting = true, error = null)

                try {
                    // First validate the matching code (repository handles format validation)
                    connectionRepository.validateMatchingCode(trimmedCode).fold(
                        onSuccess = { partnerId ->
                            if (partnerId == null) {
                                _uiState.value = _uiState.value.copy(
                                    error = "Invalid matching code. Please check and try again.",
                                    isConnecting = false
                                )
                            } else if (partnerId == userId) {
                                _uiState.value = _uiState.value.copy(
                                    error = "You cannot connect to yourself",
                                    isConnecting = false
                                )
                            } else {
                                // Create the connection directly (repository handles validation)
                                createConnection(userId, partnerId)
                            }
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                error = getErrorMessage(error),
                                isConnecting = false
                            )
                        }
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        error = "An unexpected error occurred: ${e.message}",
                        isConnecting = false
                    )
                }
            }
        } ?: run {
            _uiState.value = _uiState.value.copy(
                error = "User not authenticated. Please sign in again.",
                isConnecting = false
            )
        }
    }

    private suspend fun createConnection(userId: String, partnerId: String) {
        try {
            connectionRepository.createConnection(userId, partnerId).fold(
                onSuccess = { connection ->
                    _uiState.value = _uiState.value.copy(
                        connection = connection,
                        isConnecting = false,
                        error = null,
                        matchingCodeInput = "" // Clear the input after successful connection
                    )
                    
                    // Reload user data to get updated connection status
                    loadUserData()
                    
                    // Load partner data immediately
                    loadPartnerData(partnerId)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = getErrorMessage(error),
                        isConnecting = false
                    )
                }
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Failed to create connection: ${e.message}",
                isConnecting = false
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun updateMatchingCodeInput(code: String) {
        // Only allow alphanumeric characters and limit to 6 characters
        val filteredCode = code.filter { it.isLetterOrDigit() }.take(6).uppercase()
        _uiState.value = _uiState.value.copy(
            matchingCodeInput = filteredCode,
            error = null // Clear error when user starts typing
        )
    }
    
    /**
     * Converts exceptions to user-friendly error messages
     */
    private fun getErrorMessage(error: Throwable): String {
        return when (error) {
            is IllegalArgumentException -> error.message ?: "Invalid input provided"
            is IllegalStateException -> error.message ?: "Operation not allowed at this time"
            else -> error.message ?: "An unexpected error occurred"
        }
    }
    
    /**
     * Disconnects the current user from their partner
     */
    fun disconnect() {
        val connection = _uiState.value.connection
        if (connection == null) {
            _uiState.value = _uiState.value.copy(error = "No active connection to disconnect")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                connectionRepository.disconnectUsers(connection.id).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            connection = null,
                            connectedPartner = null,
                            isLoading = false,
                            error = null
                        )
                        // Reload user data to reflect disconnection
                        loadUserData()
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to disconnect: ${getErrorMessage(error)}",
                            isLoading = false
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Unexpected error during disconnection: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    /**
     * Loads connection data for the specified user
     */
    fun loadConnection(userId: String) {
        viewModelScope.launch {
            try {
                connectionRepository.getConnection(userId).fold(
                    onSuccess = { connection ->
                        _uiState.value = _uiState.value.copy(
                            connection = connection,
                            error = null
                        )
                        
                        // Load partner data if connection exists
                        connection?.getPartnerUserId(userId)?.let { partnerId ->
                            loadPartnerData(partnerId)
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to load connection: ${getErrorMessage(error)}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Unexpected error loading connection: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Refreshes all connection-related data
     */
    fun refresh() {
        loadUserData()
        currentUserId?.let { userId ->
            loadConnection(userId)
        }
    }
}

/**
 * UI state for the Connection screen
 */
data class ConnectionUiState(
    val currentUser: User? = null,
    val connectedPartner: User? = null,
    val connection: Connection? = null,
    val matchingCodeInput: String = "",
    val isLoading: Boolean = false,
    val isConnecting: Boolean = false,
    val error: String? = null
) {
    val isConnected: Boolean
        get() = connection != null && currentUser?.isConnected() == true
}