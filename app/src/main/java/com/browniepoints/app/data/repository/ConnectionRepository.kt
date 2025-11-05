package com.browniepoints.app.data.repository

import com.browniepoints.app.data.model.Connection
import kotlinx.coroutines.flow.Flow

interface ConnectionRepository {
    suspend fun createConnection(userId1: String, userId2: String): Result<Connection>
    suspend fun getConnection(userId: String): Result<Connection?>
    suspend fun getConnectionById(connectionId: String): Result<Connection?>
    fun observeConnection(userId: String): Flow<Connection?>
    suspend fun validateMatchingCode(matchingCode: String): Result<String?>
    suspend fun disconnectUsers(connectionId: String): Result<Unit>
}