package com.browniepoints.app.presentation.ui.common

import com.browniepoints.app.data.error.AppError

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val error: AppError) : UiState<Nothing>()
    object Idle : UiState<Nothing>()
}

// Extension functions for easier state handling
fun <T> UiState<T>.isLoading(): Boolean = this is UiState.Loading
fun <T> UiState<T>.isSuccess(): Boolean = this is UiState.Success
fun <T> UiState<T>.isError(): Boolean = this is UiState.Error
fun <T> UiState<T>.isIdle(): Boolean = this is UiState.Idle

fun <T> UiState<T>.getDataOrNull(): T? = when (this) {
    is UiState.Success -> data
    else -> null
}

fun <T> UiState<T>.getErrorOrNull(): AppError? = when (this) {
    is UiState.Error -> error
    else -> null
}

// Helper function to create UiState from Result
fun <T> Result<T>.toUiState(): UiState<T> {
    return fold(
        onSuccess = { UiState.Success(it) },
        onFailure = { 
            val appError = if (it is AppError) it else AppError.UnknownError(it.message ?: "Unknown error", it)
            UiState.Error(appError)
        }
    )
}