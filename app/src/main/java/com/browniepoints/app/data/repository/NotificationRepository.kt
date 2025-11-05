package com.browniepoints.app.data.repository

interface NotificationRepository {
    suspend fun updateFcmToken(userId: String, token: String): Result<Unit>
    suspend fun sendNotification(
        receiverId: String,
        title: String,
        message: String,
        data: Map<String, String> = emptyMap()
    ): Result<Unit>
    suspend fun getCurrentFcmToken(): Result<String>
    suspend fun subscribeToTopic(topic: String): Result<Unit>
    suspend fun unsubscribeFromTopic(topic: String): Result<Unit>
}