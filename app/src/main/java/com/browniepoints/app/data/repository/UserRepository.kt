package com.browniepoints.app.data.repository

import com.browniepoints.app.data.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun createUser(user: User): Result<Unit>
    suspend fun getUser(userId: String): Result<User?>
    suspend fun updateUser(user: User): Result<Unit>
    suspend fun generateMatchingCode(): String
    fun observeUser(userId: String): Flow<User?>
    suspend fun findUserByMatchingCode(matchingCode: String): Result<User?>
}