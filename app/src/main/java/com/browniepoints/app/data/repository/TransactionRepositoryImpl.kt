package com.browniepoints.app.data.repository

import com.browniepoints.app.data.model.Transaction
import com.browniepoints.app.data.model.TransactionType
import com.browniepoints.app.data.service.TransactionNotificationService
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val transactionNotificationService: TransactionNotificationService
) : TransactionRepository {

    companion object {
        private const val TRANSACTIONS_COLLECTION = "transactions"
        private const val USERS_COLLECTION = "users"
    }

    override suspend fun createTransaction(
        senderId: String,
        receiverId: String,
        points: Int,
        message: String?,
        connectionId: String,
        type: TransactionType
    ): Result<Transaction> {
        return try {
            android.util.Log.d("TransactionRepository", "Creating transaction: $senderId -> $receiverId ($points points)")
            
            // Validate input parameters
            if (senderId.isBlank() || receiverId.isBlank() || connectionId.isBlank()) {
                return Result.failure(IllegalArgumentException("Sender ID, receiver ID, and connection ID cannot be empty"))
            }
            
            if (senderId == receiverId) {
                return Result.failure(IllegalArgumentException("Sender and receiver cannot be the same user"))
            }
            
            // Validate points based on transaction type
            when (type) {
                TransactionType.GIVE -> {
                    if (points < Transaction.MIN_POINTS || points > Transaction.MAX_POINTS) {
                        return Result.failure(IllegalArgumentException("Points for giving must be between ${Transaction.MIN_POINTS} and ${Transaction.MAX_POINTS}"))
                    }
                }
                TransactionType.DEDUCT -> {
                    if (points > -Transaction.MIN_POINTS || points < -Transaction.MAX_POINTS) {
                        return Result.failure(IllegalArgumentException("Points for deduction must be between -${Transaction.MIN_POINTS} and -${Transaction.MAX_POINTS}"))
                    }
                }
            }
            
            // Validate message length
            if (!message.isNullOrBlank() && message.length > Transaction.MAX_MESSAGE_LENGTH) {
                return Result.failure(IllegalArgumentException("Message cannot exceed ${Transaction.MAX_MESSAGE_LENGTH} characters"))
            }
            
            // Use Firestore transaction to ensure atomicity
            val transaction = firestore.runTransaction { firestoreTransaction ->
                // IMPORTANT: All reads must happen before any writes in Firestore transactions
                
                // 1. First, verify the connection exists and is active
                val connectionRef = firestore.collection("connections").document(connectionId)
                val connectionDoc = firestoreTransaction.get(connectionRef)
                
                if (!connectionDoc.exists()) {
                    throw IllegalArgumentException("Connection does not exist")
                }
                
                val connection = connectionDoc.toObject(com.browniepoints.app.data.model.Connection::class.java)
                if (connection == null || !connection.isActive) {
                    throw IllegalArgumentException("Connection is not active")
                }
                
                // Verify that both users are part of this connection
                if (!connection.containsUser(senderId) || !connection.containsUser(receiverId)) {
                    throw IllegalArgumentException("Users are not part of this connection")
                }
                
                // 2. Read current receiver points
                val receiverRef = firestore.collection(USERS_COLLECTION).document(receiverId)
                val receiverDoc = firestoreTransaction.get(receiverRef)
                
                if (!receiverDoc.exists()) {
                    throw IllegalArgumentException("Receiver user does not exist")
                }
                
                val currentPoints = receiverDoc.getLong("totalPointsReceived") ?: 0L
                android.util.Log.d("TransactionRepository", "Current points for receiver: $currentPoints")
                
                // 3. Then, do all WRITE operations
                // Create transaction document
                val transactionRef = firestore.collection(TRANSACTIONS_COLLECTION).document()
                val pointsTransaction = Transaction(
                    id = transactionRef.id,
                    senderId = senderId,
                    receiverId = receiverId,
                    points = points,
                    message = message,
                    timestamp = Timestamp.now(),
                    connectionId = connectionId,
                    type = type
                )
                
                // Validate the transaction object
                val validationResult = pointsTransaction.validate()
                if (validationResult.isError) {
                    throw IllegalArgumentException("Transaction validation failed: ${(validationResult as com.browniepoints.app.data.validation.ValidationResult.Error).allErrors}")
                }
                
                // Set transaction document
                firestoreTransaction.set(transactionRef, pointsTransaction)
                android.util.Log.d("TransactionRepository", "Transaction document created: ${transactionRef.id}")
                
                // Update receiver's total points (allowing negative balances for deductions)
                val newTotalPoints = currentPoints + points
                firestoreTransaction.update(receiverRef, "totalPointsReceived", newTotalPoints)
                android.util.Log.d("TransactionRepository", "Updated receiver points: $currentPoints -> $newTotalPoints (change: $points)")
                
                pointsTransaction
            }.await()
            
            android.util.Log.d("TransactionRepository", "Transaction completed successfully")
            
            // Send notification for the transaction (don't fail if notification fails)
            try {
                val notificationResult = transactionNotificationService.sendTransactionNotification(transaction)
                if (notificationResult.isFailure) {
                    android.util.Log.w("TransactionRepository", "Failed to send transaction notification", notificationResult.exceptionOrNull())
                }
            } catch (e: Exception) {
                android.util.Log.w("TransactionRepository", "Exception while sending notification", e)
            }
            
            Result.success(transaction)
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "Error creating transaction", e)
            Result.failure(e)
        }
    }

    override fun observeTransactions(userId: String): Flow<List<Transaction>> = callbackFlow {
        var sentListener: ListenerRegistration? = null
        var receivedListener: ListenerRegistration? = null
        
        val sentTransactions = mutableListOf<Transaction>()
        val receivedTransactions = mutableListOf<Transaction>()
        
        fun emitCombinedTransactions() {
            val allTransactions = (sentTransactions + receivedTransactions)
                .sortedByDescending { it.timestamp }
            trySend(allTransactions)
        }
        
        try {
            // Listen to sent transactions
            sentListener = firestore.collection(TRANSACTIONS_COLLECTION)
                .whereEqualTo("senderId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    
                    sentTransactions.clear()
                    sentTransactions.addAll(snapshot?.toObjects(Transaction::class.java) ?: emptyList())
                    emitCombinedTransactions()
                }
            
            // Listen to received transactions
            receivedListener = firestore.collection(TRANSACTIONS_COLLECTION)
                .whereEqualTo("receiverId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    
                    receivedTransactions.clear()
                    receivedTransactions.addAll(snapshot?.toObjects(Transaction::class.java) ?: emptyList())
                    emitCombinedTransactions()
                }
        } catch (e: Exception) {
            close(e)
        }
        
        awaitClose {
            sentListener?.remove()
            receivedListener?.remove()
        }
    }

    override suspend fun getTransactionHistory(userId: String): Result<List<Transaction>> {
        return try {
            // Get sent transactions
            val sentTransactions = firestore.collection(TRANSACTIONS_COLLECTION)
                .whereEqualTo("senderId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Transaction::class.java)
            
            // Get received transactions
            val receivedTransactions = firestore.collection(TRANSACTIONS_COLLECTION)
                .whereEqualTo("receiverId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
                .toObjects(Transaction::class.java)
            
            // Combine and sort by timestamp
            val allTransactions = (sentTransactions + receivedTransactions)
                .sortedByDescending { it.timestamp }
            
            Result.success(allTransactions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}