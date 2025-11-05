package com.browniepoints.app.data.repository

import com.browniepoints.app.data.model.Transaction
import com.browniepoints.app.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    suspend fun createTransaction(
        senderId: String,
        receiverId: String,
        points: Int,
        message: String?,
        connectionId: String,
        type: TransactionType = TransactionType.GIVE
    ): Result<Transaction>
    
    /**
     * Creates a positive point transaction (giving brownie points)
     */
    suspend fun givePoints(
        senderId: String,
        receiverId: String,
        points: Int,
        message: String?,
        connectionId: String
    ): Result<Transaction> = createTransaction(senderId, receiverId, points, message, connectionId, TransactionType.GIVE)
    
    /**
     * Creates a negative point transaction (deducting points for conflicts)
     */
    suspend fun deductPoints(
        senderId: String,
        receiverId: String,
        points: Int,
        reason: String?,
        connectionId: String
    ): Result<Transaction> = createTransaction(senderId, receiverId, -points, reason, connectionId, TransactionType.DEDUCT)
    
    fun observeTransactions(userId: String): Flow<List<Transaction>>
    suspend fun getTransactionHistory(userId: String): Result<List<Transaction>>
}