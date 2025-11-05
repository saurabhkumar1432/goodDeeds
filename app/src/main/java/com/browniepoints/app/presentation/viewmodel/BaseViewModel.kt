package com.browniepoints.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browniepoints.app.data.error.AppError
import com.browniepoints.app.data.service.ErrorHandlerService
import com.browniepoints.app.data.service.NetworkMonitorService
import com.browniepoints.app.data.service.OfflineSyncManager
import com.browniepoints.app.data.service.SyncStatus
import com.browniepoints.app.presentation.ui.common.UiState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

abstract class BaseViewModel(
    protected val networkMonitorService: NetworkMonitorService,
    protected val offlineSyncManager: OfflineSyncManager,
    protected val errorHandlerService: ErrorHandlerService
) : ViewModel() {
    
    // Network and sync status
    val isOnline: StateFlow<Boolean> = networkMonitorService.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = networkMonitorService.isCurrentlyOnline()
        )
    
    val syncStatus: StateFlow<SyncStatus> = offlineSyncManager.syncStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncStatus.SYNCED
        )
    
    val isOffline: StateFlow<Boolean> = offlineSyncManager.isOffline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    // Error handling
    private val _errorState = MutableStateFlow<AppError?>(null)
    val errorState: StateFlow<AppError?> = _errorState.asStateFlow()
    
    protected fun handleError(error: Throwable) {
        val appError = if (error is AppError) error else AppError.UnknownError(error.message ?: "Unknown error", error)
        _errorState.value = appError
    }
    
    protected fun clearError() {
        _errorState.value = null
    }
    
    protected fun retryLastOperation() {
        // Override in subclasses to implement retry logic
    }
    
    // Helper function to execute operations with error handling
    protected fun <T> executeWithErrorHandling(
        operation: suspend () -> Result<T>,
        onSuccess: (T) -> Unit = {},
        onError: (AppError) -> Unit = { handleError(it) }
    ) {
        viewModelScope.launch {
            operation()
                .onSuccess { onSuccess(it) }
                .onFailure { 
                    val appError = if (it is AppError) it else AppError.UnknownError(it.message ?: "Unknown error", it)
                    onError(appError)
                }
        }
    }
    
    // Helper function to convert Result to UiState
    protected fun <T> Result<T>.toUiState(): UiState<T> {
        return fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { 
                val appError = if (it is AppError) it else AppError.UnknownError(it.message ?: "Unknown error", it)
                UiState.Error(appError)
            }
        )
    }
    
    // Force sync when coming back online
    protected fun forceSyncWhenOnline() {
        offlineSyncManager.forceSyncWhenOnline()
    }
    
    // Get user-friendly error message
    protected fun getErrorMessage(error: AppError): String {
        return errorHandlerService.getErrorMessage(error)
    }
    
    // Check if error is retryable
    protected fun isRetryableError(error: AppError): Boolean {
        return errorHandlerService.shouldShowRetryButton(error)
    }
}