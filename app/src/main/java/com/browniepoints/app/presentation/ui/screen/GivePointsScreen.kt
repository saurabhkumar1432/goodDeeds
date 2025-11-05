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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.browniepoints.app.presentation.ui.theme.BrowniePointsAppTheme
import com.browniepoints.app.presentation.ui.components.TimeoutDisabledOverlay
import com.browniepoints.app.presentation.viewmodel.AuthViewModel
import com.browniepoints.app.presentation.viewmodel.ConnectionViewModel
import com.browniepoints.app.presentation.viewmodel.GivePointsState
import com.browniepoints.app.presentation.viewmodel.TimeoutViewModel
import com.browniepoints.app.presentation.viewmodel.TransactionViewModel
import com.google.firebase.Timestamp

/**
 * Give Points screen that allows users to give brownie points to their connected partner
 * Features point amount selector, optional message input, and confirmation dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GivePointsScreen(
    onNavigateBack: () -> Unit,
    onPointsGiven: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    timeoutViewModel: TimeoutViewModel = hiltViewModel(),
    connectionViewModel: ConnectionViewModel = hiltViewModel()
) {
    val givePointsState by viewModel.givePointsState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val connectionState by connectionViewModel.uiState.collectAsState()
    val transactionsDisabled by timeoutViewModel.transactionsDisabled.collectAsState()
    val remainingTimeMs by timeoutViewModel.remainingTimeMs.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    // Load user data when screen opens
    LaunchedEffect(authState.currentUser?.uid) {
        authState.currentUser?.uid?.let { userId ->
            viewModel.loadGivePointsData(userId)
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
    LaunchedEffect(givePointsState.error) {
        givePointsState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Show success message and navigate back after successful transaction
    LaunchedEffect(givePointsState.lastTransaction) {
        givePointsState.lastTransaction?.let { transaction ->
            snackbarHostState.showSnackbar("Successfully gave ${transaction.points} points!")
            viewModel.clearLastTransaction()
            onPointsGiven()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Give Points") },
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
            if (givePointsState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (givePointsState.connectedPartner == null) {
                NoPartnerMessage(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                GivePointsContent(
                    state = givePointsState,
                    onPointsChange = viewModel::updatePointsAmount,
                    onMessageChange = viewModel::updateMessage,
                    onGivePoints = { showConfirmationDialog = true },
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

    // Confirmation dialog
    if (showConfirmationDialog) {
        GivePointsConfirmationDialog(
            partner = givePointsState.connectedPartner,
            points = givePointsState.selectedPoints,
            message = givePointsState.message,
            isGivingPoints = givePointsState.isGivingPoints,
            onConfirm = {
                viewModel.givePoints()
                showConfirmationDialog = false
            },
            onDismiss = { showConfirmationDialog = false }
        )
    }
}

@Composable
private fun GivePointsContent(
    state: GivePointsState,
    onPointsChange: (Int) -> Unit,
    onMessageChange: (String) -> Unit,
    onGivePoints: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Partner profile card
        PartnerProfileCard(
            partner = state.connectedPartner!!,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Points selector
        PointsSelectorCard(
            selectedPoints = state.selectedPoints,
            onPointsChange = onPointsChange,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Message input
        MessageInputCard(
            message = state.message,
            onMessageChange = onMessageChange,
            remainingChars = state.remainingMessageChars,
            hasError = state.hasValidationError,
            errorMessage = state.validationError,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Give points button
        Button(
            onClick = onGivePoints,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.isFormValid && !state.isGivingPoints
        ) {
            if (state.isGivingPoints) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Giving Points...")
            } else {
                Text("Give ${state.selectedPoints} Point${if (state.selectedPoints != 1) "s" else ""}")
            }
        }
    }
}

@Composable
private fun PartnerProfileCard(
    partner: User,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Giving points to:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Text(
                text = "Total points: ${partner.totalPointsReceived}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun PointsSelectorCard(
    selectedPoints: Int,
    onPointsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "How many points?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items((Transaction.MIN_POINTS..Transaction.MAX_POINTS).toList()) { points ->
                    PointsButton(
                        points = points,
                        isSelected = points == selectedPoints,
                        onClick = { onPointsChange(points) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select between ${Transaction.MIN_POINTS} and ${Transaction.MAX_POINTS} points",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PointsButton(
    points: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(
                width = if (isSelected) 0.dp else 1.dp,
                color = MaterialTheme.colorScheme.outline,
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
private fun MessageInputCard(
    message: String,
    onMessageChange: (String) -> Unit,
    remainingChars: Int,
    hasError: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Add a message (optional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                placeholder = { Text("Why are you giving these points?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 4,
                isError = hasError,
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (hasError && errorMessage != null) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        
                        Text(
                            text = "$remainingChars/${Transaction.MAX_MESSAGE_LENGTH}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (remainingChars < 20) {
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
}

@Composable
private fun NoPartnerMessage(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🤝",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Connected Partner",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You need to connect with someone before you can give points. Go to the connection screen to find a partner.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GivePointsConfirmationDialog(
    partner: User?,
    points: Int,
    message: String,
    isGivingPoints: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isGivingPoints) onDismiss() },
        title = {
            Text("Confirm Give Points")
        },
        text = {
            Column {
                Text(
                    text = "Give $points point${if (points != 1) "s" else ""} to ${partner?.displayName}?",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "This will increase their total points from ${partner?.totalPointsReceived ?: 0} to ${(partner?.totalPointsReceived ?: 0) + points}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (message.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Message:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\"$message\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isGivingPoints
            ) {
                if (isGivingPoints) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Giving...")
                } else {
                    Text("Give Points")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isGivingPoints
            ) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun GivePointsScreenPreview() {
    BrowniePointsAppTheme {
        GivePointsScreen(
            onNavigateBack = {},
            onPointsGiven = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PartnerProfileCardPreview() {
    BrowniePointsAppTheme {
        PartnerProfileCard(
            partner = User(
                uid = "456",
                displayName = "Jane Smith",
                email = "jane@example.com",
                matchingCode = "XYZ789",
                totalPointsReceived = 42,
                createdAt = Timestamp.now()
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PointsSelectorCardPreview() {
    BrowniePointsAppTheme {
        PointsSelectorCard(
            selectedPoints = 5,
            onPointsChange = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageInputCardPreview() {
    BrowniePointsAppTheme {
        MessageInputCard(
            message = "Great job on the presentation!",
            onMessageChange = {},
            remainingChars = 170,
            hasError = false,
            errorMessage = null
        )
    }
}