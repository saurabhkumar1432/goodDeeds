package com.browniepoints.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browniepoints.app.data.model.Notification
import com.browniepoints.app.data.service.InAppNotificationManager
import com.browniepoints.app.domain.usecase.InAppNotificationUseCase
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing in-app notifications
 */
@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val inAppNotificationUseCase: InAppNotificationUseCase,
    private val inAppNotificationManager: InAppNotificationManager,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        observeNotifications()
    }

    /**
     * Observes notifications for the current user
     */
    private fun observeNotifications() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            viewModelScope.launch {
                // Observe notifications
                inAppNotificationUseCase.getNotificationsForUser(currentUser.uid)
                    .collect { notifications ->
                        _uiState.value = _uiState.value.copy(
                            notifications = notifications,
                            isLoading = false
                        )
                    }
            }

            viewModelScope.launch {
                // Observe unread count
                inAppNotificationUseCase.getUnreadNotificationsCount(currentUser.uid)
                    .collect { count ->
                        _uiState.value = _uiState.value.copy(
                            unreadCount = count
                        )
                    }
            }
        }
    }

    /**
     * Marks a notification as read
     */
    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            inAppNotificationUseCase.markNotificationAsRead(notificationId)
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        error = exception.message ?: "Failed to mark notification as read"
                    )
                }
        }
    }
    
    /**
     * Handles notification click through the manager
     */
    fun onNotificationClick(notification: Notification) {
        inAppNotificationManager.onNotificationClick(notification)
    }

    /**
     * Marks all notifications as read
     */
    fun markAllNotificationsAsRead() {
        inAppNotificationManager.markAllAsRead()
    }

    /**
     * Deletes a notification
     */
    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            inAppNotificationUseCase.deleteNotification(notificationId)
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        error = exception.message ?: "Failed to delete notification"
                    )
                }
        }
    }

    /**
     * Shows a temporary notification (for immediate feedback)
     */
    fun showTemporaryNotification(notification: Notification) {
        _uiState.value = _uiState.value.copy(
            temporaryNotification = notification
        )
        
        // Auto-hide after 5 seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            hideTemporaryNotification()
        }
    }

    /**
     * Hides the temporary notification
     */
    fun hideTemporaryNotification() {
        _uiState.value = _uiState.value.copy(
            temporaryNotification = null
        )
    }

    /**
     * Clears any error messages
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Refreshes notifications
     */
    fun refreshNotifications() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        observeNotifications()
    }
}

/**
 * Data class representing the notification UI state
 */
data class NotificationUiState(
    val notifications: List<Notification> = emptyList(),
    val unreadCount: Int = 0,
    val temporaryNotification: Notification? = null,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    /**
     * Indicates if there are any notifications
     */
    val hasNotifications: Boolean = notifications.isNotEmpty()

    /**
     * Indicates if there are unread notifications
     */
    val hasUnreadNotifications: Boolean = unreadCount > 0

    /**
     * Indicates if there's a temporary notification to show
     */
    val hasTemporaryNotification: Boolean = temporaryNotification != null

    /**
     * Indicates if there's an error to display
     */
    val hasError: Boolean = error != null
}