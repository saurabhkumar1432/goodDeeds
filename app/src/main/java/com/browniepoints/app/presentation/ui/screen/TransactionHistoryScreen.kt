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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.browniepoints.app.data.model.Transaction
import com.browniepoints.app.data.model.TransactionType
import com.browniepoints.app.presentation.ui.theme.BrowniePointsAppTheme
import com.browniepoints.app.presentation.viewmodel.AuthViewModel
import com.browniepoints.app.presentation.viewmodel.TransactionViewModel
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transaction filter options for filtering transaction history
 */
enum class TransactionFilter {
    ALL,
    GIVEN,
    RECEIVED,
    POSITIVE,
    NEGATIVE
}

/**
 * Transaction History screen displaying chronological list of transactions
 * Shows transaction details with messages, timestamps, and real-time updates
 * Enhanced with filtering and visual indicators for transaction types
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel(),
    transactionViewModel: TransactionViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsState()
    val transactionState by transactionViewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf(TransactionFilter.ALL) }
    var showFilterMenu by remember { mutableStateOf(false) }
    
    // Load transaction history with real-time updates when screen is displayed
    LaunchedEffect(authState.currentUser?.uid) {
        authState.currentUser?.uid?.let { userId ->
            transactionViewModel.loadTransactionHistory(userId)
        }
    }
    
    // Filter transactions based on selected filter
    val filteredTransactions = remember(transactionState.transactions, selectedFilter, authState.currentUser?.uid) {
        val currentUserId = authState.currentUser?.uid ?: ""
        when (selectedFilter) {
            TransactionFilter.ALL -> transactionState.transactions
            TransactionFilter.GIVEN -> transactionState.transactions.filter { it.isSentBy(currentUserId) }
            TransactionFilter.RECEIVED -> transactionState.transactions.filter { it.isReceivedBy(currentUserId) }
            TransactionFilter.POSITIVE -> transactionState.transactions.filter { it.type == TransactionType.GIVE }
            TransactionFilter.NEGATIVE -> transactionState.transactions.filter { it.type == TransactionType.DEDUCT }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Transaction History",
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
                },
                actions = {
                    // Refresh button
                    IconButton(
                        onClick = {
                            authState.currentUser?.uid?.let { userId ->
                                transactionViewModel.loadTransactionHistory(userId)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh transactions"
                        )
                    }
                    
                    // Filter button
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter transactions"
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            TransactionFilter.values().forEach { filter ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = when (filter) {
                                                TransactionFilter.ALL -> "All Transactions"
                                                TransactionFilter.GIVEN -> "Given Points"
                                                TransactionFilter.RECEIVED -> "Received Points"
                                                TransactionFilter.POSITIVE -> "Positive (Give)"
                                                TransactionFilter.NEGATIVE -> "Negative (Deduct)"
                                            }
                                        )
                                    },
                                    onClick = {
                                        selectedFilter = filter
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
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
            // Filter chips
            if (transactionState.hasTransactions) {
                FilterChipsRow(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                    transactionCount = filteredTransactions.size,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            when {
                transactionState.isLoading -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                transactionState.hasError -> {
                    // Error state
                    ErrorMessage(
                        error = transactionState.error ?: "Unknown error occurred",
                        onRetry = {
                            authState.currentUser?.uid?.let { userId ->
                                transactionViewModel.loadTransactionHistory(userId)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                !transactionState.hasTransactions -> {
                    // Empty state
                    EmptyTransactionHistory()
                }
                
                filteredTransactions.isEmpty() -> {
                    // No transactions match filter
                    EmptyFilteredTransactions(selectedFilter)
                }
                
                else -> {
                    // Transaction list
                    TransactionList(
                        transactions = filteredTransactions,
                        currentUserId = authState.currentUser?.uid ?: "",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    selectedFilter: TransactionFilter,
    onFilterSelected: (TransactionFilter) -> Unit,
    transactionCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == TransactionFilter.ALL,
            onClick = { onFilterSelected(TransactionFilter.ALL) },
            label = { Text("All") }
        )
        
        FilterChip(
            selected = selectedFilter == TransactionFilter.POSITIVE,
            onClick = { onFilterSelected(TransactionFilter.POSITIVE) },
            label = { Text("Give") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        )
        
        FilterChip(
            selected = selectedFilter == TransactionFilter.NEGATIVE,
            onClick = { onFilterSelected(TransactionFilter.NEGATIVE) },
            label = { Text("Deduct") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "$transactionCount items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}

@Composable
private fun TransactionList(
    transactions: List<Transaction>,
    currentUserId: String,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = transactions,
            key = { transaction -> transaction.id }
        ) { transaction ->
            TransactionItem(
                transaction = transaction,
                currentUserId = currentUserId
            )
        }
    }
}

@Composable
private fun TransactionItem(
    transaction: Transaction,
    currentUserId: String,
    modifier: Modifier = Modifier
) {
    val isSent = transaction.isSentBy(currentUserId)
    val isReceived = transaction.isReceivedBy(currentUserId)
    val isPositive = transaction.type == TransactionType.GIVE
    val isNegative = transaction.type == TransactionType.DEDUCT
    
    // Determine colors and icons based on transaction type and direction
    val (containerColor, contentColor, icon, actionText, pointsPrefix, badgeColor) = when {
        isPositive && isSent -> {
            Tuple6(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                Icons.Default.Add,
                "Points Given",
                "-",
                MaterialTheme.colorScheme.primary
            )
        }
        isPositive && isReceived -> {
            Tuple6(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
                Icons.Default.Add,
                "Points Received",
                "+",
                MaterialTheme.colorScheme.primary
            )
        }
        isNegative && isSent -> {
            Tuple6(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                Icons.Default.Remove,
                "Points Deducted",
                "-",
                MaterialTheme.colorScheme.error
            )
        }
        isNegative && isReceived -> {
            Tuple6(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                Icons.Default.Remove,
                "Points Lost",
                "-",
                MaterialTheme.colorScheme.error
            )
        }
        else -> {
            Tuple6(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                Icons.Default.ArrowUpward,
                "Transaction",
                "",
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNegative) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transaction type indicator with enhanced styling
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = actionText,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Transaction details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = actionText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        
                        // Transaction type badge with enhanced styling
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = badgeColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isPositive) "GIVE" else "DEDUCT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Direction indicator
                            Text(
                                text = if (isSent) "→" else "←",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Points display with enhanced styling
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "$pointsPrefix${transaction.getAbsolutePoints()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) {
                                if (isSent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        
                        Text(
                            text = "points",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Message if present
                if (transaction.hasMessage()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = "Message",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Text(
                            text = transaction.message ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                }
                
                // Timestamp and direction indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(transaction.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Direction indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isSent) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = if (isSent) "Sent" else "Received",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        
                        Text(
                            text = if (isSent) "Sent" else "Received",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// Helper data class for multiple return values
private data class Tuple6<A, B, C, D, E, F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F
)

@Composable
private fun EmptyTransactionHistory(
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
                text = "📝",
                style = MaterialTheme.typography.displayMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "No Transactions Yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Start giving brownie points to see your transaction history here!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun EmptyFilteredTransactions(
    filter: TransactionFilter,
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
                text = when (filter) {
                    TransactionFilter.POSITIVE -> "➕"
                    TransactionFilter.NEGATIVE -> "➖"
                    TransactionFilter.GIVEN -> "📤"
                    TransactionFilter.RECEIVED -> "📥"
                    else -> "🔍"
                },
                style = MaterialTheme.typography.displayMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = when (filter) {
                    TransactionFilter.POSITIVE -> "No Positive Transactions"
                    TransactionFilter.NEGATIVE -> "No Negative Transactions"
                    TransactionFilter.GIVEN -> "No Points Given"
                    TransactionFilter.RECEIVED -> "No Points Received"
                    else -> "No Matching Transactions"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = when (filter) {
                    TransactionFilter.POSITIVE -> "You haven't given any brownie points yet."
                    TransactionFilter.NEGATIVE -> "No points have been deducted."
                    TransactionFilter.GIVEN -> "You haven't sent any points to your partner."
                    TransactionFilter.RECEIVED -> "You haven't received any points from your partner."
                    else -> "Try selecting a different filter to see your transactions."
                },
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
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.displaySmall
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
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
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = onRetry,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Try Again")
                }
            }
        }
    }
}

/**
 * Formats a Firestore Timestamp to a human-readable string
 */
