package com.browniepoints.app.data.error

sealed class AppError(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {
    
    // Network related errors
    data class NetworkError(
        val errorMessage: String = "Network connection failed",
        val throwable: Throwable? = null
    ) : AppError(errorMessage, throwable)
    
    data class NoInternetError(
        val errorMessage: String = "No internet connection available"
    ) : AppError(errorMessage)
    
    // Authentication errors
    data class AuthenticationError(
        val errorMessage: String = "Authentication failed",
        val throwable: Throwable? = null
    ) : AppError(errorMessage, throwable)
    
    data class UserNotSignedInError(
        val errorMessage: String = "User is not signed in"
    ) : AppError(errorMessage)
    
    // Firestore errors
    data class FirestoreError(
        val errorMessage: String = "Database operation failed",
        val throwable: Throwable? = null
    ) : AppError(errorMessage, throwable)
    
    data class DocumentNotFoundError(
        val errorMessage: String = "Requested document not found"
    ) : AppError(errorMessage)
    
    data class PermissionDeniedError(
        val errorMessage: String = "Permission denied for this operation"
    ) : AppError(errorMessage)
    
    // Validation errors
    data class ValidationError(
        val errorMessage: String,
        val field: String? = null
    ) : AppError(errorMessage)
    
    // Connection errors
    data class ConnectionError(
        val errorMessage: String = "Failed to establish connection"
    ) : AppError(errorMessage)
    
    data class InvalidMatchingCodeError(
        val errorMessage: String = "Invalid matching code"
    ) : AppError(errorMessage)
    
    // Transaction errors
    data class InsufficientPointsError(
        val errorMessage: String = "Insufficient points for this transaction"
    ) : AppError(errorMessage)
    
    data class TransactionFailedError(
        val errorMessage: String = "Transaction could not be completed",
        val throwable: Throwable? = null
    ) : AppError(errorMessage, throwable)
    
    // Generic errors
    data class UnknownError(
        val errorMessage: String = "An unexpected error occurred",
        val throwable: Throwable? = null
    ) : AppError(errorMessage, throwable)
    
    data class TimeoutError(
        val errorMessage: String = "Operation timed out"
    ) : AppError(errorMessage)
}

// Extension function to convert exceptions to AppError
fun Throwable.toAppError(): AppError {
    return when (this) {
        is com.google.firebase.auth.FirebaseAuthException -> {
            AppError.AuthenticationError(
                errorMessage = this.message ?: "Authentication failed",
                throwable = this
            )
        }
        is com.google.firebase.firestore.FirebaseFirestoreException -> {
            when (this.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    AppError.PermissionDeniedError("You don't have permission to perform this action")
                }
                com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND -> {
                    AppError.DocumentNotFoundError("The requested data was not found")
                }
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> {
                    AppError.NetworkError("Service is currently unavailable", this)
                }
                com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> {
                    AppError.TimeoutError("Operation timed out")
                }
                else -> {
                    AppError.FirestoreError(
                        errorMessage = this.message ?: "Database operation failed",
                        throwable = this
                    )
                }
            }
        }
        is java.net.UnknownHostException,
        is java.net.ConnectException,
        is java.net.SocketTimeoutException -> {
            AppError.NetworkError("Network connection failed", this)
        }
        else -> {
            AppError.UnknownError(
                errorMessage = this.message ?: "An unexpected error occurred",
                throwable = this
            )
        }
    }
}