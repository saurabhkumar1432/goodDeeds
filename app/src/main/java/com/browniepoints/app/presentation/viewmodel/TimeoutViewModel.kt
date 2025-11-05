package com.browniepoints.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.browniepoints.app.data.error.AppError
import com.browniepoints.app.data.model.Timeout
import com.browniepoints.app.data.service.ErrorHandlerService
import com.browniepoints.app.data.service.NetworkMonitorService
import com.browniepoints.app.data.service.OfflineSyncManager
import com.browniepoints.app.data.service.TimeoutManager
import com.browniepoints.app.domain.usecase.TimeoutException
import com.browniepoints.app.domain.usecase.TimeoutUseCase
import com.browniepoints.app.presentation.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class TimeoutViewModel @Inject constructor(
    private val timeoutUseCase: TimeoutUseCase,
    private val timeoutManager: TimeoutManager,
    networkMonitorService: NetworkMonitorService,
    offlineSyncManager: OfflineSyncManager,
    errorHandlerService: ErrorHandlerService
) : BaseViewModel(networkMonitorService, offlineSyncManager, errorHandlerService) {

    companion object {
        private const val TAG = "TimeoutViewModel"
    }

    // UI State for timeout operations
    private val _timeoutRequestState = MutableStateFlow<UiState<Timeout>>(UiState.Idle)
    val timeoutRequestState: StateFlow<UiState<Timeout>> = _timeoutRequestState.asStateFlow()

    // Dialog states
    private val _showTimeoutRequestDialog = MutableStateFlow(false)
    val showTimeoutRequestDialog: StateFlow<Boolean> = _showTimeoutRequestDialog.asStateFlow()

    // Current connection ID being observed
    private val _currentConnectionId = MutableStateFlow<String?>(null)
    val currentConnectionId: StateFlow<String?> = _currentConnectionId.asStateFlow()

    // Timeout eligibility
    private val _canRequestTimeout = MutableStateFlow(true)
    val canRequestTimeout: StateFlow<Boolean> = _canRequestTimeout.asStateFlow()

    // Active timeout for current connection
    val activeTimeout: StateFlow<Timeout?> = currentConnectionId
        .filterNotNull()
        .flatMapLatest { connectionId ->
            timeoutManager.observeActiveTimeoutWithMonitoring(connectionId)
                .distinctUntilChanged() // Prevent flickering from rapid updates
                .debounce(100) // Wait 100ms before emitting
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Timeout status (true if any timeout is active)
    val isTimeoutActive: StateFlow<Boolean> = currentConnectionId
        .filterNotNull()
        .flatMapLatest { connectionId ->
            timeoutManager.observeTimeoutStatus(connectionId)
                .distinctUntilChanged() // Prevent flickering from rapid updates
                .debounce(100) // Wait 100ms before emitting to avoid rapid changes
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Remaining time in milliseconds with automatic countdown
    val remainingTimeMs: StateFlow<Long> = combine(
        currentConnectionId.filterNotNull(),
        activeTimeout
    ) { connectionId, timeout ->
        if (timeout != null && timeout.isActive && !timeout.hasExpired()) {
            timeout.getRemainingTimeMs()
        } else {
            0L
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    // Whether transactions should be disabled
    val transactionsDisabled: StateFlow<Boolean> = currentConnectionId
        .filterNotNull()
        .flatMapLatest { connectionId ->
            timeoutManager.observeTransactionsDisabled(connectionId)
                .distinctUntilChanged() // Prevent flickering from rapid updates
                .debounce(100) // Wait 100ms before emitting
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Countdown timer that updates every second
    private val _countdownTicker = MutableStateFlow(0L)
    
    // Live remaining time that updates every second
    val liveRemainingTimeMs: StateFlow<Long> = combine(
        remainingTimeMs,
        _countdownTicker
    ) { baseTime, _ ->
        val timeout = activeTimeout.value
        if (timeout != null && timeout.isActive && !timeout.hasExpired()) {
            timeout.getRemainingTimeMs()
        } else {
            0L
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    /**
     * Sets the connection ID to observe for timeout status
     */
    fun setConnectionId(connectionId: String, userId: String) {
        Log.d(TAG, "Setting connection ID: $connectionId for user: $userId")
        _currentConnectionId.value = connectionId
        
        // Check if user can request timeout today
        checkTimeoutEligibility(userId)
        
        // Start countdown timer for active timeouts
        startCountdownTimer()
    }
    
    /**
     * Starts the countdown timer that updates every second
     */
    private fun startCountdownTimer() {
        viewModelScope.launch {
            while (true) {
                delay(1000) // Update every second
                _countdownTicker.value = System.currentTimeMillis()
                
                // Check if timeout has expired and handle it
                val timeout = activeTimeout.value
                if (timeout != null && timeout.hasExpired()) {
                    Log.d(TAG, "Timeout expired during countdown: ${timeout.id}")
                    // The repository observers will handle the expiration
                    break
                }
            }
        }
    }

    /**
     * Shows the timeout request confirmation dialog
     */
    fun showTimeoutRequestDialog() {
        Log.d(TAG, "showTimeoutRequestDialog called")
        Log.d(TAG, "  - currentConnectionId: ${_currentConnectionId.value}")
        Log.d(TAG, "  - canRequestTimeout: ${_canRequestTimeout.value}")
        Log.d(TAG, "  - isTimeoutActive: ${isTimeoutActive.value}")
        Log.d(TAG, "  - activeTimeout: ${activeTimeout.value}")
        
        // Validate before showing dialog
        if (!_canRequestTimeout.value) {
            Log.w(TAG, "Cannot show timeout dialog: daily limit exceeded")
            _timeoutRequestState.value = UiState.Error(
                AppError.ValidationError("You have already used your daily timeout allowance. Try again tomorrow.")
            )
            return
        }
        
        if (isTimeoutActive.value) {
            Log.w(TAG, "Cannot show timeout dialog: timeout already active")
            _timeoutRequestState.value = UiState.Error(
                AppError.ValidationError("A timeout is already active.")
            )
            return
        }
        
        if (_currentConnectionId.value == null) {
            Log.w(TAG, "Cannot show timeout dialog: no connection ID set")
            _timeoutRequestState.value = UiState.Error(
                AppError.ValidationError("No active connection found. Please connect with someone first.")
            )
            return
        }
        
        Log.d(TAG, "Showing timeout request dialog")
        _showTimeoutRequestDialog.value = true
    }

    /**
     * Hides the timeout request confirmation dialog
     */
    fun hideTimeoutRequestDialog() {
        Log.d(TAG, "Hiding timeout request dialog")
        _showTimeoutRequestDialog.value = false
    }

    /**
     * Requests a timeout for the current connection
     */
    fun requestTimeout(userId: String) {
        val connectionId = _currentConnectionId.value
        if (connectionId == null) {
            Log.w(TAG, "Cannot request timeout: no connection ID set")
            handleError(TimeoutException.InvalidRequest("No connection available"))
            return
        }

        // Validate request before proceeding
        if (!_canRequestTimeout.value) {
            Log.w(TAG, "Cannot request timeout: daily limit exceeded")
            _timeoutRequestState.value = UiState.Error(
                AppError.ValidationError("You have already used your daily timeout allowance")
            )
            return
        }

        // Check if timeout is already active
        if (isTimeoutActive.value) {
            Log.w(TAG, "Cannot request timeout: timeout already active")
            _timeoutRequestState.value = UiState.Error(
                AppError.ValidationError("A timeout is already active")
            )
            _showTimeoutRequestDialog.value = false
            return
        }

        Log.d(TAG, "Requesting timeout for user: $userId, connection: $connectionId")
        _timeoutRequestState.value = UiState.Loading

        executeWithErrorHandling(
            operation = {
                timeoutUseCase.requestTimeout(userId, connectionId)
            },
            onSuccess = { timeout ->
                Log.d(TAG, "Timeout requested successfully: ${timeout.id}")
                _timeoutRequestState.value = UiState.Success(timeout)
                _showTimeoutRequestDialog.value = false
                
                // Start monitoring the timeout
                timeoutManager.startTimeoutMonitoring(timeout)
                
                // Update eligibility immediately
                _canRequestTimeout.value = false
                
                // Refresh timeout status
                refreshTimeoutStatus(userId)
            },
            onError = { error ->
                Log.e(TAG, "Error requesting timeout", error)
                _timeoutRequestState.value = UiState.Error(error)
                
                // Show user-friendly error message based on error type
                when (error) {
                    is AppError.ValidationError -> {
                        // Validation errors are already user-friendly
                    }
                    else -> {
                        when (val cause = error.cause) {
                            is TimeoutException.DailyLimitExceeded -> {
                                _canRequestTimeout.value = false
                            }
                            is TimeoutException.TimeoutAlreadyActive -> {
                                // Timeout already active, hide dialog
                                _showTimeoutRequestDialog.value = false
                            }
                            else -> {
                                // Handle other error types
                            }
                        }
                    }
                }
            }
        )
    }

    /**
     * Checks if the user can request a timeout today
     */
    private fun checkTimeoutEligibility(userId: String) {
        viewModelScope.launch {
            try {
                val canRequest = timeoutUseCase.canRequestTimeout(userId).getOrThrow()
                _canRequestTimeout.value = canRequest
                Log.d(TAG, "User $userId can request timeout: $canRequest")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking timeout eligibility", e)
                _canRequestTimeout.value = false
            }
        }
    }

    /**
     * Manually expires the current timeout (for testing or admin purposes)
     */
    fun expireCurrentTimeout() {
        val timeout = activeTimeout.value
        val connectionId = _currentConnectionId.value
        
        if (timeout != null && connectionId != null) {
            Log.d(TAG, "Manually expiring timeout: ${timeout.id}")
            
            viewModelScope.launch {
                try {
                    timeoutManager.expireTimeout(timeout.id, connectionId).getOrThrow()
                    Log.d(TAG, "Timeout expired successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error expiring timeout", e)
                    handleError(e)
                }
            }
        }
    }

    /**
     * Gets timeout history for a user
     */
    /**
     * Gets timeout history for a user with caching to prevent repeated queries
     */
    private val timeoutHistoryCache = mutableMapOf<String, Flow<UiState<List<Timeout>>>>()
    
    fun getTimeoutHistory(userId: String): Flow<UiState<List<Timeout>>> {
        // Return cached flow if exists, otherwise create and cache it
        return timeoutHistoryCache.getOrPut(userId) {
            flow {
                emit(UiState.Loading)
                try {
                    val history = timeoutUseCase.getTimeoutHistory(userId).getOrThrow()
                    emit(UiState.Success(history))
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting timeout history", e)
                    val appError = if (e is AppError) e else AppError.UnknownError(e.message ?: "Unknown error", e)
                    emit(UiState.Error(appError))
                }
            }.shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                replay = 1
            )
        }
    }

    /**
     * Clears the timeout request state
     */
    fun clearTimeoutRequestState() {
        _timeoutRequestState.value = UiState.Idle
    }

    /**
     * Refreshes timeout status for current connection
     */
    fun refreshTimeoutStatus(userId: String) {
        val connectionId = _currentConnectionId.value
        if (connectionId != null) {
            Log.d(TAG, "Refreshing timeout status for connection: $connectionId")
            checkTimeoutEligibility(userId)
            
            // Synchronize partner timeout state
            viewModelScope.launch {
                try {
                    timeoutUseCase.synchronizePartnerTimeoutState(connectionId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to synchronize partner timeout state", e)
                }
            }
        }
    }
    
    /**
     * Handles timeout expiration events
     */
    fun onTimeoutExpired(timeoutId: String) {
        Log.d(TAG, "Handling timeout expiration: $timeoutId")
        
        // Clear any loading states
        _timeoutRequestState.value = UiState.Idle
        
        // Update eligibility (user can request timeout again tomorrow)
        val userId = _currentConnectionId.value?.let { connectionId ->
            // We need the user ID to check eligibility
            // This should be passed from the calling component
        }
        
        // Show success message
        _timeoutRequestState.value = UiState.Success(
            // Create a dummy timeout object for success state
            Timeout(
                id = timeoutId,
                userId = "",
                connectionId = _currentConnectionId.value ?: "",
                isActive = false
            )
        )
    }

    /**
     * Gets user-friendly error message for timeout errors
     */
    fun getTimeoutErrorMessage(error: Throwable): String {
        return when (error) {
            is TimeoutException.DailyLimitExceeded -> 
                "You have already used your daily timeout allowance. Try again tomorrow."
            is TimeoutException.TimeoutAlreadyActive -> 
                "A timeout is already active for this connection."
            is TimeoutException.InvalidRequest -> 
                "Invalid timeout request: ${error.message}"
            else -> 
                "Failed to request timeout. Please try again."
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "TimeoutViewModel cleared")
    }
}