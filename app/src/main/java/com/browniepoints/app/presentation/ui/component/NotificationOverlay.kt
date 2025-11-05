package com.browniepoints.app.presentation.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.browniepoints.app.data.model.Notification
import com.browniepoints.app.data.model.NotificationType
import com.browniepoints.app.presentation.ui.theme.*

/**
 * Composable for displaying a temporary romantic notification overlay at the top of the screen
 */
@Composable
fun NotificationOverlay(
    notification: Notification?,
    onNotificationClick: (Notification) -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Animations
    val bounceScale = rememberBounceAnimation(notification != null)
    val pulseAlpha = rememberPulseAnimation()
    
    AnimatedVisibility(
        visible = notification != null,
        enter = slideInFromBottomAnimation(),
        exit = slideOutToBottomAnimation(),
        modifier = modifier.zIndex(1000f)
    ) {
        notification?.let { notif ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ResponsiveSpacing.medium())
                    .scale(bounceScale)
                    .clickable { 
                        onNotificationClick(notif)
                        onDismiss()
                    },
                colors = CardDefaults.cardColors(
                    containerColor = when (notif.type) {
                        NotificationType.POINTS_RECEIVED -> BrownieGoldLight
                        NotificationType.CONNECTION_ACCEPTED -> SuccessGreenLight
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = ResponsiveCard.elevation() * 2
                ),
                shape = RoundedCornerShape(ResponsiveCard.cornerRadius())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = when (notif.type) {
                                NotificationType.POINTS_RECEIVED -> Brush.horizontalGradient(
                                    listOf(
                                        BrownieGoldLight.copy(alpha = 0.3f),
                                        BrownieGold.copy(alpha = 0.3f),
                                        BrownieGoldDark.copy(alpha = 0.3f)
                                    )
                                )
                                NotificationType.CONNECTION_ACCEPTED -> Brush.horizontalGradient(
                                    listOf(
                                        SuccessGreenLight.copy(alpha = 0.3f),
                                        SuccessGreen.copy(alpha = 0.3f)
                                    )
                                )
                                else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ResponsiveCard.padding()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile image or romantic icon with pulse
                        if (!notif.senderPhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = notif.senderPhotoUrl,
                                contentDescription = "Your partner 💕",
                                modifier = Modifier
                                    .size(ResponsiveIcon.large())
                                    .clip(CircleShape)
                                    .scale(pulseAlpha),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(ResponsiveIcon.large())
                                    .clip(CircleShape)
                                    .background(
                                        brush = when (notif.type) {
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
                                    .scale(pulseAlpha),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (notif.type) {
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

                        // Notification content with romantic styling
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                            text = notif.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = ResponsiveText.body()
                            ),
                            fontWeight = FontWeight.Bold,
                            color = when (notif.type) {
                                NotificationType.POINTS_RECEIVED -> ChocolateBrown
                                NotificationType.CONNECTION_ACCEPTED -> Color.White
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = notif.getDisplayMessage(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Points indicator for points notifications
                        if ((notif.type == NotificationType.POINTS_RECEIVED || notif.type == NotificationType.POINTS_DEDUCTED) && notif.points != 0) {
                            val isPositive = notif.points > 0
                            val absolutePoints = kotlin.math.abs(notif.points)
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPositive) Icons.Default.Star else Icons.Default.Remove,
                                    contentDescription = null,
                                    tint = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${if (isPositive) "+" else "-"}$absolutePoints point${if (absolutePoints != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Dismiss button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(ResponsiveIcon.medium())
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss notification",
                            tint = when (notif.type) {
                                NotificationType.POINTS_RECEIVED -> ChocolateBrown
                                NotificationType.CONNECTION_ACCEPTED -> Color.White
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.size(ResponsiveIcon.small())
                        )
                    }
                    }
                }
            }
        }
    }
}