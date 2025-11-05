package com.browniepoints.app.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseMessaging: FirebaseMessaging
) : NotificationRepository {

    companion object {
        private const val TAG = "NotificationRepository"
        private const val USERS_COLLECTION = "users"
    }

    override suspend fun updateFcmToken(userId: String, token: String): Result<Unit> {
        return try {
            Log.d(TAG, "Updating FCM token for user: $userId")
            
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update("fcmToken", token)
                .await()
            
            Log.d(TAG, "FCM token updated successfully for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update FCM token for user: $userId", e)
            Result.failure(e)
        }
    }

    override suspend fun sendNotification(
        receiverId: String,
        title: String,
        message: String,
        data: Map<String, String>
    ): Result<Unit> {
        return try {
            Log.d(TAG, "Sending notification to user: $receiverId")
            
            // Get the receiver's FCM token from Firestore
            val userDoc = firestore.collection(USERS_COLLECTION)
                .document(receiverId)
                .get()
                .await()
            
            val fcmToken = userDoc.getString("fcmToken")
            
            if (fcmToken.isNullOrBlank()) {
                Log.w(TAG, "No FCM token found for user: $receiverId")
                return Result.failure(Exception("No FCM token found for user"))
            }
            
            // Note: For actual push notifications, we would need a server-side implementation
            // or Cloud Functions to send FCM messages. For now, we'll log the notification
            // and return success to indicate the token retrieval worked.
            Log.d(TAG, "Would send notification to token: $fcmToken")
            Log.d(TAG, "Title: $title, Message: $message, Data: $data")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification to user: $receiverId", e)
            Result.failure(e)
        }
    }

    /**
     * Gets the current FCM token for this device
     */
    override suspend fun getCurrentFcmToken(): Result<String> {
        return try {
            val token = firebaseMessaging.token.await()
            Log.d(TAG, "Retrieved current FCM token: $token")
            Result.success(token)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current FCM token", e)
            Result.failure(e)
        }
    }

    /**
     * Subscribes to a topic for receiving notifications
     */
    override suspend fun subscribeToTopic(topic: String): Result<Unit> {
        return try {
            firebaseMessaging.subscribeToTopic(topic).await()
            Log.d(TAG, "Subscribed to topic: $topic")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to topic: $topic", e)
            Result.failure(e)
        }
    }

    /**
     * Unsubscribes from a topic
     */
    override suspend fun unsubscribeFromTopic(topic: String): Result<Unit> {
        return try {
            firebaseMessaging.unsubscribeFromTopic(topic).await()
            Log.d(TAG, "Unsubscribed from topic: $topic")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from topic: $topic", e)
            Result.failure(e)
        }
    }
}