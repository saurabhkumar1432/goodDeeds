package com.browniepoints.app.presentation.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.browniepoints.app.data.model.Notification
import com.browniepoints.app.data.model.NotificationType
import com.browniepoints.app.presentation.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Composable for displaying a notification card with romantic couple-focused design
 */
@Composable
fun NotificationCard(
    notification: Notification,
    onNotificationClick: (Notification) -> Unit = {},
    onDismiss: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Animations
    val bounceScale = rememberBounceAnimation(!notification.isRead)
    val pulseAlpha = rememberPulseAnimation()
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(bounceScale)
            .clickable { 
                onNotificationClick(notification)
            },
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) {
                MaterialTheme.colorScheme.surface
            } else {
                when (notification.type) {
                    NotificationType.POINTS_RECEIVED -> BrownieGoldLight.copy(alpha = 0.15f)
                    NotificationType.CONNECTION_ACCEPTED -> SuccessGreenLight.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                }
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = ResponsiveCard.elevation()
        ),
        shape = RoundedCornerShape(ResponsiveCard.cornerRadius())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = if (!notification.isRead) {
                        when (notification.type) {
                            NotificationType.POINTS_RECEIVED -> Brush.horizontalGradient(
                                listOf(
                                    BrownieGoldLight.copy(alpha = 0.05f),
                                    BrownieGold.copy(alpha = 0.05f),
                                    BrownieGoldDark.copy(alpha = 0.05f)
                                )
                            )
                            NotificationType.CONNECTION_ACCEPTED -> Brush.horizontalGradient(
                                listOf(
                                    SuccessGreenLight.copy(alpha = 0.05f),
                                    SuccessGreen.copy(alpha = 0.05f)
                                )
                            )
                            else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ResponsiveCard.padding()),
                verticalAlignment = Alignment.Top
            ) {
                // Profile image or romantic icon
                if (!notification.senderPhotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = notification.senderPhotoUrl,
                        contentDescription = "Your partner",
                        modifier = Modifier
                            .size(ResponsiveIcon.large())
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(ResponsiveIcon.large())
                            .clip(CircleShape)
                            .background(
                                brush = when (notification.type) {
                                    NotificationType.POINTS_RECEIVED -> GoldGradient
                                    NotificationType.CONNECTION_ACCEPTED -> SuccessGradient
                                    else -> Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            )
                            .scale(if (!notification.isRead) pulseAlpha else 1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (notification.type) {
                                NotificationType.POINTS_RECEIVED -> Icons.Default.Favorite
                                NotificationType.POINTS_DEDUCTED -> Icons.Default.Remove
                                NotificationType.CONNECTION_REQUEST -> Icons.Default.Person
                                NotificationType.CONNECTION_ACCEPTED -> Icons.Default.Favorite
                                NotificationType.TIMEOUT_REQUESTED,
                                NotificationType.TIMEOUT_PARTNER_REQUEST,
                                NotificationType.TIMEOUT_EXPIRED -> Icons.Default.Schedule
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(ResponsiveIcon.small())
                        )
                    }
                }

                Spacer(modifier = Modifier.width(ResponsiveSpacing.medium()))

                // Notification content
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                // Title and timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = ResponsiveText.body()
                        ),
                        fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                        color = when (notification.type) {
                            NotificationType.POINTS_RECEIVED -> BrownieGold
                            NotificationType.CONNECTION_ACCEPTED -> SuccessGreen
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = formatTimestamp(notification.createdAt.toDate()),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = ResponsiveText.small()
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.padding(2.dp))

                // Message
                Text(
                    text = notification.getDisplayMessage(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Points indicator for points notifications
                if ((notification.type == NotificationType.POINTS_RECEIVED || notification.type == NotificationType.POINTS_DEDUCTED) && notification.points != 0) {
                    Spacer(modifier = Modifier.padding(4.dp))
                    
                    val isPositive = notification.points > 0
                    val absolutePoints = kotlin.math.abs(notification.points)
                    
                    Box(
                        modifier = Modifier
                            .background(
                                if (isPositive) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                } else {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                },
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${if (isPositive) "+" else "-"}$absolutePoints point${if (absolutePoints != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPositive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Unread indicator
                if (!notification.isRead) {
                    Spacer(modifier = Modifier.padding(2.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

                // Dismiss button
                IconButton(
                    onClick = { onDismiss(notification.id) },
                    modifier = Modifier.size(ResponsiveIcon.small())
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss notification",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ResponsiveIcon.small() * 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Formats timestamp for display
 */
private fun formatTimestamp(date: Date): String {
    val now = Date()
    val diffInMillis = now.time - date.time
    val diffInMinutes = diffInMillis / (1000 * 60)
    val diffInHours = diffInMinutes / 60
    val diffInDays = diffInHours / 24

    return when {
        diffInMinutes < 1 -> "Just now"
        diffInMinutes < 60 -> "${diffInMinutes}m"
        diffInHours < 24 -> "${diffInHours}h"
        diffInDays < 7 -> "${diffInDays}d"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
    }
}