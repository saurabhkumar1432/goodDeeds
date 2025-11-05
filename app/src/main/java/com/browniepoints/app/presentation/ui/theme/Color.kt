package com.browniepoints.app.presentation.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Brownie Points App Colors - Enhanced
val BrownieGold = Color(0xFFD4AF37)
val BrownieGoldLight = Color(0xFFE6C547)
val BrownieGoldDark = Color(0xFFB8941F)
val ChocolateBrown = Color(0xFF8B4513)
val WarmCream = Color(0xFFFFF8DC)

// Additional vibrant colors for engagement
val SuccessGreen = Color(0xFF4CAF50)
val SuccessGreenLight = Color(0xFF81C784)
val ErrorRed = Color(0xFFE53935)
val ErrorRedLight = Color(0xFFEF5350)
val WarningOrange = Color(0xFFFF9800)
val WarningOrangeLight = Color(0xFFFFB74D)
val InfoBlue = Color(0xFF2196F3)
val InfoBlueLight = Color(0xFF64B5F6)

// Gradient backgrounds
val GoldGradient = Brush.horizontalGradient(
    colors = listOf(BrownieGoldLight, BrownieGold, BrownieGoldDark)
)

val SuccessGradient = Brush.horizontalGradient(
    colors = listOf(SuccessGreenLight, SuccessGreen)
)

val ErrorGradient = Brush.horizontalGradient(
    colors = listOf(ErrorRedLight, ErrorRed)
)

val WarmGradient = Brush.verticalGradient(
    colors = listOf(WarmCream, Color(0xFFFFF5E1))
)

val CelebrationGradient = Brush.radialGradient(
    colors = listOf(
        BrownieGoldLight.copy(alpha = 0.8f),
        BrownieGold.copy(alpha = 0.6f),
        Color.Transparent
    )
)