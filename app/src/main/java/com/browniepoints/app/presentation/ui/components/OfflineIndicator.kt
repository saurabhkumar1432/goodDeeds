package com.browniepoints.app.presentation.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.browniepoints.app.data.service.SyncStatus

@Composable
fun OfflineIndicator(
    isOffline: Boolean,
    modifier: Modifier = Modifier,
    onRetryClick: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = isOffline,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(8.dp)
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
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "Offline",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "You're offline",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                if (onRetryClick != null) {
                    TextButton(
                        onClick = onRetryClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
fun SyncStatusIndicator(
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier,
    onRetryClick: (() -> Unit)? = null
) {
    val (icon, text, color) = when (syncStatus) {
        SyncStatus.SYNCED -> Triple(
            Icons.Default.Wifi,
            "Synced",
            MaterialTheme.colorScheme.primary
        )
        SyncStatus.SYNCING -> Triple(
            Icons.Default.Sync,
            "Syncing...",
            MaterialTheme.colorScheme.primary
        )
        SyncStatus.ERROR -> Triple(
            Icons.Default.SyncProblem,
            "Sync Error",
            MaterialTheme.colorScheme.error
        )
        SyncStatus.OFFLINE -> Triple(
            Icons.Default.CloudOff,
            "Offline",
            MaterialTheme.colorScheme.error
        )
        SyncStatus.PENDING_SYNC -> Triple(
            Icons.Default.CloudOff,
            "Pending Sync",
            MaterialTheme.colorScheme.error
        )
    }
    
    AnimatedVisibility(
        visible = syncStatus != SyncStatus.SYNCED,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (syncStatus) {
                    SyncStatus.OFFLINE, SyncStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            ),
            shape = RoundedCornerShape(8.dp)
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
                    if (syncStatus == SyncStatus.SYNCING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = color
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = text,
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (syncStatus) {
                            SyncStatus.OFFLINE, SyncStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
                
                if (syncStatus == SyncStatus.ERROR && onRetryClick != null) {
                    TextButton(
                        onClick = onRetryClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Retry", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusBadge(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isOnline) Color.Green else Color.Red
            )
    )
}

@Composable
fun TopBarSyncIndicator(
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier
) {
    when (syncStatus) {
        SyncStatus.SYNCING -> {
            CircularProgressIndicator(
                modifier = modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        SyncStatus.ERROR -> {
            Icon(
                imageVector = Icons.Default.SyncProblem,
                contentDescription = "Sync Failed",
                tint = MaterialTheme.colorScheme.error,
                modifier = modifier.size(20.dp)
            )
        }
        SyncStatus.OFFLINE -> {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "Offline",
                tint = MaterialTheme.colorScheme.error,
                modifier = modifier.size(20.dp)
            )
        }
        SyncStatus.PENDING_SYNC -> {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "Pending Sync",
                tint = MaterialTheme.colorScheme.error,
                modifier = modifier.size(20.dp)
            )
        }
        SyncStatus.ERROR -> {
            Icon(
                imageVector = Icons.Default.SyncProblem,
                contentDescription = "Sync Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = modifier.size(20.dp)
            )
        }
        SyncStatus.SYNCED -> {
            // Don't show anything when synced
        }
    }
}