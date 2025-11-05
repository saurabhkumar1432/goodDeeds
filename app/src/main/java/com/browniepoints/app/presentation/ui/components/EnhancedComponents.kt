package com.browniepoints.app.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.browniepoints.app.presentation.ui.theme.*

/**
 * Empty state component with icon, message, and optional action
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    // Gentle floating animation for icon
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ResponsiveSpacing.large()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated Icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(ResponsiveIcon.extraLarge())
                .offset(y = offsetY.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(ResponsiveSpacing.medium()))
        
        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = ResponsiveText.headline()
            ),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(ResponsiveSpacing.small()))
        
        // Message
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = ResponsiveText.body()
            ),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        )
        
        // Optional action button
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(ResponsiveSpacing.medium()))
            
            Button(
                onClick = onActionClick,
                modifier = Modifier.height(ResponsiveButton.height())
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = ResponsiveText.body()
                    )
                )
            }
        }
    }
}

/**
 * Celebration animation overlay for achievements
 */
@Composable
fun CelebrationAnimation(
    visible: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (visible) {
        val scale by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "scale",
            finishedListener = { onComplete() }
        )
        
        val rotation by animateFloatAsState(
            targetValue = if (visible) 360f else 0f,
            animationSpec = tween(1000, easing = FastOutSlowInEasing),
            label = "rotation"
        )
        
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Celebration",
                modifier = Modifier
                    .size(ResponsiveIcon.extraLarge())
                    .scale(scale)
                    .rotate(rotation),
                tint = BrownieGold
            )
        }
        
        LaunchedEffect(visible) {
            kotlinx.coroutines.delay(1500)
            onComplete()
        }
    }
}

/**
 * Loading shimmer card
 */
@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier
) {
    val shimmer = rememberShimmerAnimation()
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shimmerEffect(shimmer)
        )
    }
}

/**
 * Shimmer effect modifier
 */
fun Modifier.shimmerEffect(offset: Float): Modifier = this.then(
    Modifier.drawBehind {
        // Simple shimmer effect - you can enhance this later
        // For now, just a placeholder to make build work
    }
)

/**
 * Success checkmark animation
 */
@Composable
fun SuccessCheckmark(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkmark"
    )
    
    if (visible) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Success",
            modifier = modifier
                .size(ResponsiveIcon.large())
                .scale(scale),
            tint = SuccessGreen
        )
    }
}

/**
 * Points counter with animation
 */
@Composable
fun AnimatedPointsCounter(
    points: Int,
    modifier: Modifier = Modifier
) {
    var oldPoints by remember { mutableStateOf(points) }
    val animatedPoints by animateIntAsState(
        targetValue = points,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "points"
    )
    
    LaunchedEffect(points) {
        oldPoints = points
    }
    
    Text(
        text = "$animatedPoints",
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = ResponsiveText.display()
        ),
        fontWeight = FontWeight.Bold,
        color = BrownieGold,
        modifier = modifier
    )
}

/**
 * Info card with icon
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoCard(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(
            defaultElevation = ResponsiveCard.elevation()
        )
    ) {
        Row(
            modifier = Modifier
                .padding(ResponsiveCard.padding()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ResponsiveIcon.medium()),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(ResponsiveSpacing.medium()))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = ResponsiveText.title()
                    ),
                    fontWeight = FontWeight.SemiBold
                )
                
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = ResponsiveText.body()
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Navigate",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
