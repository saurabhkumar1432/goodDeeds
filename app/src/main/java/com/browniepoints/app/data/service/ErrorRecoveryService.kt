package com.browniepoints.app.data.service

import android.util.Log
import com.browniepoints.app.data.error.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorRecoveryService @Inject constructor(
    private val errorHandlerService: ErrorHandlerService,
    private val retryManager: RetryManager,
    private val offlineSyncManager: OfflineSyncManager,
    private val networkMonitorService: NetworkMonitorService
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _recoveryEvents = MutableSharedFlow<RecoveryEvent>()
    val recoveryEvents: SharedFlow<RecoveryEvent> = _recoveryEvents.asSharedFlow()
    
    companion object {
        private const val TAG = "ErrorRecoveryService"
    }
    
    /**
     * Handles an error and attempts automatic recovery if possible
     */
    suspend fun handleError(
        error: AppError,
        context: String = "Unknown",
        autoRetry: Boolean = true
    ): ErrorRecoveryResult {
        Log.w(TAG, "Handling error in context '$context': ${error.message}")
        
        val severity = errorHandlerService.getErrorSeverity(error)
        val recoveryActions = errorHandlerService.getRecoveryActions(error)
        
        // Emit error event
        _recoveryEvents.emit(
            RecoveryEvent.ErrorOccurred(
                error = error,
                context = context,
                severity = severity,
                timestamp = System.currentTimeMillis()
            )
        )
        
        // Attempt automatic recovery for certain error types
        if (autoRetry && canAutoRecover(error)) {
            return attemptAutoRecovery(error, context)
        }
        
        return ErrorRecoveryResult.ManualRecoveryRequired(
            error = error,
            message = errorHandlerService.getErrorMessage(error),
            title = errorHandlerService.getErrorTitle(error),
            actions = recoveryActions,
            severity = severity
        )
    }
    
    /**
     * Executes a manual recovery action
     */
    suspend fun executeRecoveryAction(
        action: RecoveryAction,
        error: AppError,
        context: String = "Manual Recovery"
    ): RecoveryActionResult {
        Log.d(TAG, "Executing recovery action: $action for error: ${error.message}")
        
        _recoveryEvents.emit(
            RecoveryEvent.RecoveryAttempted(
                action = action,
                error = error,
                context = context,
                timestamp = System.currentTimeMillis()
            )
        )
        
        return when (action) {
            RecoveryAction.RETRY -> {
                RecoveryActionResult.RetryRequested
            }
            
            RecoveryAction.CHECK_CONNECTION -> {
                val isOnline = networkMonitorService.isCurrentlyOnline()
                if (isOnline) {
                    RecoveryActionResult.Success("Connection is available")
                } else {
                    RecoveryActionResult.Failed("No internet connection")
                }
            }
            
            RecoveryAction.WORK_OFFLINE -> {
                RecoveryActionResult.Success("Working in offline mode")
            }
            
            RecoveryAction.FORCE_SYNC -> {
                try {
                    offlineSyncManager.forceSyncWhenOnline()
                    RecoveryActionResult.Success("Sync initiated")
                } catch (e: Exception) {
                    RecoveryActionResult.Failed("Sync failed: ${e.message}")
                }
            }
            
            RecoveryAction.SIGN_IN -> {
                RecoveryActionResult.NavigationRequired("sign_in")
            }
            
            RecoveryAction.SIGN_OUT_AND_IN -> {
                RecoveryActionResult.NavigationRequired("sign_out_and_in")
            }
            
            RecoveryAction.FIX_INPUT -> {
                RecoveryActionResult.Success("Please correct the input and try again")
            }
            
            RecoveryAction.CHECK_CODE -> {
                RecoveryActionResult.Success("Please verify the matching code and try again")
            }
        }
    }
    
    private suspend fun attemptAutoRecovery(
        error: AppError,
        context: String
    ): ErrorRecoveryResult {
        return when (error) {
            is AppError.NetworkError,
            is AppError.FirestoreError,
            is AppError.TimeoutError -> {
                Log.d(TAG, "Attempting auto-recovery with retry for: ${error.message}")
                
                val result = retryManager.executeWithNetworkRetry(maxRetries = 2) {
                    // This is a placeholder - the actual operation would be passed in
                    throw error // Re-throw to simulate failure for now
                }
                
                if (result.isSuccess) {
                    _recoveryEvents.emit(
                        RecoveryEvent.RecoverySucceeded(
                            error = error,
                            context = context,
                            method = "Auto-retry",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    ErrorRecoveryResult.AutoRecoverySucceeded
                } else {
                    ErrorRecoveryResult.AutoRecoveryFailed(
                        originalError = error,
                        message = errorHandlerService.getErrorMessage(error),
                        title = errorHandlerService.getErrorTitle(error),
                        actions = errorHandlerService.getRecoveryActions(error),
                        severity = errorHandlerService.getErrorSeverity(error)
                    )
                }
            }
            
            else -> {
                ErrorRecoveryResult.ManualRecoveryRequired(
                    error = error,
                    message = errorHandlerService.getErrorMessage(error),
                    title = errorHandlerService.getErrorTitle(error),
                    actions = errorHandlerService.getRecoveryActions(error),
                    severity = errorHandlerService.getErrorSeverity(error)
                )
            }
        }
    }
    
    private fun canAutoRecover(error: AppError): Boolean {
        return when (error) {
            is AppError.NetworkError,
            is AppError.FirestoreError,
            is AppError.TimeoutError -> true
            
            is AppError.TransactionFailedError -> networkMonitorService.isCurrentlyOnline()
            
            else -> false
        }
    }
    
    /**
     * Clears error recovery history (useful for testing or cleanup)
     */
    fun clearRecoveryHistory() {
        scope.launch {
            _recoveryEvents.emit(RecoveryEvent.HistoryCleared(System.currentTimeMillis()))
        }
    }
}

sealed class ErrorRecoveryResult {
    object AutoRecoverySucceeded : ErrorRecoveryResult()
    
    data class AutoRecoveryFailed(
        val originalError: AppError,
        val message: String,
        val title: String,
        val actions: List<RecoveryAction>,
        val severity: ErrorSeverity
    ) : ErrorRecoveryResult()
    
    data class ManualRecoveryRequired(
        val error: AppError,
        val message: String,
        val title: String,
        val actions: List<RecoveryAction>,
        val severity: ErrorSeverity
    ) : ErrorRecoveryResult()
}

sealed class RecoveryActionResult {
    object RetryRequested : RecoveryActionResult()
    data class Success(val message: String) : RecoveryActionResult()
    data class Failed(val message: String) : RecoveryActionResult()
    data class NavigationRequired(val destination: String) : RecoveryActionResult()
}

sealed class RecoveryEvent {
    data class ErrorOccurred(
        val error: AppError,
        val context: String,
        val severity: ErrorSeverity,
        val timestamp: Long
    ) : RecoveryEvent()
    
    data class RecoveryAttempted(
        val action: RecoveryAction,
        val error: AppError,
        val context: String,
        val timestamp: Long
    ) : RecoveryEvent()
    
    data class RecoverySucceeded(
        val error: AppError,
        val context: String,
        val method: String,
        val timestamp: Long
    ) : RecoveryEvent()
    
    data class HistoryCleared(
        val timestamp: Long
    ) : RecoveryEvent()
}