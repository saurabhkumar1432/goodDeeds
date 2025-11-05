package com.browniepoints.app.data.repository

import com.browniepoints.app.data.model.Connection
import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.validation.ValidationResult
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : ConnectionRepository {

    companion object {
        private const val CONNECTIONS_COLLECTION = "connections"
        private const val USERS_COLLECTION = "users"
    }

    override suspend fun createConnection(userId1: String, userId2: String): Result<Connection> {
        return try {
            android.util.Log.d("ConnectionRepository", "Creating connection between $userId1 and $userId2")
            
            // Validate input parameters
            if (userId1.isBlank() || userId2.isBlank()) {
                return Result.failure(IllegalArgumentException("User IDs cannot be empty"))
            }
            
            if (userId1 == userId2) {
                return Result.failure(IllegalArgumentException("User cannot connect to themselves"))
            }
            
            // Check if either user already has an active connection
            val existingConnection1 = getConnection(userId1).getOrNull()
            val existingConnection2 = getConnection(userId2).getOrNull()
            
            if (existingConnection1 != null) {
                return Result.failure(IllegalStateException("User $userId1 already has an active connection"))
            }
            
            if (existingConnection2 != null) {
                return Result.failure(IllegalStateException("User $userId2 already has an active connection"))
            }
            
            // Use Firestore transaction to ensure atomicity
            val connection = firestore.runTransaction { transaction ->
                // Create connection document with auto-generated ID
                val connectionRef = firestore.collection(CONNECTIONS_COLLECTION).document()
                val connectionId = connectionRef.id
                
                android.util.Log.d("ConnectionRepository", "Generated connection ID: $connectionId")
                
                val connection = Connection(
                    id = connectionId,
                    user1Id = userId1,
                    user2Id = userId2,
                    createdAt = Timestamp.now(),
                    isActive = true
                )
                
                // Validate the connection before saving
                when (val validationResult = connection.validate()) {
                    is ValidationResult.Error -> {
                        throw IllegalArgumentException("Connection validation failed: ${validationResult.errors.joinToString(", ")}")
                    }
                    is ValidationResult.Success -> {
                        // Continue with creation
                    }
                }
                
                android.util.Log.d("ConnectionRepository", "Setting connection document: $connectionId")
                // Set connection document
                transaction.set(connectionRef, connection)
                
                // Update both users' connectedUserId field
                val user1Ref = firestore.collection(USERS_COLLECTION).document(userId1)
                val user2Ref = firestore.collection(USERS_COLLECTION).document(userId2)
                
                android.util.Log.d("ConnectionRepository", "Updating user documents with connection info")
                transaction.update(user1Ref, "connectedUserId", userId2)
                transaction.update(user2Ref, "connectedUserId", userId1)
                
                connection
            }.await()
            
            android.util.Log.d("ConnectionRepository", "Connection created successfully: ${connection.id}")
            Result.success(connection)
        } catch (e: Exception) {
            android.util.Log.e("ConnectionRepository", "Error creating connection", e)
            Result.failure(e)
        }
    }

    override suspend fun getConnection(userId: String): Result<Connection?> {
        return try {
            android.util.Log.d("ConnectionRepository", "Getting connection for user: $userId")
            
            // Use array-contains-any to query both user1Id and user2Id in a single query
            // Since Firestore doesn't support OR queries directly, we'll use two separate queries
            // but handle them more efficiently
            
            // First check if user is user1
            val querySnapshot1 = firestore.collection(CONNECTIONS_COLLECTION)
                .whereEqualTo("user1Id", userId)
                .whereEqualTo("isActive", true)
                .limit(1)
                .get()
                .await()
            
            if (!querySnapshot1.isEmpty) {
                val connection = querySnapshot1.documents.first().toObject(Connection::class.java)
                android.util.Log.d("ConnectionRepository", "Found connection as user1: ${connection?.id}")
                return Result.success(connection)
            }
            
            // Then check if user is user2
            val querySnapshot2 = firestore.collection(CONNECTIONS_COLLECTION)
                .whereEqualTo("user2Id", userId)
                .whereEqualTo("isActive", true)
                .limit(1)
                .get()
                .await()
            
            val connection = if (!querySnapshot2.isEmpty) {
                querySnapshot2.documents.first().toObject(Connection::class.java)
            } else {
                null
            }
            
            android.util.Log.d("ConnectionRepository", "Found connection as user2: ${connection?.id}")
            Result.success(connection)
        } catch (e: Exception) {
            android.util.Log.e("ConnectionRepository", "Error getting connection for user: $userId", e)
            Result.failure(e)
        }
    }

    override fun observeConnection(userId: String): Flow<Connection?> = callbackFlow {
        var listenerRegistration1: ListenerRegistration? = null
        var listenerRegistration2: ListenerRegistration? = null
        var currentConnection: Connection? = null
        
        try {
            android.util.Log.d("ConnectionRepository", "Starting to observe connection for user: $userId")
            
            // Listen for connections where user is user1
            listenerRegistration1 = firestore.collection(CONNECTIONS_COLLECTION)
                .whereEqualTo("user1Id", userId)
                .whereEqualTo("isActive", true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("ConnectionRepository", "Error in user1 listener", error)
                        // Handle PERMISSION_DENIED (e.g., after logout) gracefully
                        if (error is com.google.firebase.firestore.FirebaseFirestoreException &&
                            error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            android.util.Log.d("ConnectionRepository", "Permission denied, closing connection listener")
                            trySend(null)
                            close()
                            return@addSnapshotListener
                        }
                        close(error)
                        return@addSnapshotListener
                    }
                    
                    val connection = if (snapshot != null && !snapshot.isEmpty) {
                        snapshot.documents.first().toObject(Connection::class.java)
                    } else {
                        null
                    }
                    
                    // Only send if this is a different connection or state change
                    if (connection != currentConnection) {
                        currentConnection = connection
                        android.util.Log.d("ConnectionRepository", "Connection update (as user1): ${connection?.id}")
                        trySend(connection)
                    }
                }
            
            // Listen for connections where user is user2
            listenerRegistration2 = firestore.collection(CONNECTIONS_COLLECTION)
                .whereEqualTo("user2Id", userId)
                .whereEqualTo("isActive", true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("ConnectionRepository", "Error in user2 listener", error)
                        // Handle PERMISSION_DENIED (e.g., after logout) gracefully
                        if (error is com.google.firebase.firestore.FirebaseFirestoreException &&
                            error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            android.util.Log.d("ConnectionRepository", "Permission denied, closing connection listener")
                            trySend(null)
                            close()
                            return@addSnapshotListener
                        }
                        close(error)
                        return@addSnapshotListener
                    }
                    
                    val connection = if (snapshot != null && !snapshot.isEmpty) {
                        snapshot.documents.first().toObject(Connection::class.java)
                    } else {
                        null
                    }
                    
                    // Only send if this is a different connection or state change
                    // and we don't already have a connection from the user1 listener
                    if (connection != currentConnection && currentConnection == null) {
                        currentConnection = connection
                        android.util.Log.d("ConnectionRepository", "Connection update (as user2): ${connection?.id}")
                        trySend(connection)
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("ConnectionRepository", "Error setting up connection listeners", e)
            close(e)
        }
        
        awaitClose {
            android.util.Log.d("ConnectionRepository", "Closing connection listeners for user: $userId")
            listenerRegistration1?.remove()
            listenerRegistration2?.remove()
        }
    }

    override suspend fun getConnectionById(connectionId: String): Result<Connection?> {
        return try {
            android.util.Log.d("ConnectionRepository", "Getting connection by ID: $connectionId")
            val documentSnapshot = firestore.collection(CONNECTIONS_COLLECTION)
                .document(connectionId)
                .get()
                .await()
            
            val connection = if (documentSnapshot.exists()) {
                documentSnapshot.toObject(Connection::class.java)
            } else {
                null
            }
            
            android.util.Log.d("ConnectionRepository", "Connection found: ${connection != null}")
            Result.success(connection)
        } catch (e: Exception) {
            android.util.Log.e("ConnectionRepository", "Error getting connection by ID", e)
            Result.failure(e)
        }
    }

    override suspend fun validateMatchingCode(matchingCode: String): Result<String?> {
        return try {
            android.util.Log.d("ConnectionRepository", "Validating matching code: $matchingCode")
            
            // Validate matching code format
            if (matchingCode.isBlank()) {
                return Result.failure(IllegalArgumentException("Matching code cannot be empty"))
            }
            
            if (matchingCode.length != 6) {
                return Result.failure(IllegalArgumentException("Matching code must be exactly 6 characters"))
            }
            
            val querySnapshot = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("matchingCode", matchingCode)
                .limit(1)
                .get()
                .await()
            
            val userId = if (!querySnapshot.isEmpty) {
                val user = querySnapshot.documents.first().toObject(User::class.java)
                android.util.Log.d("ConnectionRepository", "Found user with matching code: ${user?.uid}")
                
                // Check if the found user already has a connection
                if (!user?.connectedUserId.isNullOrBlank()) {
                    android.util.Log.d("ConnectionRepository", "User with matching code already has a connection")
                    return Result.failure(IllegalStateException("User with this matching code is already connected"))
                }
                
                user?.uid
            } else {
                android.util.Log.d("ConnectionRepository", "No user found with matching code: $matchingCode")
                null
            }
            
            Result.success(userId)
        } catch (e: Exception) {
            android.util.Log.e("ConnectionRepository", "Error validating matching code", e)
            Result.failure(e)
        }
    }

    override suspend fun disconnectUsers(connectionId: String): Result<Unit> {
        return try {
            android.util.Log.d("ConnectionRepository", "Disconnecting users for connection: $connectionId")
            
            // Get the connection first to know which users to update
            val connection = getConnectionById(connectionId).getOrNull()
                ?: return Result.failure(IllegalArgumentException("Connection not found"))
            
            // Use Firestore transaction to ensure atomicity
            firestore.runTransaction { transaction ->
                // Deactivate the connection
                val connectionRef = firestore.collection(CONNECTIONS_COLLECTION).document(connectionId)
                transaction.update(connectionRef, "isActive", false)
                
                // Clear both users' connectedUserId field
                val user1Ref = firestore.collection(USERS_COLLECTION).document(connection.user1Id)
                val user2Ref = firestore.collection(USERS_COLLECTION).document(connection.user2Id)
                
                transaction.update(user1Ref, "connectedUserId", null)
                transaction.update(user2Ref, "connectedUserId", null)
            }.await()
            
            android.util.Log.d("ConnectionRepository", "Users disconnected successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ConnectionRepository", "Error disconnecting users", e)
            Result.failure(e)
        }
    }
}