private fun formatTimestamp(timestamp: Timestamp): String {
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
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionHistoryScreenPreview() {
    BrowniePointsAppTheme {
        TransactionHistoryScreen(
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionItemPreview() {
    BrowniePointsAppTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            // Positive transaction sent (giving points)
            TransactionItem(
                transaction = Transaction(
                    id = "1",
                    senderId = "current_user",
                    receiverId = "partner",
                    points = 5,
                    message = "Thanks for helping with the dishes!",
                    timestamp = Timestamp.now(),
                    connectionId = "connection1",
                    type = TransactionType.GIVE
                ),
                currentUserId = "current_user"
            )
            
            // Positive transaction received (receiving points)
            TransactionItem(
                transaction = Transaction(
                    id = "2",
                    senderId = "partner",
                    receiverId = "current_user",
                    points = 3,
                    message = "Great job on the presentation!",
                    timestamp = Timestamp(Date(System.currentTimeMillis() - 3600000)), // 1 hour ago
                    connectionId = "connection1",
                    type = TransactionType.GIVE
                ),
                currentUserId = "current_user"
            )
            
            // Negative transaction sent (deducting points)
            TransactionItem(
                transaction = Transaction(
                    id = "3",
                    senderId = "current_user",
                    receiverId = "partner",
                    points = -2,
                    message = "Left dishes in the sink again",
                    timestamp = Timestamp(Date(System.currentTimeMillis() - 7200000)), // 2 hours ago
                    connectionId = "connection1",
                    type = TransactionType.DEDUCT
                ),
                currentUserId = "current_user"
            )
            
            // Negative transaction received (losing points)
            TransactionItem(
                transaction = Transaction(
                    id = "4",
                    senderId = "partner",
                    receiverId = "current_user",
                    points = -1,
                    message = "Forgot to take out the trash",
                    timestamp = Timestamp(Date(System.currentTimeMillis() - 10800000)), // 3 hours ago
                    connectionId = "connection1",
                    type = TransactionType.DEDUCT
                ),
                currentUserId = "current_user"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyTransactionHistoryPreview() {
    BrowniePointsAppTheme {
        EmptyTransactionHistory()
    }
}