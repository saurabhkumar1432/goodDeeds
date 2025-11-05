package com.browniepoints.app.data.service

import com.browniepoints.app.data.error.AppError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorHandlerService @Inject constructor(
    private val networkMonitorService: NetworkMonitorService
) {
    
    fun getErrorMessage(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> {
                if (networkMonitorService.isCurrentlyOnline()) {
                    "Connection to server failed. Please try again."
                } else {
                    "No internet connection. Please check your connection and try again."
                }
            }
            is AppError.NoInternetError -> "No internet connection. Please connect to the internet and try again."
            
            is AppError.AuthenticationError -> "Sign-in failed. Please try signing in again."
            is AppError.UserNotSignedInError -> "Please sign in to continue."
            
            is AppError.FirestoreError -> {
                if (networkMonitorService.isCurrentlyOnline()) {
                    "Unable to sync your data. Please try again."
                } else {
                    "Data will sync when connection is restored."
                }
            }
            is AppError.DocumentNotFoundError -> "The requested information could not be found."
            is AppError.PermissionDeniedError -> "You don't have permission to perform this action."
            
            is AppError.ValidationError -> {
                if (error.field != null) {
                    "Invalid ${error.field}: ${error.message}"
                } else {
                    error.message
                }
            }
            
            is AppError.ConnectionError -> "Unable to connect with your partner. Please check your matching code and try again."
            is AppError.InvalidMatchingCodeError -> "Invalid matching code. Please check the code and try again."
            
            is AppError.InsufficientPointsError -> "You don't have enough points for this transaction."
            is AppError.TransactionFailedError -> {
                if (networkMonitorService.isCurrentlyOnline()) {
                    "Unable to complete the transaction. Please try again."
                } else {
                    "Transaction will be processed when connection is restored."
                }
            }
            
            is AppError.TimeoutError -> "The operation took too long. Please try again."
            is AppError.UnknownError -> "Something went wrong. Please try again."
        }
    }
    
    fun getErrorTitle(error: AppError): String {
        return when (error) {
            is AppError.NetworkError,
            is AppError.NoInternetError -> "Connection Problem"
            
            is AppError.AuthenticationError,
            is AppError.UserNotSignedInError -> "Sign-In Required"
            
            is AppError.FirestoreError,
            is AppError.DocumentNotFoundError,
            is AppError.PermissionDeniedError -> "Data Error"
            
            is AppError.ValidationError -> "Invalid Input"
            
            is AppError.ConnectionError,
            is AppError.InvalidMatchingCodeError -> "Connection Error"
            
            is AppError.InsufficientPointsError,
            is AppError.TransactionFailedError -> "Transaction Error"
            
            is AppError.TimeoutError -> "Timeout"
            is AppError.UnknownError -> "Error"
        }
    }
    
    fun shouldShowRetryButton(error: AppError): Boolean {
        return when (error) {
            is AppError.NetworkError,
            is AppError.NoInternetError,
            is AppError.FirestoreError,
            is AppError.TimeoutError,
            is AppError.UnknownError,
            is AppError.TransactionFailedError -> true
            
            is AppError.AuthenticationError,
            is AppError.UserNotSignedInError,
            is AppError.DocumentNotFoundError,
            is AppError.PermissionDeniedError,
            is AppError.ValidationError,
            is AppError.ConnectionError,
            is AppError.InvalidMatchingCodeError,
            is AppError.InsufficientPointsError -> false
        }
    }
    
    fun getRetryButtonText(error: AppError): String {
        return when (error) {
            is AppError.NetworkError,
            is AppError.NoInternetError -> "Retry Connection"
            
            is AppError.FirestoreError -> "Retry Sync"
            
            is AppError.TransactionFailedError -> "Retry Transaction"
            
            else -> "Try Again"
        }
    }
    
    fun getRecoveryActions(error: AppError): List<RecoveryAction> {
        return when (error) {
            is AppError.NetworkError,
            is AppError.NoInternetError -> listOf(
                RecoveryAction.RETRY,
                RecoveryAction.CHECK_CONNECTION,
                RecoveryAction.WORK_OFFLINE
            )
            
            is AppError.AuthenticationError -> listOf(
                RecoveryAction.RETRY,
                RecoveryAction.SIGN_OUT_AND_IN
            )
            
            is AppError.UserNotSignedInError -> listOf(
                RecoveryAction.SIGN_IN
            )
            
            is AppError.FirestoreError -> listOf(
                RecoveryAction.RETRY,
                RecoveryAction.FORCE_SYNC,
                RecoveryAction.WORK_OFFLINE
            )
            
            is AppError.ValidationError -> listOf(
                RecoveryAction.FIX_INPUT
            )
            
            is AppError.ConnectionError,
            is AppError.InvalidMatchingCodeError -> listOf(
                RecoveryAction.CHECK_CODE,
                RecoveryAction.RETRY
            )
            
            is AppError.TransactionFailedError -> listOf(
                RecoveryAction.RETRY,
                RecoveryAction.CHECK_CONNECTION
            )
            
            is AppError.TimeoutError -> listOf(
                RecoveryAction.RETRY,
                RecoveryAction.CHECK_CONNECTION
            )
            
            else -> listOf(RecoveryAction.RETRY)
        }
    }
    
    fun getErrorSeverity(error: AppError): ErrorSeverity {
        return when (error) {
            is AppError.NetworkError,
            is AppError.NoInternetError,
            is AppError.TimeoutError -> ErrorSeverity.WARNING
            
            is AppError.AuthenticationError,
            is AppError.UserNotSignedInError -> ErrorSeverity.HIGH
            
            is AppError.FirestoreError,
            is AppError.TransactionFailedError -> ErrorSeverity.MEDIUM
            
            is AppError.ValidationError,
            is AppError.ConnectionError,
            is AppError.InvalidMatchingCodeError -> ErrorSeverity.LOW
            
            is AppError.DocumentNotFoundError,
            is AppError.PermissionDeniedError,
            is AppError.InsufficientPointsError -> ErrorSeverity.LOW
            
            is AppError.UnknownError -> ErrorSeverity.HIGH
        }
    }
    
    fun shouldShowOfflineMessage(error: AppError): Boolean {
        return when (error) {
            is AppError.NetworkError,
            is AppError.NoInternetError,
            is AppError.FirestoreError,
            is AppError.TransactionFailedError -> !networkMonitorService.isCurrentlyOnline()
            else -> false
        }
    }
}

enum class RecoveryAction {
    RETRY,
    CHECK_CONNECTION,
    WORK_OFFLINE,
    SIGN_IN,
    SIGN_OUT_AND_IN,
    FORCE_SYNC,
    FIX_INPUT,
    CHECK_CODE
}

enum class ErrorSeverity {
    LOW,     // User can continue with limited functionality
    MEDIUM,  // Some features may not work
    WARNING, // Temporary issue, likely to resolve
    HIGH     // Critical error, blocks main functionality
}