package com.browniepoints.app.data.repository

import com.browniepoints.app.data.error.AppError
import com.browniepoints.app.data.error.toAppError
import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.util.RetryUtil
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : UserRepository {

    companion object {
        private const val USERS_COLLECTION = "users"
    }

    override suspend fun createUser(user: User): Result<Unit> {
        return RetryUtil.retryWithExponentialBackoff {
            firestore.collection(USERS_COLLECTION)
                .document(user.uid)
                .set(user)
                .await()
        }
    }

    override suspend fun getUser(userId: String): Result<User?> {
        return RetryUtil.retryWithExponentialBackoff {
            val document = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()
            
            if (document.exists()) {
                document.toObject(User::class.java)
            } else {
                null
            }
        }
    }

    override suspend fun updateUser(user: User): Result<Unit> {
        return RetryUtil.retryWithExponentialBackoff {
            firestore.collection(USERS_COLLECTION)
                .document(user.uid)
                .set(user)
                .await()
        }
    }

    override suspend fun generateMatchingCode(): String {
        var code: String
        var isUnique = false
        
        do {
            // Generate a 6-character alphanumeric code
            code = (1..6)
                .map { Random.nextInt(0, 36) }
                .map { if (it < 10) ('0' + it) else ('A' + it - 10) }
                .joinToString("")
            
            // Check if code is unique in Firestore
            val existingUser = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("matchingCode", code)
                .get()
                .await()
            
            isUnique = existingUser.isEmpty
        } while (!isUnique)
        
        return code
    }

    override fun observeUser(userId: String): Flow<User?> = callbackFlow {
        var listenerRegistration: ListenerRegistration? = null
        
        try {
            listenerRegistration = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Handle permission errors gracefully (e.g., after logout)
                        if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            // User is no longer authenticated, close the flow gracefully
                            trySend(null)
                            close()
                        } else {
                            // Other errors should be propagated
                            close(error.toAppError())
                        }
                        return@addSnapshotListener
                    }
                    
                    val user = if (snapshot?.exists() == true) {
                        snapshot.toObject(User::class.java)
                    } else {
                        null
                    }
                    trySend(user)
                }
        } catch (e: Exception) {
            // Handle exceptions gracefully
            if (e is com.google.firebase.firestore.FirebaseFirestoreException && 
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                trySend(null)
                close()
            } else {
                close(e.toAppError())
            }
        }
        
        awaitClose {
            listenerRegistration?.remove()
        }
    }

    override suspend fun findUserByMatchingCode(matchingCode: String): Result<User?> {
        // Validate matching code format
        if (matchingCode.length != 6 || !matchingCode.all { it.isLetterOrDigit() }) {
            return Result.failure(AppError.InvalidMatchingCodeError("Matching code must be 6 alphanumeric characters"))
        }
        
        return RetryUtil.retryWithExponentialBackoff {
            val querySnapshot = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("matchingCode", matchingCode)
                .get()
                .await()
            
            if (!querySnapshot.isEmpty) {
                querySnapshot.documents.first().toObject(User::class.java)
            } else {
                null
            }
        }
    }
}