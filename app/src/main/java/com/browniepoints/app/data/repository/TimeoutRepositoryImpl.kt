package com.browniepoints.app.data.repository

import android.util.Log
import com.browniepoints.app.data.model.Timeout
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeoutRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val timeoutNotificationService: com.browniepoints.app.data.service.TimeoutNotificationService
) : TimeoutRepository {

    companion object {
        private const val TIMEOUTS_COLLECTION = "timeouts"
        private const val TAG = "TimeoutRepository"
    }

    override suspend fun createTimeout(
        userId: String,
        connectionId: String
    ): Result<Timeout> {
        return try {
            Log.d(TAG, "Creating timeout for user: $userId, connection: $connectionId")
            
            // Check if user can request timeout today
            val canRequest = canRequestTimeout(userId).getOrThrow()
            if (!canRequest) {
                return Result.failure(Exception("User has already requested timeout today"))
            }
            
            // Create timeout document
            val timeoutRef = firestore.collection(TIMEOUTS_COLLECTION).document()
            val timeout = Timeout(
                id = timeoutRef.id,
                userId = userId,
                connectionId = connectionId,
                startTime = Timestamp.now(),
                duration = Timeout.DEFAULT_DURATION_MS,
                isActive = true,
                createdDate = getCurrentDateString()
            )
            
            // Validate timeout before saving
            val validationResult = timeout.validate()
            if (validationResult is com.browniepoints.app.data.validation.ValidationResult.Error) {
                return Result.failure(Exception("Invalid timeout data: ${validationResult.errors.joinToString(", ")}"))
            }
            
            timeoutRef.set(timeout).await()
            Log.d(TAG, "Timeout created successfully: ${timeout.id}")
            
            // Send timeout request notification
            val notificationResult = timeoutNotificationService.sendTimeoutRequestNotification(timeout)
            if (notificationResult.isFailure) {
                Log.w(TAG, "Failed to send timeout request notification", notificationResult.exceptionOrNull())
                // Don't fail the timeout creation if notification fails
            }
            
            Result.success(timeout)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating timeout", e)
            Result.failure(e)
        }
    }

    override suspend fun canRequestTimeout(userId: String): Result<Boolean> {
        return try {
            // Check today's timeout count
            val todayCount = getTodayTimeoutCount(userId).getOrThrow()
            val canRequest = todayCount < Timeout.MAX_TIMEOUTS_PER_DAY
            
            Log.d(TAG, "User $userId can request timeout: $canRequest (today count: $todayCount)")
            
            // Additional validation: check if user has any active timeouts
            if (canRequest) {
                val userActiveTimeouts = firestore.collection(TIMEOUTS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("active", true)
                    .get()
                    .await()
                
                val hasActiveTimeout = userActiveTimeouts.documents.any { doc ->
                    val timeout = doc.toObject(Timeout::class.java)
                    timeout != null && !timeout.hasExpired()
                }
                
                if (hasActiveTimeout) {
                    Log.d(TAG, "User $userId has an active timeout, cannot request another")
                    return Result.success(false)
                }
            }
            
            Result.success(canRequest)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking timeout eligibility", e)
            Result.failure(e)
        }
    }

    override suspend fun getActiveTimeout(connectionId: String): Result<Timeout?> {
        return try {
            Log.d(TAG, "Getting active timeout for connection: $connectionId")
            
            val snapshot = firestore.collection(TIMEOUTS_COLLECTION)
                .whereEqualTo("connectionId", connectionId)
                .whereEqualTo("active", true)
                .orderBy("startTime", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            
            val timeout = snapshot.documents.firstOrNull()?.toObject(Timeout::class.java)
            
            // Check if timeout has expired and handle expiration
            if (timeout != null && timeout.hasExpired()) {
                Log.d(TAG, "Found expired timeout, marking as inactive: ${timeout.id}")
                // Use atomic update to prevent race conditions
                firestore.collection(TIMEOUTS_COLLECTION)
                    .document(timeout.id)
                    .update(mapOf(
                        "active" to false,
                        "expiredAt" to com.google.firebase.Timestamp.now()
                    ))
                    .await()
                Result.success(null)
            } else {
                Log.d(TAG, "Active timeout found: ${timeout?.id}")
                Result.success(timeout)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active timeout", e)
            Result.failure(e)
        }
    }

    override fun observeActiveTimeout(connectionId: String): Flow<Timeout?> = callbackFlow {
        var listener: ListenerRegistration? = null
        
        try {
            listener = firestore.collection(TIMEOUTS_COLLECTION)
                .whereEqualTo("connectionId", connectionId)
                .whereEqualTo("active", true)
                .orderBy("startTime", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing active timeout", error)
                        // Check if it's an index building error
                        if (error.message?.contains("index is currently building") == true ||
                            error.message?.contains("requires an index") == true) {
                            Log.w(TAG, "Index is still building, will retry automatically")
                            trySend(null) // Send null instead of crashing
                            return@addSnapshotListener
                        }
                        close(error)
                        return@addSnapshotListener
                    }
                    
                    val timeout = snapshot?.documents?.firstOrNull()?.toObject(Timeout::class.java)
                    
                    // Check if timeout has expired
                    if (timeout != null && timeout.hasExpired()) {
                        Log.d(TAG, "Observed expired timeout, marking as inactive: ${timeout.id}")
                        // Mark as expired asynchronously with additional metadata
                        firestore.collection(TIMEOUTS_COLLECTION)
                            .document(timeout.id)
                            .update(mapOf(
                                "active" to false,
                                "expiredAt" to com.google.firebase.Timestamp.now(),
                                "autoExpired" to true
                            ))
                        trySend(null)
                    } else {
                        trySend(timeout)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up timeout observer", e)
            close(e)
        }
        
        awaitClose {
            listener?.remove()
        }
    }

    override suspend fun expireTimeout(timeoutId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Expiring timeout: $timeoutId")
            
            // Use atomic update with additional metadata
            firestore.collection(TIMEOUTS_COLLECTION)
                .document(timeoutId)
                .update(mapOf(
                    "active" to false,
                    "expiredAt" to com.google.firebase.Timestamp.now(),
                    "manuallyExpired" to true
                ))
                .await()
                
            Log.d(TAG, "Timeout expired successfully: $timeoutId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error expiring timeout", e)
            Result.failure(e)
        }
    }

    override suspend fun getTimeoutHistory(userId: String): Result<List<Timeout>> {
        return try {
            Log.d(TAG, "Getting timeout history for user: $userId")
            
            val snapshot = firestore.collection(TIMEOUTS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("startTime", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val timeouts = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Timeout::class.java)
            }
            
            Log.d(TAG, "Retrieved ${timeouts.size} timeouts for user: $userId")
            Result.success(timeouts)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting timeout history", e)
            Result.failure(e)
        }
    }

    override suspend fun getTodayTimeoutCount(userId: String): Result<Int> {
        return try {
            val todayString = getCurrentDateString()
            Log.d(TAG, "Getting today's timeout count for user: $userId, date: $todayString")
            
            val snapshot = firestore.collection(TIMEOUTS_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("createdDate", todayString)
                .get()
                .await()
            
            val count = snapshot.size()
            Log.d(TAG, "Today's timeout count for user $userId: $count")
            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting today's timeout count", e)
            Result.failure(e)
        }
    }

    override fun observeTimeoutStatus(connectionId: String): Flow<Boolean> = callbackFlow {
        var listener: ListenerRegistration? = null
        
        try {
            listener = firestore.collection(TIMEOUTS_COLLECTION)
                .whereEqualTo("connectionId", connectionId)
                .whereEqualTo("active", true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing timeout status", error)
                        // Check if it's an index building error
                        if (error.message?.contains("index is currently building") == true ||
                            error.message?.contains("requires an index") == true) {
                            Log.w(TAG, "Index is still building for timeout status, will retry automatically")
                            trySend(false) // Send false instead of crashing
                            return@addSnapshotListener
                        }
                        close(error)
                        return@addSnapshotListener
                    }
                    
                    val hasActiveTimeout = snapshot?.documents?.any { doc ->
                        val timeout = doc.toObject(Timeout::class.java)
                        if (timeout != null && timeout.hasExpired()) {
                            // Auto-expire expired timeouts
                            firestore.collection(TIMEOUTS_COLLECTION)
                                .document(timeout.id)
                                .update(mapOf(
                                    "active" to false,
                                    "expiredAt" to com.google.firebase.Timestamp.now(),
                                    "autoExpired" to true
                                ))
                            false
                        } else {
                            timeout != null
                        }
                    } ?: false
                    
                    Log.d(TAG, "Timeout status for connection $connectionId: $hasActiveTimeout")
                    trySend(hasActiveTimeout)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up timeout status observer", e)
            close(e)
        }
        
        awaitClose {
            listener?.remove()
        }
    }

    override suspend fun cleanupExpiredTimeouts(): Result<Unit> {
        return try {
            Log.d(TAG, "Cleaning up expired timeouts")
            
            val snapshot = firestore.collection(TIMEOUTS_COLLECTION)
                .whereEqualTo("active", true)
                .get()
                .await()
            
            val batch = firestore.batch()
            var expiredCount = 0
            
            snapshot.documents.forEach { doc ->
                val timeout = doc.toObject(Timeout::class.java)
                if (timeout != null && timeout.hasExpired()) {
                    // Update with cleanup metadata
                    batch.update(doc.reference, mapOf(
                        "active" to false,
                        "expiredAt" to com.google.firebase.Timestamp.now(),
                        "cleanupExpired" to true
                    ))
                    expiredCount++
                }
            }
            
            if (expiredCount > 0) {
                batch.commit().await()
                Log.d(TAG, "Cleaned up $expiredCount expired timeouts")
                
                // Send expiration notifications for cleaned up timeouts
                snapshot.documents.forEach { doc ->
                    val timeout = doc.toObject(Timeout::class.java)
                    if (timeout != null && timeout.hasExpired()) {
                        try {
                            timeoutNotificationService.sendTimeoutExpirationNotification(timeout)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to send expiration notification for timeout: ${timeout.id}", e)
                        }
                    }
                }
            } else {
                Log.d(TAG, "No expired timeouts to clean up")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up expired timeouts", e)
            Result.failure(e)
        }
    }

    override suspend fun synchronizePartnerTimeoutState(connectionId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Synchronizing partner timeout state for connection: $connectionId")
            
            // Get all active timeouts for this connection
            val snapshot = firestore.collection(TIMEOUTS_COLLECTION)
                .whereEqualTo("connectionId", connectionId)
                .whereEqualTo("active", true)
                .get()
                .await()
            
            val batch = firestore.batch()
            var syncedCount = 0
            
            // Check each timeout and expire if needed
            snapshot.documents.forEach { doc ->
                val timeout = doc.toObject(Timeout::class.java)
                if (timeout != null && timeout.hasExpired()) {
                    batch.update(doc.reference, mapOf(
                        "active" to false,
                        "expiredAt" to com.google.firebase.Timestamp.now(),
                        "syncExpired" to true
                    ))
                    syncedCount++
                }
            }
            
            if (syncedCount > 0) {
                batch.commit().await()
                Log.d(TAG, "Synchronized $syncedCount timeouts for connection: $connectionId")
            } else {
                Log.d(TAG, "No timeouts needed synchronization for connection: $connectionId")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error synchronizing partner timeout state", e)
            Result.failure(e)
        }
    }

    private fun getCurrentDateString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}