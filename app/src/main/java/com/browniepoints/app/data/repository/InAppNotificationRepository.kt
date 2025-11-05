package com.browniepoints.app.data.repository

import com.browniepoints.app.data.model.Notification
import kotlinx.coroutines.flow.Flow

interface InAppNotificationRepository {
    /**
     * Creates a new in-app notification
     */
    suspend fun createNotification(notification: Notification): Result<Unit>
    
    /**
     * Gets real-time notifications for a specific user
     */
    fun getNotificationsForUser(userId: String): Flow<List<Notification>>
    
    /**
     * Gets unread notifications count for a user
     */
    fun getUnreadNotificationsCount(userId: String): Flow<Int>
    
    /**
     * Marks a notification as read
     */
    suspend fun markNotificationAsRead(notificationId: String): Result<Unit>
    
    /**
     * Marks all notifications as read for a user
     */
    suspend fun markAllNotificationsAsRead(userId: String): Result<Unit>
    
    /**
     * Deletes a notification
     */
    suspend fun deleteNotification(notificationId: String): Result<Unit>
    
    /**
     * Deletes all notifications for a user
     */
    suspend fun deleteAllNotificationsForUser(userId: String): Result<Unit>
}