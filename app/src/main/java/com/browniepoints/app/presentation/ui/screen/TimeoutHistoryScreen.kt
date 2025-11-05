package com.browniepoints.app.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.browniepoints.app.data.model.Timeout
import com.browniepoints.app.presentation.ui.common.UiState
import com.browniepoints.app.presentation.ui.theme.BrowniePointsAppTheme
import com.browniepoints.app.presentation.viewmodel.AuthViewModel
import com.browniepoints.app.presentation.viewmodel.TimeoutViewModel
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Timeout History screen displaying chronological list of timeout requests
 * Shows timeout usage patterns and statistics
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeoutHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel(),
    timeoutViewModel: TimeoutViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsState()
    val currentUserId = authState.currentUser?.uid ?: ""
    
    // Get timeout history
    val timeoutHistoryState by timeoutViewModel.getTimeoutHistory(currentUserId).collectAsState(
        initial = UiState.Loading
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Timeout History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
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
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = timeoutHistoryState) {
                is UiState.Loading -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                is UiState.Error -> {
                    // Error state
                    ErrorMessage(
                        error = state.error.message ?: "Unknown error occurred"
                    )
                }
                
                is UiState.Success -> {
                    val timeouts = state.data
                    
                    if (timeouts.isEmpty()) {
                        // Empty state
                        EmptyTimeoutHistory()
                    } else {
                        // Statistics and history list
                        Column {
                            TimeoutStatistics(
                                timeouts = timeouts,
                                modifier = Modifier.padding(16.dp)
                            )
                            
                            TimeoutHistoryList(
                                timeouts = timeouts,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                
                else -> {
                    // Idle state - shouldn't happen but handle gracefully
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Loading timeout history...")
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeoutStatistics(
    timeouts: List<Timeout>,
    modifier: Modifier = Modifier
) {
    val totalTimeouts = timeouts.size
    val completedTimeouts = timeouts.count { !it.isActive }
    val currentMonthTimeouts = timeouts.count { isCurrentMonth(it.startTime) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Timeout Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    value = totalTimeouts.toString(),
                    label = "Total Timeouts",
                    icon = Icons.Default.History
                )
                
                StatisticItem(
                    value = completedTimeouts.toString(),
                    label = "Completed",
                    icon = Icons.Default.CheckCircle
                )
                
                StatisticItem(
                    value = currentMonthTimeouts.toString(),
                    label = "This Month",
                    icon = Icons.Default.AccessTime
                )
            }
        }
    }
}

@Composable
private fun StatisticItem(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TimeoutHistoryList(
    timeouts: List<Timeout>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = timeouts,
            key = { timeout -> timeout.id }
        ) { timeout ->
            TimeoutHistoryItem(timeout = timeout)
        }
    }
}

@Composable
private fun TimeoutHistoryItem(
    timeout: Timeout,
    modifier: Modifier = Modifier
) {
    val isCompleted = !timeout.isActive
    val isExpired = timeout.hasExpired()
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Block,
                    contentDescription = if (isCompleted) "Completed" else "Active",
                    tint = if (isCompleted) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Timeout details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCompleted) "Timeout Completed" else "Timeout Active",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = "${timeout.duration / (60 * 1000)} min",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Status and remaining time
                Text(
                    text = when {
                        isCompleted -> "Completed successfully"
                        isExpired -> "Expired"
                        else -> "Remaining: ${formatRemainingTime(timeout.getRemainingTimeMs())}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isCompleted -> MaterialTheme.colorScheme.primary
                        isExpired -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Date and time
                Text(
                    text = formatTimeoutTimestamp(timeout.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyTimeoutHistory(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "⏰",
                style = MaterialTheme.typography.displayMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "No Timeouts Yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "When you request timeout breaks during conflicts, they'll appear here with usage statistics.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun ErrorMessage(
    error: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Error Loading History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Formats a Firestore Timestamp to a human-readable string for timeout history
 */
private fun formatTimeoutTimestamp(timestamp: Timestamp): String {
    val date = timestamp.toDate()
    val now = Date()
    val diffInMillis = now.time - date.time
    val diffInHours = diffInMillis / (1000 * 60 * 60)
    val diffInDays = diffInHours / 24
    
    return when {
        diffInHours < 1 -> {
            val diffInMinutes = diffInMillis / (1000 * 60)
            if (diffInMinutes < 1) "Just now"
            else "${diffInMinutes}m ago"
        }
        diffInHours < 24 -> "${diffInHours}h ago"
        diffInDays < 7 -> "${diffInDays}d ago"
        else -> SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(date)
    }
}

/**
 * Formats remaining time in a readable format
 */
private fun formatRemainingTime(remainingMs: Long): String {
    val minutes = (remainingMs / (60 * 1000)).toInt()
    val seconds = ((remainingMs % (60 * 1000)) / 1000).toInt()
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Checks if a timestamp is from the current month
 */
private fun isCurrentMonth(timestamp: Timestamp): Boolean {
    val date = timestamp.toDate()
    val now = Date()
    val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    return dateFormat.format(date) == dateFormat.format(now)
}

@Preview(showBackground = true)
@Composable
private fun TimeoutHistoryScreenPreview() {
    BrowniePointsAppTheme {
        TimeoutHistoryScreen(
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TimeoutHistoryItemPreview() {
    BrowniePointsAppTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            // Completed timeout
            TimeoutHistoryItem(
                timeout = Timeout(
                    id = "1",
                    userId = "user1",
                    connectionId = "connection1",
                    startTime = Timestamp(Date(System.currentTimeMillis() - 7200000)), // 2 hours ago
                    duration = 30 * 60 * 1000L, // 30 minutes
                    isActive = false,
                    createdDate = "2024-01-15"
                )
            )
            
            // Active timeout
            TimeoutHistoryItem(
                timeout = Timeout(
                    id = "2",
                    userId = "user1",
                    connectionId = "connection1",
                    startTime = Timestamp(Date(System.currentTimeMillis() - 600000)), // 10 minutes ago
                    duration = 30 * 60 * 1000L, // 30 minutes
                    isActive = true,
                    createdDate = "2024-01-15"
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyTimeoutHistoryPreview() {
    BrowniePointsAppTheme {
        EmptyTimeoutHistory()
    }
}