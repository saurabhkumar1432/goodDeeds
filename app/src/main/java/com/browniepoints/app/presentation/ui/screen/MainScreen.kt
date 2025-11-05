package com.browniepoints.app.presentation.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.browniepoints.app.data.model.User
import com.browniepoints.app.presentation.ui.components.CompactTimeoutStatus
import com.browniepoints.app.presentation.ui.components.RequestTimeoutButton
import com.browniepoints.app.presentation.ui.components.TimeoutCountdown
import com.browniepoints.app.presentation.ui.components.TimeoutDisabledOverlay
import com.browniepoints.app.presentation.ui.components.TimeoutRequestDialog
import com.browniepoints.app.presentation.ui.theme.BrowniePointsAppTheme
import com.browniepoints.app.presentation.viewmodel.AuthViewModel
import com.browniepoints.app.presentation.viewmodel.ConnectionViewModel
import com.browniepoints.app.presentation.viewmodel.TimeoutViewModel
import com.browniepoints.app.presentation.viewmodel.TransactionViewModel
import com.google.firebase.Timestamp

/**
 * Main dashboard screen showing point balance and connected partner information
 * Provides navigation to other screens and displays current user status
 */
@Composable
fun MainScreen(
    onSignOut: () -> Unit,
    onNavigateToConnection: () -> Unit,
    onNavigateToGivePoints: () -> Unit,
    onNavigateToDeductPoints: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToTimeoutHistory: () -> Unit,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel(),
    transactionViewModel: TransactionViewModel = hiltViewModel(),
    timeoutViewModel: TimeoutViewModel = hiltViewModel(),
    connectionViewModel: ConnectionViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsState()
    val givePointsState by transactionViewModel.givePointsState.collectAsState()
    val connectionState by connectionViewModel.uiState.collectAsState()
    
    // Timeout states - now properly integrated
    val isTimeoutActive by timeoutViewModel.isTimeoutActive.collectAsState()
    val remainingTimeMs by timeoutViewModel.liveRemainingTimeMs.collectAsState()
    val transactionsDisabled by timeoutViewModel.transactionsDisabled.collectAsState()
    val canRequestTimeout by timeoutViewModel.canRequestTimeout.collectAsState()
    val showTimeoutRequestDialog by timeoutViewModel.showTimeoutRequestDialog.collectAsState()
    
    // Load user data and observe real-time updates when screen is displayed
    LaunchedEffect(authState.currentUser?.uid) {
        authState.currentUser?.uid?.let { userId ->
            transactionViewModel.loadGivePointsData(userId)
            connectionViewModel.loadConnection(userId)
            // Start observing real-time point balance updates
            transactionViewModel.observeUserPointBalance(userId)
        } ?: run {
            // User signed out, clear data
            transactionViewModel.clearData()
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
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with sign out button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🍪 Brownie Points",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = onSignOut) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Sign Out",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (givePointsState.isLoading) {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Point balance card with timeout status
            PointBalanceCard(
                currentUser = givePointsState.currentUser,
                isTimeoutActive = isTimeoutActive,
                remainingTimeMs = remainingTimeMs,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Connected partner card
            ConnectedPartnerCard(
                connectedPartner = givePointsState.connectedPartner,
                onNavigateToConnection = onNavigateToConnection,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Timeout history summary (only show if connected and user is authenticated)
            authState.currentUser?.uid?.let { userId ->
                if (givePointsState.connectedPartner != null) {
                    TimeoutHistorySummary(
                        userId = userId,
                        onNavigateToTimeoutHistory = onNavigateToTimeoutHistory,
                        timeoutViewModel = timeoutViewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Timeout countdown (when active)
            if (isTimeoutActive && remainingTimeMs > 0) {
                TimeoutCountdown(
                    remainingTimeMs = remainingTimeMs,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Action buttons with timeout integration
            Box(modifier = Modifier.fillMaxWidth()) {
                ActionButtons(
                    canGivePoints = (givePointsState.connectedPartner != null) && !transactionsDisabled,
                    transactionsDisabled = transactionsDisabled,
                    isConnected = givePointsState.connectedPartner != null, // Use this to show/hide timeout button
                    onNavigateToGivePoints = onNavigateToGivePoints,
                    onNavigateToDeductPoints = onNavigateToDeductPoints,
                    onNavigateToHistory = onNavigateToHistory,
                    onRequestTimeout = { timeoutViewModel.showTimeoutRequestDialog() },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Show disabled overlay when timeout is active
                if (transactionsDisabled && isTimeoutActive) {
                    TimeoutDisabledOverlay(
                        isVisible = true,
                        remainingTimeMs = remainingTimeMs,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            // Error handling
            givePointsState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
    
    // Timeout request dialog
    TimeoutRequestDialog(
        isVisible = showTimeoutRequestDialog,
        canRequestToday = canRequestTimeout,
        onConfirm = {
            authState.currentUser?.uid?.let { userId ->
                timeoutViewModel.requestTimeout(userId)
            }
        },
        onDismiss = { timeoutViewModel.hideTimeoutRequestDialog() }
    )
}

@Composable
private fun PointBalanceCard(
    currentUser: User?,
    isTimeoutActive: Boolean = false,
    remainingTimeMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    val pointsBalance = currentUser?.totalPointsReceived ?: 0
    val isNegative = pointsBalance < 0
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNegative) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Your Balance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "$pointsBalance",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = when {
                    isNegative -> MaterialTheme.colorScheme.error
                    pointsBalance == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            
            Text(
                text = when {
                    isNegative -> "brownie points in debt"
                    pointsBalance == 0 -> "no brownie points yet"
                    pointsBalance == 1 -> "brownie point earned"
                    else -> "brownie points earned"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            // Show balance status indicator
            if (isNegative) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Negative Balance",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Time to earn some points back!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // Compact timeout status
            if (isTimeoutActive) {
                Spacer(modifier = Modifier.height(8.dp))
                CompactTimeoutStatus(
                    isActive = isTimeoutActive,
                    remainingTimeMs = remainingTimeMs
                )
            }
        }
    }
}

@Composable
private fun ConnectedPartnerCard(
    connectedPartner: User?,
    onNavigateToConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "Connection",
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Connected Partner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (connectedPartner != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Partner avatar placeholder
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Partner Avatar",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = connectedPartner.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            
                            Text(
                                text = "Connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "Points: ${connectedPartner.totalPointsReceived}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No partner connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Connect with someone to start exchanging brownie points!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedButton(
                        onClick = onNavigateToConnection,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect with Someone")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeoutHistorySummary(
    userId: String,
    onNavigateToTimeoutHistory: () -> Unit,
    timeoutViewModel: TimeoutViewModel,
    modifier: Modifier = Modifier
) {
    val timeoutHistoryState by timeoutViewModel.getTimeoutHistory(userId).collectAsState(
        initial = com.browniepoints.app.presentation.ui.common.UiState.Loading
    )
    
    when (val state = timeoutHistoryState) {
        is com.browniepoints.app.presentation.ui.common.UiState.Success -> {
            val timeouts = state.data
            val totalTimeouts = timeouts.size
            val thisMonthTimeouts = timeouts.count { isCurrentMonth(it.startTime) }
            
            if (totalTimeouts > 0) {
                Card(
                    modifier = modifier,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    onClick = onNavigateToTimeoutHistory
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = "Timeout History",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                
                                Text(
                                    text = "Timeout Usage",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            Text(
                                text = "View All",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "$totalTimeouts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Total",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Column {
                                Text(
                                    text = "$thisMonthTimeouts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "This Month",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        else -> {
            // Don't show anything if loading or error - keep UI clean
        }
    }
}

/**
 * Checks if a timestamp is from the current month
 */
private fun isCurrentMonth(timestamp: Timestamp): Boolean {
    val date = timestamp.toDate()
    val now = java.util.Date()
    val dateFormat = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
    return dateFormat.format(date) == dateFormat.format(now)
}

@Composable
private fun ActionButtons(
    canGivePoints: Boolean,
    transactionsDisabled: Boolean = false,
    isConnected: Boolean = false,
    onNavigateToGivePoints: () -> Unit,
    onNavigateToDeductPoints: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onRequestTimeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Points action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Give Points button
            ElevatedButton(
                onClick = onNavigateToGivePoints,
                enabled = canGivePoints,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Give",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            
            // Deduct Points button
            OutlinedButton(
                onClick = onNavigateToDeductPoints,
                enabled = canGivePoints,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Deduct",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        
        // Request Timeout button (show if connected)
        if (isConnected) {
            RequestTimeoutButton(
                onClick = onRequestTimeout,
                modifier = Modifier.fillMaxWidth(),
                enabled = !transactionsDisabled, // Disabled when timeout is active
                isActive = transactionsDisabled,
                isLoading = false // Could be connected to timeout request state if needed
            )
        }
        
        // View History button
        Button(
            onClick = onNavigateToHistory,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "View History",
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    BrowniePointsAppTheme {
        MainScreen(
            onSignOut = {},
            onNavigateToConnection = {},
            onNavigateToGivePoints = {},
            onNavigateToDeductPoints = {},
            onNavigateToHistory = {},
            onNavigateToTimeoutHistory = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PointBalanceCardPreview() {
    BrowniePointsAppTheme {
        PointBalanceCard(
            currentUser = User(
                uid = "test",
                displayName = "Test User",
                email = "test@example.com",
                totalPointsReceived = 42,
                matchingCode = "ABC123",
                createdAt = Timestamp.now()
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectedPartnerCardPreview() {
    BrowniePointsAppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // With connected partner
            ConnectedPartnerCard(
                connectedPartner = User(
                    uid = "partner",
                    displayName = "Partner Name",
                    email = "partner@example.com",
                    matchingCode = "XYZ789",
                    createdAt = Timestamp.now()
                ),
                onNavigateToConnection = {}
            )
            
            // Without connected partner
            ConnectedPartnerCard(
                connectedPartner = null,
                onNavigateToConnection = {}
            )
        }
    }
}