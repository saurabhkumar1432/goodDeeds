package com.browniepoints.app.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.browniepoints.app.data.model.Transaction
import com.browniepoints.app.data.model.User
import com.browniepoints.app.presentation.viewmodel.DeductPointsState
import com.browniepoints.app.presentation.ui.components.TimeoutDisabledOverlay
import com.browniepoints.app.presentation.ui.theme.BrowniePointsAppTheme
import com.browniepoints.app.presentation.viewmodel.AuthViewModel
import com.browniepoints.app.presentation.viewmodel.ConnectionViewModel
import com.browniepoints.app.presentation.viewmodel.TimeoutViewModel
import com.browniepoints.app.presentation.viewmodel.TransactionViewModel
import com.google.firebase.Timestamp

/**
 * Deduct Points screen that allows users to deduct brownie points from their connected partner
 * Features point amount selector, required reason input, and confirmation dialog with warnings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeductPointsScreen(
    onNavigateBack: () -> Unit,
    onPointsDeducted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    timeoutViewModel: TimeoutViewModel = hiltViewModel(),
    connectionViewModel: ConnectionViewModel = hiltViewModel()
) {
    val deductPointsState by viewModel.deductPointsState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val connectionState by connectionViewModel.uiState.collectAsState()
    val transactionsDisabled by timeoutViewModel.transactionsDisabled.collectAsState()
    val remainingTimeMs by timeoutViewModel.remainingTimeMs.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    // Load user data when screen opens
    LaunchedEffect(authState.currentUser?.uid) {
        authState.currentUser?.uid?.let { userId ->
            viewModel.loadDeductPointsData(userId)
            connectionViewModel.loadConnection(userId)
        }
    }
    
    // Set connection ID for timeout monitoring when connection is loaded
    LaunchedEffect(connectionState.connection?.id, authState.currentUser?.uid) {
        val connectionId = connectionState.connection?.id
        val userId = authState.currentUser?.uid
        if (connectionId != null && userId != null) {
            timeoutViewModel.setConnectionId(connectionId, userId)
        }
    }

    // Show error messages in snackbar
    LaunchedEffect(deductPointsState.error) {
        deductPointsState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearDeductError()
        }
    }

    // Show success message and navigate back after successful transaction
    LaunchedEffect(deductPointsState.lastTransaction) {
        deductPointsState.lastTransaction?.let { transaction ->
            snackbarHostState.showSnackbar("Deducted ${kotlin.math.abs(transaction.points)} points")
            viewModel.clearLastDeductTransaction()
            onPointsDeducted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Deduct Points",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (deductPointsState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (deductPointsState.connectedPartner == null) {
                NoPartnerMessage(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                DeductPointsContent(
                    state = deductPointsState,
                    onPointsChange = viewModel::updateDeductPointsAmount,
                    onReasonChange = viewModel::updateDeductReason,
                    onDeductPoints = { showConfirmationDialog = true },
                    modifier = Modifier.padding(24.dp)
                )
            }
            
            // Timeout disabled overlay
            TimeoutDisabledOverlay(
                isVisible = transactionsDisabled,
                remainingTimeMs = remainingTimeMs
            )
        }
    }

    // Confirmation dialog with warning
    if (showConfirmationDialog) {
        DeductPointsConfirmationDialog(
            points = deductPointsState.pointsAmount,
            reason = deductPointsState.reason,
            partnerName = deductPointsState.connectedPartner?.displayName ?: "Partner",
            onConfirm = {
                viewModel.deductPoints()
                showConfirmationDialog = false
            },
            onDismiss = { showConfirmationDialog = false }
        )
    }
}

@Composable
private fun DeductPointsContent(
    state: DeductPointsState,
    onPointsChange: (Int) -> Unit,
    onReasonChange: (String) -> Unit,
    onDeductPoints: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Warning message
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Deducting Points",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "This will remove points from your partner's balance. Please provide a reason.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Partner profile
        state.connectedPartner?.let { partner ->
            PartnerProfileCard(
                partner = partner,
                isDeductMode = true
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Points selector
        Text(
            text = "Points to Deduct",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        PointsSelector(
            selectedPoints = state.pointsAmount,
            onPointsSelected = onPointsChange,
            isDeductMode = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Reason input (required)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Reason for Deduction (Required)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.reason,
                    onValueChange = onReasonChange,
                    placeholder = { Text("Explain why you're deducting points...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 4,
                    isError = state.hasValidationError || (state.reason.isBlank()),
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (state.hasValidationError && state.validationError != null) {
                                Text(
                                    text = state.validationError,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else if (state.reason.isBlank()) {
                                Text(
                                    text = "Reason is required",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }
                            
                            Text(
                                text = "${state.remainingReasonChars}/${Transaction.MAX_MESSAGE_LENGTH}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.remainingReasonChars < 20) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Deduct button
        Button(
            onClick = onDeductPoints,
            enabled = state.isFormValid && !state.isDeductingPoints,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isDeductingPoints) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onError
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Deducting...")
            } else {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Deduct ${state.pointsAmount} Point${if (state.pointsAmount != 1) "s" else ""}")
            }
        }
    }
}

@Composable
private fun PartnerProfileCard(
    partner: User,
    isDeductMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDeductMode) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                           else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile picture or placeholder
            if (partner.photoUrl != null) {
                AsyncImage(
                    model = partner.photoUrl,
                    contentDescription = "Partner profile picture",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Default profile picture",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = partner.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Current Balance: ${partner.totalPointsReceived} points",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PointsSelector(
    selectedPoints: Int,
    onPointsSelected: (Int) -> Unit,
    isDeductMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items((1..10).toList()) { points ->
            PointsSelectorItem(
                points = points,
                isSelected = selectedPoints == points,
                onClick = { onPointsSelected(points) },
                isDeductMode = isDeductMode
            )
        }
    }
}

@Composable
private fun PointsSelectorItem(
    points: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDeductMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSelected && isDeductMode -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    
    val contentColor = when {
        isSelected && isDeductMode -> MaterialTheme.colorScheme.onError
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    val borderColor = if (isDeductMode) MaterialTheme.colorScheme.error 
                     else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(
                width = if (isSelected) 0.dp else 1.dp,
                color = borderColor,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = points.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
private fun DeductPointsConfirmationDialog(
    points: Int,
    reason: String,
    partnerName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "⚠️ Deduct Points?",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "You are about to deduct $points point${if (points != 1) "s" else ""} from $partnerName.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Reason:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"$reason\"",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "⚠️ This will reduce their balance and cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Are you sure you want to proceed?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Deduct Points")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NoPartnerMessage(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No Connected Partner",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You need to connect with someone before you can deduct points.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DeductPointsScreenPreview() {
    BrowniePointsAppTheme {
        DeductPointsScreen(
            onNavigateBack = {},
            onPointsDeducted = {}
        )
    }
}