package com.browniepoints.app.data.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UiStateManager @Inject constructor(
    private val networkMonitorService: NetworkMonitorService,
    private val offlineSyncManager: OfflineSyncManager
) {
    
    private val _loadingStates = MutableStateFlow<Map<String, LoadingState>>(emptyMap())
    val loadingStates: StateFlow<Map<String, LoadingState>> = _loadingStates.asStateFlow()
    
    private val _globalLoadingState = MutableStateFlow(GlobalLoadingState.IDLE)
    val globalLoadingState: StateFlow<GlobalLoadingState> = _globalLoadingState.asStateFlow()
    
    private val _errorStates = MutableStateFlow<Map<String, UiError>>(emptyMap())
    val errorStates: StateFlow<Map<String, UiError>> = _errorStates.asStateFlow()
    
    private val _successMessages = MutableStateFlow<Map<String, String>>(emptyMap())
    val successMessages: StateFlow<Map<String, String>> = _successMessages.asStateFlow()
    
    // Combined UI state that includes network and sync status
    val combinedUiState = combine(
        globalLoadingState,
        networkMonitorService.isOnline,
        offlineSyncManager.syncStatus,
        offlineSyncManager.hasPendingWrites
    ) { loading, isOnline, syncStatus, hasPendingWrites ->
        CombinedUiState(
            globalLoading = loading,
            isOnline = isOnline,
            syncStatus = syncStatus,
            hasPendingWrites = hasPendingWrites
        )
    }
    
    /**
     * Sets loading state for a specific operation
     */
    fun setLoading(operationId: String, state: LoadingState) {
        val currentStates = _loadingStates.value.toMutableMap()
        if (state == LoadingState.IDLE) {
            currentStates.remove(operationId)
        } else {
            currentStates[operationId] = state
        }
        _loadingStates.value = currentStates
        
        // Update global loading state
        updateGlobalLoadingState()
    }
    
    /**
     * Sets loading state with a message
     */
    fun setLoadingWithMessage(operationId: String, message: String) {
        setLoading(operationId, LoadingState.LOADING_WITH_MESSAGE(message))
    }
    
    /**
     * Sets an error state for a specific operation
     */
    fun setError(operationId: String, error: UiError) {
        val currentErrors = _errorStates.value.toMutableMap()
        currentErrors[operationId] = error
        _errorStates.value = currentErrors
        
        // Clear loading state for this operation
        setLoading(operationId, LoadingState.IDLE)
    }
    
    /**
     * Clears error state for a specific operation
     */
    fun clearError(operationId: String) {
        val currentErrors = _errorStates.value.toMutableMap()
        currentErrors.remove(operationId)
        _errorStates.value = currentErrors
    }
    
    /**
     * Sets a success message for a specific operation
     */
    fun setSuccess(operationId: String, message: String) {
        val currentMessages = _successMessages.value.toMutableMap()
        currentMessages[operationId] = message
        _successMessages.value = currentMessages
        
        // Clear loading state for this operation
        setLoading(operationId, LoadingState.IDLE)
    }
    
    /**
     * Clears success message for a specific operation
     */
    fun clearSuccess(operationId: String) {
        val currentMessages = _successMessages.value.toMutableMap()
        currentMessages.remove(operationId)
        _successMessages.value = currentMessages
    }
    
    /**
     * Clears all states for a specific operation
     */
    fun clearAllStates(operationId: String) {
        setLoading(operationId, LoadingState.IDLE)
        clearError(operationId)
        clearSuccess(operationId)
    }
    
    /**
     * Gets the current loading state for an operation
     */
    fun getLoadingState(operationId: String): LoadingState {
        return _loadingStates.value[operationId] ?: LoadingState.IDLE
    }
    
    /**
     * Gets the current error state for an operation
     */
    fun getError(operationId: String): UiError? {
        return _errorStates.value[operationId]
    }
    
    /**
     * Gets the current success message for an operation
     */
    fun getSuccessMessage(operationId: String): String? {
        return _successMessages.value[operationId]
    }
    
    /**
     * Checks if any operation is currently loading
     */
    fun isAnyOperationLoading(): Boolean {
        return _loadingStates.value.values.any { it != LoadingState.IDLE }
    }
    
    /**
     * Checks if a specific operation is loading
     */
    fun isOperationLoading(operationId: String): Boolean {
        return getLoadingState(operationId) != LoadingState.IDLE
    }
    
    /**
     * Gets all currently loading operations
     */
    fun getLoadingOperations(): Map<String, LoadingState> {
        return _loadingStates.value.filter { it.value != LoadingState.IDLE }
    }
    
    private fun updateGlobalLoadingState() {
        val loadingOperations = getLoadingOperations()
        
        _globalLoadingState.value = when {
            loadingOperations.isEmpty() -> GlobalLoadingState.IDLE
            loadingOperations.values.any { it is LoadingState.LOADING_CRITICAL } -> GlobalLoadingState.CRITICAL_LOADING
            loadingOperations.size == 1 -> GlobalLoadingState.SINGLE_OPERATION
            else -> GlobalLoadingState.MULTIPLE_OPERATIONS
        }
    }
}

sealed class LoadingState {
    object IDLE : LoadingState()
    object LOADING : LoadingState()
    data class LOADING_WITH_MESSAGE(val message: String) : LoadingState()
    data class LOADING_CRITICAL(val message: String) : LoadingState() // Blocks UI interaction
}

enum class GlobalLoadingState {
    IDLE,
    SINGLE_OPERATION,
    MULTIPLE_OPERATIONS,
    CRITICAL_LOADING
}

data class UiError(
    val message: String,
    val title: String? = null,
    val isRetryable: Boolean = false,
    val severity: ErrorSeverity = ErrorSeverity.MEDIUM,
    val timestamp: Long = System.currentTimeMillis()
)

data class CombinedUiState(
    val globalLoading: GlobalLoadingState,
    val isOnline: Boolean,
    val syncStatus: SyncStatus,
    val hasPendingWrites: Boolean
) {
    val showOfflineIndicator: Boolean
        get() = !isOnline
    
    val showSyncIndicator: Boolean
        get() = syncStatus == SyncStatus.SYNCING || hasPendingWrites
    
    val showErrorIndicator: Boolean
        get() = syncStatus == SyncStatus.ERROR
    
    val isFullyOperational: Boolean
        get() = isOnline && syncStatus == SyncStatus.SYNCED && !hasPendingWrites
    
    val statusMessage: String
        get() = when {
            !isOnline && hasPendingWrites -> "Offline - Changes will sync when connected"
            !isOnline -> "Offline"
            syncStatus == SyncStatus.SYNCING -> "Syncing..."
            syncStatus == SyncStatus.ERROR -> "Sync error"
            hasPendingWrites -> "Syncing changes..."
            else -> "Connected"
        }
}

// Common operation IDs for consistency
object OperationIds {
    const val SIGN_IN = "sign_in"
    const val SIGN_OUT = "sign_out"
    const val LOAD_USER_PROFILE = "load_user_profile"
    const val CREATE_CONNECTION = "create_connection"
    const val LOAD_CONNECTION = "load_connection"
    const val GIVE_POINTS = "give_points"
    const val DEDUCT_POINTS = "deduct_points"
    const val LOAD_TRANSACTIONS = "load_transactions"
    const val REQUEST_TIMEOUT = "request_timeout"
    const val LOAD_TIMEOUT_STATUS = "load_timeout_status"
    const val SEND_NOTIFICATION = "send_notification"
    const val SYNC_DATA = "sync_data"
}