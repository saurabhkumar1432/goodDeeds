package com.browniepoints.app.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browniepoints.app.data.service.CombinedUiState
import com.browniepoints.app.data.service.ErrorHandlerService
import com.browniepoints.app.data.service.LoadingState
import com.browniepoints.app.data.service.UiError
import com.browniepoints.app.data.service.UiStateManager
import com.browniepoints.app.presentation.ui.common.UiState
import com.browniepoints.app.presentation.ui.common.getDataOrNull
import com.browniepoints.app.presentation.ui.common.isLoading

@Composable
fun <T> UiStateHandler(
    uiState: UiState<T>,
    errorHandlerService: ErrorHandlerService,
    modifier: Modifier = Modifier,
    loadingMessage: String = "Loading...",
    onRetry: (() -> Unit)? = null,
    onError: ((com.browniepoints.app.data.error.AppError) -> Unit)? = null,
    content: @Composable (T) -> Unit
) {
    Box(modifier = modifier) {
        when (uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(message = loadingMessage)
                }
            }
            
            is UiState.Success -> {
                content(uiState.data)
            }
            
            is UiState.Error -> {
                onError?.invoke(uiState.error)
                EmptyStateWithError(
                    error = uiState.error,
                    errorHandlerService = errorHandlerService,
                    onRetry = onRetry
                )
            }
            
            is UiState.Idle -> {
                // Show nothing or placeholder content
            }
        }
    }
}

@Composable
fun <T> UiStateHandlerWithOverlay(
    uiState: UiState<T>,
    errorHandlerService: ErrorHandlerService,
    modifier: Modifier = Modifier,
    loadingMessage: String = "Loading...",
    showErrorDialog: Boolean = true,
    onRetry: (() -> Unit)? = null,
    onErrorDismiss: (() -> Unit)? = null,
    content: @Composable (T?) -> Unit
) {
    var showError by remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState) {
        showError = uiState is UiState.Error && showErrorDialog
    }
    
    Box(modifier = modifier) {
        // Always show content (even if loading or error)
        content(uiState.getDataOrNull())
        
        // Show loading overlay
        LoadingOverlay(
            isVisible = uiState.isLoading(),
            message = loadingMessage
        )
        
        // Show error dialog
        if (showError && uiState is UiState.Error) {
            ErrorDialog(
                error = uiState.error,
                errorHandlerService = errorHandlerService,
                onRetry = onRetry,
                onDismiss = {
                    showError = false
                    onErrorDismiss?.invoke()
                }
            )
        }
    }
}

@Composable
fun <T> UiStateHandlerWithInlineError(
    uiState: UiState<T>,
    errorHandlerService: ErrorHandlerService,
    modifier: Modifier = Modifier,
    loadingMessage: String = "Loading...",
    onRetry: (() -> Unit)? = null,
    content: @Composable (T?) -> Unit
) {
    Column(modifier = modifier) {
        // Show inline error message
        if (uiState is UiState.Error) {
            InlineErrorMessage(
                error = uiState.error,
                errorHandlerService = errorHandlerService,
                onRetry = onRetry
            )
        }
        
        // Show loading indicator
        if (uiState.isLoading()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(message = loadingMessage)
            }
        }
        
        // Show content
        content(uiState.getDataOrNull())
    }
}

// New enhanced UI state handlers using UiStateManager

@Composable
fun EnhancedUiStateHandler(
    uiStateManager: UiStateManager,
    operationId: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val loadingState by uiStateManager.loadingStates.collectAsState()
    val errorStates by uiStateManager.errorStates.collectAsState()
    val successMessages by uiStateManager.successMessages.collectAsState()
    
    val currentLoadingState = loadingState[operationId] ?: LoadingState.IDLE
    val currentError = errorStates[operationId]
    val currentSuccess = successMessages[operationId]
    
    Box(modifier = modifier) {
        content()
        
        // Show loading overlay
        when (currentLoadingState) {
            is LoadingState.LOADING -> {
                LoadingOverlay(isVisible = true, message = "Loading...")
            }
            is LoadingState.LOADING_WITH_MESSAGE -> {
                LoadingOverlay(isVisible = true, message = currentLoadingState.message)
            }
            is LoadingState.LOADING_CRITICAL -> {
                LoadingDialog(isVisible = true, message = currentLoadingState.message)
            }
            LoadingState.IDLE -> { /* No loading */ }
        }
        
        // Show error message
        currentError?.let { error ->
            InlineErrorMessage(
                error = com.browniepoints.app.data.error.AppError.UnknownError(error.message),
                errorHandlerService = ErrorHandlerService(
                    networkMonitorService = com.browniepoints.app.data.service.NetworkMonitorService(
                        androidx.compose.ui.platform.LocalContext.current
                    )
                ),
                onRetry = if (error.isRetryable) {
                    { uiStateManager.clearError(operationId) }
                } else null
            )
        }
        
        // Show success message
        currentSuccess?.let { message ->
            SuccessMessage(
                message = message,
                onDismiss = { uiStateManager.clearSuccess(operationId) }
            )
        }
    }
}

@Composable
fun CombinedUiStateIndicator(
    combinedUiState: CombinedUiState,
    modifier: Modifier = Modifier,
    onRetrySync: (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        // Offline indicator
        OfflineIndicator(
            isOffline = combinedUiState.showOfflineIndicator,
            onRetryClick = onRetrySync
        )
        
        // Sync status indicator
        SyncStatusIndicator(
            syncStatus = combinedUiState.syncStatus,
            onRetryClick = onRetrySync
        )
    }
}

@Composable
fun SuccessMessage(
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    androidx.compose.material3.Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Check,
                    contentDescription = "Success",
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Text(
                    text = message,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            if (onDismiss != null) {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    androidx.compose.material3.Text("Dismiss")
                }
            }
        }
    }
}