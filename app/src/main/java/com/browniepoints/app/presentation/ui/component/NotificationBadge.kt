package com.browniepoints.app.presentation.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composable for displaying a notification badge with unread count
 */
@Composable
fun NotificationBadge(
    count: Int,
    modifier: Modifier = Modifier,
    showZero: Boolean = false
) {
    if (count > 0 || showZero) {
        Box(
            modifier = modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Composable for displaying a notification badge positioned over another composable
 */
@Composable
fun BadgedBox(
    badge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
        ) {
            badge()
        }
    }
}

/**
 * Convenience composable for a notification icon with badge
 */
@Composable
fun NotificationIconWithBadge(
    unreadCount: Int,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    BadgedBox(
        badge = {
            if (unreadCount > 0) {
                NotificationBadge(count = unreadCount)
            }
        },
        modifier = modifier
    ) {
        icon()
    }
}