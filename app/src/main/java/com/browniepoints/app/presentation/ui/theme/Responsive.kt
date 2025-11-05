package com.browniepoints.app.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Responsive design utilities for adaptive UI across different screen sizes
 */

enum class ScreenSize {
    COMPACT,  // Phones in portrait
    MEDIUM,   // Large phones, small tablets
    EXPANDED  // Tablets, foldables
}

@Composable
fun getScreenSize(): ScreenSize {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    
    return when {
        screenWidthDp < 600 -> ScreenSize.COMPACT
        screenWidthDp < 840 -> ScreenSize.MEDIUM
        else -> ScreenSize.EXPANDED
    }
}

/**
 * Responsive spacing based on screen size
 */
object ResponsiveSpacing {
    @Composable
    fun extraSmall(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 4.dp
        ScreenSize.MEDIUM -> 6.dp
        ScreenSize.EXPANDED -> 8.dp
    }
    
    @Composable
    fun small(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 8.dp
        ScreenSize.MEDIUM -> 12.dp
        ScreenSize.EXPANDED -> 16.dp
    }
    
    @Composable
    fun medium(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 16.dp
        ScreenSize.MEDIUM -> 20.dp
        ScreenSize.EXPANDED -> 24.dp
    }
    
    @Composable
    fun large(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 24.dp
        ScreenSize.MEDIUM -> 32.dp
        ScreenSize.EXPANDED -> 40.dp
    }
    
    @Composable
    fun extraLarge(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 32.dp
        ScreenSize.MEDIUM -> 40.dp
        ScreenSize.EXPANDED -> 48.dp
    }
}

/**
 * Responsive text sizes
 */
object ResponsiveText {
    @Composable
    fun small(): TextUnit = when (getScreenSize()) {
        ScreenSize.COMPACT -> 12.sp
        ScreenSize.MEDIUM -> 13.sp
        ScreenSize.EXPANDED -> 14.sp
    }
    
    @Composable
    fun body(): TextUnit = when (getScreenSize()) {
        ScreenSize.COMPACT -> 14.sp
        ScreenSize.MEDIUM -> 15.sp
        ScreenSize.EXPANDED -> 16.sp
    }
    
    @Composable
    fun title(): TextUnit = when (getScreenSize()) {
        ScreenSize.COMPACT -> 18.sp
        ScreenSize.MEDIUM -> 20.sp
        ScreenSize.EXPANDED -> 22.sp
    }
    
    @Composable
    fun headline(): TextUnit = when (getScreenSize()) {
        ScreenSize.COMPACT -> 24.sp
        ScreenSize.MEDIUM -> 28.sp
        ScreenSize.EXPANDED -> 32.sp
    }
    
    @Composable
    fun display(): TextUnit = when (getScreenSize()) {
        ScreenSize.COMPACT -> 32.sp
        ScreenSize.MEDIUM -> 40.sp
        ScreenSize.EXPANDED -> 48.sp
    }
}

/**
 * Responsive icon sizes
 */
object ResponsiveIcon {
    @Composable
    fun small(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 20.dp
        ScreenSize.MEDIUM -> 22.dp
        ScreenSize.EXPANDED -> 24.dp
    }
    
    @Composable
    fun medium(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 24.dp
        ScreenSize.MEDIUM -> 28.dp
        ScreenSize.EXPANDED -> 32.dp
    }
    
    @Composable
    fun large(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 48.dp
        ScreenSize.MEDIUM -> 56.dp
        ScreenSize.EXPANDED -> 64.dp
    }
    
    @Composable
    fun extraLarge(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 72.dp
        ScreenSize.MEDIUM -> 84.dp
        ScreenSize.EXPANDED -> 96.dp
    }
}

/**
 * Responsive card sizes
 */
object ResponsiveCard {
    @Composable
    fun elevation(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 2.dp
        ScreenSize.MEDIUM -> 3.dp
        ScreenSize.EXPANDED -> 4.dp
    }
    
    @Composable
    fun cornerRadius(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 12.dp
        ScreenSize.MEDIUM -> 16.dp
        ScreenSize.EXPANDED -> 20.dp
    }
    
    @Composable
    fun padding(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 16.dp
        ScreenSize.MEDIUM -> 20.dp
        ScreenSize.EXPANDED -> 24.dp
    }
}

/**
 * Responsive button sizes
 */
object ResponsiveButton {
    @Composable
    fun height(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 48.dp
        ScreenSize.MEDIUM -> 52.dp
        ScreenSize.EXPANDED -> 56.dp
    }
    
    @Composable
    fun minWidth(): Dp = when (getScreenSize()) {
        ScreenSize.COMPACT -> 120.dp
        ScreenSize.MEDIUM -> 140.dp
        ScreenSize.EXPANDED -> 160.dp
    }
}

/**
 * Maximum content width for tablets
 */
@Composable
fun maxContentWidth(): Dp = when (getScreenSize()) {
    ScreenSize.COMPACT -> Dp.Infinity
    ScreenSize.MEDIUM -> 600.dp
    ScreenSize.EXPANDED -> 800.dp
}
