package com.browniepoints.app.data.repository

import android.util.Log
import com.browniepoints.app.data.model.Notification
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppNotificationRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : InAppNotificationRepository {

    companion object {
        private const val TAG = "InAppNotificationRepo"
        private const val NOTIFICATIONS_COLLECTION = "notifications"
    }

    override suspend fun createNotification(notification: Notification): Result<Unit> {
        return try {
            Log.d(TAG, "Creating notification for user: ${notification.receiverId}")
            
            // Validate notification data
            val validationResult = notification.validate()
            if (validationResult is com.browniepoints.app.data.validation.ValidationResult.Error) {
                return Result.failure(Exception("Invalid notification data: ${validationResult.errors.joinToString(", ")}"))
            }
            
            firestore.collection(NOTIFICATIONS_COLLECTION)
                .add(notification)
                .await()
            
            Log.d(TAG, "Notification created successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create notification", e)
            Result.failure(e)
        }
    }

    override fun getNotificationsForUser(userId: String): Flow<List<Notification>> = callbackFlow {
        Log.d(TAG, "Setting up notifications listener for user: $userId")
        
        val listener = firestore.collection(NOTIFICATIONS_COLLECTION)
            .whereEqualTo("receiverId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to notifications", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val notifications = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(Notification::class.java)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing notification document: ${doc.id}", e)
                            null
                        }
                    }
                    
                    Log.d(TAG, "Received ${notifications.size} notifications for user: $userId")
                    trySend(notifications)
                }
            }
        
        awaitClose {
            Log.d(TAG, "Removing notifications listener for user: $userId")
            listener.remove()
        }
    }

    override fun getUnreadNotificationsCount(userId: String): Flow<Int> = callbackFlow {
        Log.d(TAG, "Setting up unread notifications count listener for user: $userId")
        
        val listener = firestore.collection(NOTIFICATIONS_COLLECTION)
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to unread notifications count", error)
                    return@addSnapshotListener
                }
                
                val count = snapshot?.size() ?: 0
                Log.d(TAG, "Unread notifications count for user $userId: $count")
                trySend(count)
            }
        
        awaitClose {
            Log.d(TAG, "Removing unread notifications count listener for user: $userId")
            listener.remove()
        }
    }

    override suspend fun markNotificationAsRead(notificationId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Marking notification as read: $notificationId")
            
            firestore.collection(NOTIFICATIONS_COLLECTION)
                .document(notificationId)
                .update("isRead", true)
                .await()
            
            Log.d(TAG, "Notification marked as read successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark notification as read", e)
            Result.failure(e)
        }
    }

    override suspend fun markAllNotificationsAsRead(userId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Marking all notifications as read for user: $userId")
            
            val batch = firestore.batch()
            val notifications = firestore.collection(NOTIFICATIONS_COLLECTION)
                .whereEqualTo("receiverId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()
            
            notifications.documents.forEach { doc ->
                batch.update(doc.reference, "isRead", true)
            }
            
            batch.commit().await()
            
            Log.d(TAG, "All notifications marked as read successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark all notifications as read", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteNotification(notificationId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Deleting notification: $notificationId")
            
            firestore.collection(NOTIFICATIONS_COLLECTION)
                .document(notificationId)
                .delete()
                .await()
            
            Log.d(TAG, "Notification deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete notification", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteAllNotificationsForUser(userId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Deleting all notifications for user: $userId")
            
            val batch = firestore.batch()
            val notifications = firestore.collection(NOTIFICATIONS_COLLECTION)
                .whereEqualTo("receiverId", userId)
                .get()
                .await()
            
            notifications.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            
            batch.commit().await()
            
            Log.d(TAG, "All notifications deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete all notifications", e)
            Result.failure(e)
        }
    }
}