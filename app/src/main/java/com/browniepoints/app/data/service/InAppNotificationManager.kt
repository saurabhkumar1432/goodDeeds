package com.browniepoints.app.data.service

import android.util.Log
import com.browniepoints.app.data.model.Notification
import com.browniepoints.app.data.model.NotificationType
import com.browniepoints.app.data.repository.InAppNotificationRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing in-app notification display and interactions
 */
@Singleton
class InAppNotificationManager @Inject constructor(
    private val inAppNotificationRepository: InAppNotificationRepository,
    private val firebaseAuth: FirebaseAuth
) {
    
    companion object {
        private const val TAG = "InAppNotificationManager"
        private const val OVERLAY_DISPLAY_DURATION = 5000L // 5 seconds
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Flow for temporary overlay notifications
    private val _overlayNotification = MutableStateFlow<Notification?>(null)
    val overlayNotification: StateFlow<Notification?> = _overlayNotification.asStateFlow()
    
    // Flow for notification events (for navigation, etc.)
    private val _notificationEvents = MutableSharedFlow<NotificationEvent>()
    val notificationEvents: SharedFlow<NotificationEvent> = _notificationEvents.asSharedFlow()
    
    // Flow for notification sounds/vibrations
    private val _notificationAlerts = MutableSharedFlow<NotificationAlert>()
    val notificationAlerts: SharedFlow<NotificationAlert> = _notificationAlerts.asSharedFlow()
    
    /**
     * Shows a temporary overlay notification
     */
    fun showOverlayNotification(notification: Notification) {
        Log.d(TAG, "Showing overlay notification: ${notification.title}")
        
        serviceScope.launch {
            try {
                // Set the overlay notification
                _overlayNotification.value = notification
                
                // Emit alert for sound/vibration
                _notificationAlerts.emit(
                    NotificationAlert(
                        type = notification.type,
                        shouldVibrate = true,
                        shouldPlaySound = true
                    )
                )
                
                // Auto-hide after duration
                kotlinx.coroutines.delay(OVERLAY_DISPLAY_DURATION)
                if (_overlayNotification.value?.id == notification.id) {
                    hideOverlayNotification()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing overlay notification", e)
            }
        }
    }
    
    /**
     * Hides the current overlay notification
     */
    fun hideOverlayNotification() {
        Log.d(TAG, "Hiding overlay notification")
        _overlayNotification.value = null
    }
    
    /**
     * Handles notification click events
     */
    fun onNotificationClick(notification: Notification) {
        Log.d(TAG, "Notification clicked: ${notification.id}")
        
        serviceScope.launch {
            try {
                // Mark as read if not already
                if (!notification.isRead) {
                    inAppNotificationRepository.markNotificationAsRead(notification.id)
                }
                
                // Emit navigation event based on notification type
                val event = when (notification.type) {
                    NotificationType.POINTS_RECEIVED,
                    NotificationType.POINTS_DEDUCTED -> {
                        NotificationEvent.NavigateToTransactionHistory
                    }
                    NotificationType.CONNECTION_REQUEST -> {
                        NotificationEvent.NavigateToConnection(notification.senderId)
                    }
                    NotificationType.CONNECTION_ACCEPTED -> {
                        NotificationEvent.NavigateToMain
                    }
                    NotificationType.TIMEOUT_REQUESTED,
                    NotificationType.TIMEOUT_PARTNER_REQUEST,
                    NotificationType.TIMEOUT_EXPIRED -> {
                        NotificationEvent.NavigateToMain
                    }
                }
                
                _notificationEvents.emit(event)
                
                // Hide overlay if this notification is currently shown
                if (_overlayNotification.value?.id == notification.id) {
                    hideOverlayNotification()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling notification click", e)
            }
        }
    }
    
    /**
     * Processes a new notification (from FCM or real-time updates)
     */
    fun processNewNotification(notification: Notification) {
        Log.d(TAG, "Processing new notification: ${notification.title}")
        
        val currentUser = firebaseAuth.currentUser
        if (currentUser == null || notification.receiverId != currentUser.uid) {
            Log.w(TAG, "Notification not for current user, ignoring")
            return
        }
        
        serviceScope.launch {
            try {
                // Show overlay notification for immediate feedback
                showOverlayNotification(notification)
                
                // Emit event for any additional processing
                _notificationEvents.emit(NotificationEvent.NewNotificationReceived(notification))
                
                Log.d(TAG, "New notification processed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing new notification", e)
            }
        }
    }
    
    /**
     * Clears all notifications for the current user
     */
    fun clearAllNotifications() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            serviceScope.launch {
                try {
                    inAppNotificationRepository.deleteAllNotificationsForUser(currentUser.uid)
                    Log.d(TAG, "All notifications cleared for user: ${currentUser.uid}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing all notifications", e)
                }
            }
        }
    }
    
    /**
     * Marks all notifications as read for the current user
     */
    fun markAllAsRead() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            serviceScope.launch {
                try {
                    inAppNotificationRepository.markAllNotificationsAsRead(currentUser.uid)
                    Log.d(TAG, "All notifications marked as read for user: ${currentUser.uid}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error marking all notifications as read", e)
                }
            }
        }
    }
    
    /**
     * Gets notification priority for display ordering
     */
    private fun getNotificationPriority(type: NotificationType): Int {
        return when (type) {
            NotificationType.TIMEOUT_REQUESTED,
            NotificationType.TIMEOUT_PARTNER_REQUEST -> 1 // Highest priority
            NotificationType.POINTS_RECEIVED,
            NotificationType.POINTS_DEDUCTED -> 2
            NotificationType.CONNECTION_REQUEST -> 3
            NotificationType.CONNECTION_ACCEPTED -> 4
            NotificationType.TIMEOUT_EXPIRED -> 5 // Lowest priority
        }
    }
}

/**
 * Sealed class representing notification events
 */
sealed class NotificationEvent {
    object NavigateToMain : NotificationEvent()
    object NavigateToTransactionHistory : NotificationEvent()
    data class NavigateToConnection(val userId: String) : NotificationEvent()
    data class NewNotificationReceived(val notification: Notification) : NotificationEvent()
}

/**
 * Data class representing notification alert settings
 */
data class NotificationAlert(
    val type: NotificationType,
    val shouldVibrate: Boolean = true,
    val shouldPlaySound: Boolean = true,
    val priority: Int = 0
)