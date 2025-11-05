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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.browniepoints.app.data.model.User
import com.browniepoints.app.presentation.ui.components.AnimatedPointsCounter
import com.browniepoints.app.presentation.ui.components.CelebrationAnimation
import com.browniepoints.app.presentation.ui.components.CompactTimeoutStatus
import com.browniepoints.app.presentation.ui.components.EmptyState
import com.browniepoints.app.presentation.ui.components.InfoCard
import com.browniepoints.app.presentation.ui.components.RequestTimeoutButton
import com.browniepoints.app.presentation.ui.components.TimeoutCountdown
import com.browniepoints.app.presentation.ui.components.TimeoutDisabledOverlay
import com.browniepoints.app.presentation.ui.components.TimeoutRequestDialog
import com.browniepoints.app.presentation.ui.theme.*
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
    var showCelebration by remember { mutableStateOf(false) }
    val bounceScale = rememberBounceAnimation(pointsBalance > 0)
    
    // Show celebration when points increase
    LaunchedEffect(pointsBalance) {
        if (pointsBalance > 0) {
            showCelebration = true
        }
    }
    
    Box(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = ResponsiveCard.elevation()),
            shape = RoundedCornerShape(ResponsiveCard.cornerRadius()),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isNegative -> ErrorRed.copy(alpha = 0.1f)
                    pointsBalance > 0 -> BrownieGoldLight.copy(alpha = 0.1f)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = when {
                            pointsBalance > 0 -> GoldGradient
                            else -> Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color.Transparent)
                            )
                        },
                        alpha = 0.1f
                    )
                    .padding(ResponsiveCard.padding())
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cute couple-focused header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (pointsBalance > 0) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (pointsBalance > 0) BrownieGold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(ResponsiveIcon.small())
                                .scale(bounceScale)
                        )
                        Spacer(modifier = Modifier.width(ResponsiveSpacing.small()))
                        Text(
                            text = when {
                                isNegative -> "Oops! You're in debt 💔"
                                pointsBalance == 0 -> "Your Love Score ❤️"
                                else -> "Your Love Score 💝"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = ResponsiveText.title()
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(ResponsiveSpacing.small()))
                    
                    // Animated points counter
                    AnimatedPointsCounter(
                        points = pointsBalance,
                        modifier = Modifier.scale(bounceScale)
                    )
                    
                    Text(
                        text = when {
                            isNegative -> "brownie points to earn back 😅"
                            pointsBalance == 0 -> "Start earning brownie points! 🎯"
                            pointsBalance == 1 -> "brownie point from your partner 🌟"
                            else -> "brownie points from your partner 🎉"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = ResponsiveText.body()
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = ResponsiveSpacing.medium())
                    )
                    
                    // Motivational message
                    if (isNegative) {
                        Spacer(modifier = Modifier.height(ResponsiveSpacing.small()))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = ErrorRedLight.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(ResponsiveCard.cornerRadius() / 2)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(
                                    horizontal = ResponsiveSpacing.medium(),
                                    vertical = ResponsiveSpacing.small()
                                )
                            ) {
                                Text(
                                    text = "Time to be extra sweet! 💕",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = ResponsiveText.small()
                                    ),
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else if (pointsBalance >= 10) {
                        Spacer(modifier = Modifier.height(ResponsiveSpacing.small()))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = SuccessGreenLight.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(ResponsiveCard.cornerRadius() / 2)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(
                                    horizontal = ResponsiveSpacing.medium(),
                                    vertical = ResponsiveSpacing.small()
                                )
                            ) {
                                Text(
                                    text = "You're doing amazing! 🌈✨",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = ResponsiveText.small()
                                    ),
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    // Compact timeout status
                    if (isTimeoutActive) {
                        Spacer(modifier = Modifier.height(ResponsiveSpacing.medium()))
                        CompactTimeoutStatus(
                            isActive = isTimeoutActive,
                            remainingTimeMs = remainingTimeMs
                        )
                    }
                }
            }
        }
        
        // Celebration animation overlay
        if (showCelebration && pointsBalance > 0) {
            CelebrationAnimation(
                visible = true,
                onComplete = { showCelebration = false }
            )
        }
    }
}

@Composable
private fun ConnectedPartnerCard(
    connectedPartner: User?,
    onNavigateToConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (connectedPartner != null) {
        // Connected - show partner info with love
        val heartBeat = rememberPulseAnimation()
        
        Card(
            modifier = modifier,
            elevation = CardDefaults.cardElevation(defaultElevation = ResponsiveCard.elevation()),
            shape = RoundedCornerShape(ResponsiveCard.cornerRadius()),
            colors = CardDefaults.cardColors(
                containerColor = BrownieGoldLight.copy(alpha = 0.05f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = GoldGradient,
                        alpha = 0.05f
                    )
                    .padding(ResponsiveCard.padding())
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ResponsiveSpacing.medium())
                    ) {
                        // Partner avatar with heart beat
                        Box(
                            modifier = Modifier
                                .size(ResponsiveIcon.large())
                                .clip(CircleShape)
                                .background(BrownieGoldLight.copy(alpha = heartBeat * 0.3f))
                                .padding(ResponsiveSpacing.extraSmall()),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(ResponsiveIcon.medium())
                                    .scale(1f + (1f - heartBeat) * 0.1f),
                                tint = BrownieGold
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connected with 💕",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = ResponsiveText.small()
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(ResponsiveSpacing.extraSmall()))
                            Text(
                                text = connectedPartner.displayName,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = ResponsiveText.headline()
                                ),
                                fontWeight = FontWeight.Bold,
                                color = BrownieGold
                            )
                        }
                        
                        // View details button
                        IconButton(onClick = onNavigateToConnection) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "View Partner",
                                tint = BrownieGold,
                                modifier = Modifier.size(ResponsiveIcon.medium())
                            )
                        }
                    }
                    
                    // Relationship status message
                    Spacer(modifier = Modifier.height(ResponsiveSpacing.medium()))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = WarmCream.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(ResponsiveCard.cornerRadius() / 2)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(ResponsiveSpacing.medium()),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "💑 Keep the love alive with brownie points! 💝",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = ResponsiveText.small()
                                ),
                                color = ChocolateBrown,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Not connected - show empty state with invitation
        EmptyState(
            icon = Icons.Default.FavoriteBorder,
            title = "No Partner Yet 💔",
            message = "Connect with your special someone to start earning and giving brownie points together!",
            actionText = "Connect Now",
            onActionClick = onNavigateToConnection,
            modifier = modifier
        )
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