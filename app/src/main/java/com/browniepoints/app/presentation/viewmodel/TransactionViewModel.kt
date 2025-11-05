package com.browniepoints.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browniepoints.app.data.model.Transaction
import com.browniepoints.app.data.model.TransactionType
import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.repository.ConnectionRepository
import com.browniepoints.app.data.repository.TransactionRepository
import com.browniepoints.app.data.repository.UserRepository
import com.browniepoints.app.domain.usecase.InAppNotificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing points transactions and transaction history
 * Handles giving points and viewing transaction history
 */
@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository,
    private val connectionRepository: ConnectionRepository,
    private val inAppNotificationUseCase: InAppNotificationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionUiState())
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    private val _givePointsState = MutableStateFlow(GivePointsState())
    val givePointsState: StateFlow<GivePointsState> = _givePointsState.asStateFlow()

    private val _deductPointsState = MutableStateFlow(DeductPointsState())
    val deductPointsState: StateFlow<DeductPointsState> = _deductPointsState.asStateFlow()

    /**
     * Loads transaction history for the specified user
     */
    fun loadTransactionHistory(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Observe real-time transaction updates
            transactionRepository.observeTransactions(userId).collect { transactions ->
                _uiState.value = _uiState.value.copy(
                    transactions = transactions,
                    isLoading = false,
                    error = null
                )
            }
        }
    }

    /**
     * Loads user data and connected partner information for giving points
     */
    fun loadGivePointsData(userId: String) {
        viewModelScope.launch {
            _givePointsState.value = _givePointsState.value.copy(isLoading = true, error = null)

            userRepository.getUser(userId)
                .onSuccess { user ->
                    if (user != null && user.connected && !user.connectedUserId.isNullOrBlank()) {
                        // Load connected partner information
                        userRepository.getUser(user.connectedUserId!!)
                            .onSuccess { partner ->
                                _givePointsState.value = _givePointsState.value.copy(
                                    currentUser = user,
                                    connectedPartner = partner,
                                    isLoading = false,
                                    error = null
                                )
                            }
                            .onFailure { exception ->
                                _givePointsState.value = _givePointsState.value.copy(
                                    isLoading = false,
                                    error = exception.message ?: "Failed to load partner information"
                                )
                            }
                    } else {
                        _givePointsState.value = _givePointsState.value.copy(
                            currentUser = user,
                            isLoading = false,
                            error = if (user?.connected != true || user.connectedUserId.isNullOrBlank()) "No connected partner found" else null
                        )
                    }
                }
                .onFailure { exception ->
                    _givePointsState.value = _givePointsState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Failed to load user data"
                    )
                }
        }
    }

    /**
     * Observes real-time point balance updates for the specified user
     */
    fun observeUserPointBalance(userId: String) {
        viewModelScope.launch {
            userRepository.observeUser(userId).collect { user ->
                if (user != null) {
                    val previousConnectedUserId = _givePointsState.value.currentUser?.connectedUserId
                    val newConnectedUserId = user.connectedUserId
                    
                    _givePointsState.value = _givePointsState.value.copy(
                        currentUser = user
                    )
                    
                    // If connectedUserId changed (new connection or disconnection), reload partner
                    if (previousConnectedUserId != newConnectedUserId) {
                        android.util.Log.d("TransactionViewModel", "Connected user ID changed from $previousConnectedUserId to $newConnectedUserId")
                        
                        if (newConnectedUserId != null) {
                            // Load the new partner
                            android.util.Log.d("TransactionViewModel", "Loading new partner: $newConnectedUserId")
                            userRepository.getUser(newConnectedUserId).fold(
                                onSuccess = { partner ->
                                    android.util.Log.d("TransactionViewModel", "Partner loaded: ${partner?.displayName}")
                                    _givePointsState.value = _givePointsState.value.copy(
                                        connectedPartner = partner
                                    )
                                },
                                onFailure = { error ->
                                    android.util.Log.e("TransactionViewModel", "Failed to load partner", error)
                                    _givePointsState.value = _givePointsState.value.copy(
                                        connectedPartner = null,
                                        error = "Failed to load partner: ${error.message}"
                                    )
                                }
                            )
                        } else {
                            // User disconnected, clear partner
                            android.util.Log.d("TransactionViewModel", "User disconnected, clearing partner")
                            _givePointsState.value = _givePointsState.value.copy(
                                connectedPartner = null
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates the points amount to be given
     */
    fun updatePointsAmount(points: Int) {
        if (points in Transaction.MIN_POINTS..Transaction.MAX_POINTS) {
            _givePointsState.value = _givePointsState.value.copy(
                selectedPoints = points,
                validationError = null
            )
        } else {
            _givePointsState.value = _givePointsState.value.copy(
                selectedPoints = points.coerceIn(Transaction.MIN_POINTS, Transaction.MAX_POINTS),
                validationError = "Points must be between ${Transaction.MIN_POINTS} and ${Transaction.MAX_POINTS}"
            )
        }
    }

    /**
     * Updates the message to be sent with the points
     */
    fun updateMessage(message: String) {
        if (message.length <= Transaction.MAX_MESSAGE_LENGTH) {
            _givePointsState.value = _givePointsState.value.copy(
                message = message,
                validationError = null
            )
        } else {
            // Truncate the message to the maximum length
            val truncatedMessage = message.take(Transaction.MAX_MESSAGE_LENGTH)
            _givePointsState.value = _givePointsState.value.copy(
                message = truncatedMessage,
                validationError = "Message truncated to ${Transaction.MAX_MESSAGE_LENGTH} characters"
            )
        }
    }

    /**
     * Gives points to the connected partner
     */
    fun givePoints() {
        val state = _givePointsState.value
        val currentUser = state.currentUser
        val partner = state.connectedPartner

        // Validate required data
        if (currentUser == null || partner == null) {
            _givePointsState.value = state.copy(
                error = "Missing user or partner information"
            )
            return
        }

        // Validate points amount
        if (state.selectedPoints < Transaction.MIN_POINTS || state.selectedPoints > Transaction.MAX_POINTS) {
            _givePointsState.value = state.copy(
                validationError = "Points must be between ${Transaction.MIN_POINTS} and ${Transaction.MAX_POINTS}"
            )
            return
        }

        // Validate message length
        if (state.message.length > Transaction.MAX_MESSAGE_LENGTH) {
            _givePointsState.value = state.copy(
                validationError = "Message cannot exceed ${Transaction.MAX_MESSAGE_LENGTH} characters"
            )
            return
        }

        viewModelScope.launch {
            _givePointsState.value = state.copy(
                isGivingPoints = true,
                error = null,
                validationError = null
            )

            try {
                // Get the actual connection ID from the repository
                val connectionResult = connectionRepository.getConnection(currentUser.uid)
                
                connectionResult
                    .onSuccess { connection ->
                        if (connection == null) {
                            _givePointsState.value = _givePointsState.value.copy(
                                isGivingPoints = false,
                                error = "No active connection found"
                            )
                            return@onSuccess
                        }

                        // Verify the partner is part of this connection
                        if (!connection.containsUser(partner.uid)) {
                            _givePointsState.value = _givePointsState.value.copy(
                                isGivingPoints = false,
                                error = "Partner is not part of the active connection"
                            )
                            return@onSuccess
                        }

                        // Create the transaction using the proper connection ID
                        transactionRepository.givePoints(
                            senderId = currentUser.uid,
                            receiverId = partner.uid,
                            points = state.selectedPoints,
                            message = state.message.takeIf { it.isNotBlank() },
                            connectionId = connection.id
                        )
                            .onSuccess { transaction ->
                                _givePointsState.value = _givePointsState.value.copy(
                                    isGivingPoints = false,
                                    error = null,
                                    lastTransaction = transaction,
                                    selectedPoints = 1, // Reset to default
                                    message = "" // Clear message
                                )
                                
                                // Create in-app notification for the receiver
                                createPointsReceivedNotification(transaction, currentUser)
                            }
                            .onFailure { exception ->
                                _givePointsState.value = _givePointsState.value.copy(
                                    isGivingPoints = false,
                                    error = exception.message ?: "Failed to give points"
                                )
                            }
                    }
                    .onFailure { exception ->
                        _givePointsState.value = _givePointsState.value.copy(
                            isGivingPoints = false,
                            error = exception.message ?: "Failed to get connection information"
                        )
                    }
            } catch (e: Exception) {
                _givePointsState.value = _givePointsState.value.copy(
                    isGivingPoints = false,
                    error = e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    /**
     * Loads user data and connected partner information for deducting points
     */
    fun loadDeductPointsData(userId: String) {
        viewModelScope.launch {
            _deductPointsState.value = _deductPointsState.value.copy(isLoading = true, error = null)

            userRepository.getUser(userId)
                .onSuccess { user ->
                    if (user != null && user.connected && !user.connectedUserId.isNullOrBlank()) {
                        // Load connected partner information
                        userRepository.getUser(user.connectedUserId!!)
                            .onSuccess { partner ->
                                _deductPointsState.value = _deductPointsState.value.copy(
                                    currentUser = user,
                                    connectedPartner = partner,
                                    isLoading = false,
                                    error = null
                                )
                            }
                            .onFailure { exception ->
                                _deductPointsState.value = _deductPointsState.value.copy(
                                    isLoading = false,
                                    error = exception.message ?: "Failed to load partner information"
                                )
                            }
                    } else {
                        _deductPointsState.value = _deductPointsState.value.copy(
                            currentUser = user,
                            isLoading = false,
                            error = if (user?.connected != true || user.connectedUserId.isNullOrBlank()) "No connected partner found" else null
                        )
                    }
                }
                .onFailure { exception ->
                    _deductPointsState.value = _deductPointsState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Failed to load user data"
                    )
                }
        }
    }

    /**
     * Updates the points amount to be deducted
     */
    fun updateDeductPointsAmount(points: Int) {
        if (points in Transaction.MIN_POINTS..Transaction.MAX_POINTS) {
            _deductPointsState.value = _deductPointsState.value.copy(
                pointsAmount = points,
                validationError = null
            )
        } else {
            _deductPointsState.value = _deductPointsState.value.copy(
                pointsAmount = points.coerceIn(Transaction.MIN_POINTS, Transaction.MAX_POINTS),
                validationError = "Points must be between ${Transaction.MIN_POINTS} and ${Transaction.MAX_POINTS}"
            )
        }
    }

    /**
     * Updates the reason for deducting points
     */
    fun updateDeductReason(reason: String) {
        if (reason.length <= Transaction.MAX_MESSAGE_LENGTH) {
            _deductPointsState.value = _deductPointsState.value.copy(
                reason = reason,
                validationError = null
            )
        } else {
            // Truncate the reason to the maximum length
            val truncatedReason = reason.take(Transaction.MAX_MESSAGE_LENGTH)
            _deductPointsState.value = _deductPointsState.value.copy(
                reason = truncatedReason,
                validationError = "Reason truncated to ${Transaction.MAX_MESSAGE_LENGTH} characters"
            )
        }
    }

    /**
     * Deducts points from the connected partner
     */
    fun deductPoints() {
        val state = _deductPointsState.value
        val currentUser = state.currentUser
        val partner = state.connectedPartner

        // Validate required data
        if (currentUser == null || partner == null) {
            _deductPointsState.value = state.copy(
                error = "Missing user or partner information"
            )
            return
        }

        // Validate points amount
        if (state.pointsAmount < Transaction.MIN_POINTS || state.pointsAmount > Transaction.MAX_POINTS) {
            _deductPointsState.value = state.copy(
                validationError = "Points must be between ${Transaction.MIN_POINTS} and ${Transaction.MAX_POINTS}"
            )
            return
        }

        // Validate reason is provided and not too long
        if (state.reason.isBlank()) {
            _deductPointsState.value = state.copy(
                validationError = "Please provide a reason for deducting points"
            )
            return
        }

        if (state.reason.length > Transaction.MAX_MESSAGE_LENGTH) {
            _deductPointsState.value = state.copy(
                validationError = "Reason cannot exceed ${Transaction.MAX_MESSAGE_LENGTH} characters"
            )
            return
        }

        viewModelScope.launch {
            _deductPointsState.value = state.copy(
                isDeductingPoints = true,
                error = null,
                validationError = null
            )

            try {
                // Get the actual connection ID from the repository
                val connectionResult = connectionRepository.getConnection(currentUser.uid)
                
                connectionResult
                    .onSuccess { connection ->
                        if (connection == null) {
                            _deductPointsState.value = _deductPointsState.value.copy(
                                isDeductingPoints = false,
                                error = "No active connection found"
                            )
                            return@onSuccess
                        }

                        // Verify the partner is part of this connection
                        if (!connection.containsUser(partner.uid)) {
                            _deductPointsState.value = _deductPointsState.value.copy(
                                isDeductingPoints = false,
                                error = "Partner is not part of the active connection"
                            )
                            return@onSuccess
                        }

                        // Create the deduction transaction using the proper connection ID
                        transactionRepository.deductPoints(
                            senderId = currentUser.uid,
                            receiverId = partner.uid,
                            points = state.pointsAmount,
                            reason = state.reason,
                            connectionId = connection.id
                        )
                            .onSuccess { transaction ->
                                _deductPointsState.value = _deductPointsState.value.copy(
                                    isDeductingPoints = false,
                                    error = null,
                                    lastTransaction = transaction,
                                    pointsAmount = 1, // Reset to default
                                    reason = "" // Clear reason
                                )
                                
                                // Create in-app notification for the receiver
                                createPointsDeductedNotification(transaction, currentUser)
                            }
                            .onFailure { exception ->
                                _deductPointsState.value = _deductPointsState.value.copy(
                                    isDeductingPoints = false,
                                    error = exception.message ?: "Failed to deduct points"
                                )
                            }
                    }
                    .onFailure { exception ->
                        _deductPointsState.value = _deductPointsState.value.copy(
                            isDeductingPoints = false,
                            error = exception.message ?: "Failed to get connection information"
                        )
                    }
            } catch (e: Exception) {
                _deductPointsState.value = _deductPointsState.value.copy(
                    isDeductingPoints = false,
                    error = e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    /**
     * Clears any error messages from the UI state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
        _givePointsState.value = _givePointsState.value.copy(error = null, validationError = null)
    }

    /**
     * Clears deduct points error messages
     */
    fun clearDeductError() {
        _deductPointsState.value = _deductPointsState.value.copy(error = null, validationError = null)
    }

    /**
     * Clears the last transaction result
     */
    fun clearLastTransaction() {
        _givePointsState.value = _givePointsState.value.copy(lastTransaction = null)
    }

    /**
     * Clears the last deduct transaction result
     */
    fun clearLastDeductTransaction() {
        _deductPointsState.value = _deductPointsState.value.copy(lastTransaction = null)
    }

    /**
     * Creates an in-app notification for points received
     */
    private fun createPointsReceivedNotification(transaction: Transaction, senderUser: User) {
        viewModelScope.launch {
            inAppNotificationUseCase.createPointsReceivedNotification(transaction, senderUser)
                .onFailure { exception ->
                    // Log the error but don't show it to user as it's not critical
                    android.util.Log.w("TransactionViewModel", "Failed to create notification", exception)
                }
        }
    }

    /**
     * Creates an in-app notification for points deducted
     */
    private fun createPointsDeductedNotification(transaction: Transaction, senderUser: User) {
        viewModelScope.launch {
            try {
                // Create a notification specifically for point deductions
                // The notification use case should handle the different transaction types appropriately
                inAppNotificationUseCase.createPointsReceivedNotification(transaction, senderUser)
                    .onFailure { exception ->
                        // Log the error but don't show it to user as it's not critical
                        android.util.Log.w("TransactionViewModel", "Failed to create deduction notification", exception)
                    }
            } catch (e: Exception) {
                android.util.Log.w("TransactionViewModel", "Exception while creating deduction notification", e)
            }
        }
    }
    
    /**
     * Clears all data when user logs out
     */
    fun clearData() {
        _uiState.value = TransactionUiState()
        _givePointsState.value = GivePointsState()
        _deductPointsState.value = DeductPointsState()
    }
}

/**
 * Data class representing the transaction history UI state
 */
data class TransactionUiState(
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    /**
     * Indicates if there are transactions to display
     */
    val hasTransactions: Boolean = transactions.isNotEmpty()

    /**
     * Indicates if there's an error that should be displayed to the user
     */
    val hasError: Boolean = error != null

    /**
     * Gets transactions sent by the user
     */
    fun getSentTransactions(userId: String): List<Transaction> {
        return transactions.filter { it.senderId == userId }
    }

    /**
     * Gets transactions received by the user
     */
    fun getReceivedTransactions(userId: String): List<Transaction> {
        return transactions.filter { it.receiverId == userId }
    }
}

/**
 * Data class representing the give points UI state
 */
data class GivePointsState(
    val currentUser: User? = null,
    val connectedPartner: User? = null,
    val selectedPoints: Int = 1,
    val message: String = "",
    val isLoading: Boolean = false,
    val isGivingPoints: Boolean = false,
    val error: String? = null,
    val validationError: String? = null,
    val lastTransaction: Transaction? = null
) {
    /**
     * Indicates if the user can give points (has a connected partner)
     */
    val canGivePoints: Boolean = connectedPartner != null && !isGivingPoints

    /**
     * Indicates if there's an error that should be displayed to the user
     */
    val hasError: Boolean = error != null

    /**
     * Indicates if there's a validation error
     */
    val hasValidationError: Boolean = validationError != null

    /**
     * Indicates if the form is valid and ready to submit
     */
    val isFormValid: Boolean = selectedPoints in Transaction.MIN_POINTS..Transaction.MAX_POINTS &&
            message.length <= Transaction.MAX_MESSAGE_LENGTH &&
            connectedPartner != null

    /**
     * Gets the remaining character count for the message
     */
    val remainingMessageChars: Int = Transaction.MAX_MESSAGE_LENGTH - message.length
}

/**
 * Data class representing the deduct points UI state
 */
data class DeductPointsState(
    val currentUser: User? = null,
    val connectedPartner: User? = null,
    val pointsAmount: Int = 1,
    val reason: String = "",
    val isLoading: Boolean = false,
    val isDeductingPoints: Boolean = false,
    val error: String? = null,
    val validationError: String? = null,
    val lastTransaction: Transaction? = null
) {
    /**
     * Indicates if the user can deduct points (has a connected partner)
     */
    val canDeductPoints: Boolean = connectedPartner != null && !isDeductingPoints

    /**
     * Indicates if there's an error that should be displayed to the user
     */
    val hasError: Boolean = error != null

    /**
     * Indicates if there's a validation error
     */
    val hasValidationError: Boolean = validationError != null

    /**
     * Indicates if the form is valid and ready to submit
     */
    val isFormValid: Boolean = pointsAmount in Transaction.MIN_POINTS..Transaction.MAX_POINTS &&
            reason.isNotBlank() &&
            reason.length <= Transaction.MAX_MESSAGE_LENGTH &&
            connectedPartner != null

    /**
     * Gets the remaining character count for the reason
     */
    val remainingReasonChars: Int = Transaction.MAX_MESSAGE_LENGTH - reason.length
}