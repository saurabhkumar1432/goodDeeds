package com.browniepoints.app.presentation.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically

/**
 * Reusable animation utilities for engaging UI
 */

/**
 * Celebration animation for point transactions
 */
@Composable
fun rememberCelebrationAnimation(
    trigger: Boolean
): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "celebration")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    return if (trigger) scale else 1f
}

/**
 * Bounce animation for buttons and icons
 */
@Composable
fun rememberBounceAnimation(trigger: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (trigger) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounce"
    )
    return scale
}

/**
 * Pulse animation for notifications and alerts
 */
@Composable
fun rememberPulseAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    return alpha
}

/**
 * Slide in from bottom animation
 */
fun slideInFromBottomAnimation() = slideInVertically(
    initialOffsetY = { it },
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
) + fadeIn(animationSpec = tween(300))

/**
 * Slide out to bottom animation
 */
fun slideOutToBottomAnimation() = slideOutVertically(
    targetOffsetY = { it },
    animationSpec = tween(300)
) + fadeOut(animationSpec = tween(300))

/**
 * Expand vertically animation
 */
fun expandAnimation() = expandVertically(
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
) + fadeIn(animationSpec = tween(300))

/**
 * Shrink vertically animation
 */
fun shrinkAnimation() = shrinkVertically(
    animationSpec = tween(300)
) + fadeOut(animationSpec = tween(300))

/**
 * Shimmer effect for loading states
 */
@Composable
fun rememberShimmerAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    return shimmer
}

/**
 * Shake animation for errors
 */
@Composable
fun rememberShakeAnimation(trigger: Boolean): Float {
    val shakeOffset by animateFloatAsState(
        targetValue = if (trigger) 10f else 0f,
        animationSpec = repeatable(
            iterations = 3,
            animation = tween(50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake"
    )
    return shakeOffset
}

/**
 * Rotation animation for refresh/loading
 */
@Composable
fun rememberRotationAnimation(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    return rotation
}

/**
 * Scale in animation modifier
 */
fun Modifier.scaleInAnimation(scale: Float) = this.graphicsLayer {
    scaleX = scale
    scaleY = scale
}

/**
 * Fade animation specs
 */
object AnimationSpecs {
    val fastFade = tween<Float>(200)
    val normalFade = tween<Float>(300)
    val slowFade = tween<Float>(500)
    
    val bounceSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    
    val smoothSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
