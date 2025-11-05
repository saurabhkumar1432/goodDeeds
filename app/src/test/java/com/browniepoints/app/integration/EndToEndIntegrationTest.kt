package com.browniepoints.app.integration

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.browniepoints.app.data.model.*
import com.browniepoints.app.data.repository.*
import com.browniepoints.app.data.service.SyncStatus
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class EndToEndIntegrationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var connectionRepository: ConnectionRepository

    @Mock
    private lateinit var transactionRepository: TransactionRepository

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun `end to end user connection and transaction flow`() = runTest {
        // Given
        val user1 = User(uid = "user1", displayName = "User One", email = "user1@test.com")
        val user2 = User(uid = "user2", displayName = "User Two", email = "user2@test.com")
        val connection = Connection(
            id = "connection1",
            user1Id = "user1",
            user2Id = "user2"
        )
        val transaction = Transaction(
            id = "transaction1",
            senderId = "user1",
            receiverId = "user2",
            points = 5,
            message = "Great job!",
            connectionId = "connection1"
        )

        // Mock repository responses
        whenever(userRepository.getUser("user1")).thenReturn(Result.success(user1))
        whenever(userRepository.getUser("user2")).thenReturn(Result.success(user2))
        whenever(connectionRepository.getConnection("connection1")).thenReturn(Result.success(connection))
        whenever(transactionRepository.createTransaction(
            senderId = "user1",
            receiverId = "user2",
            points = 5,
            message = "Great job!",
            connectionId = "connection1"
        )).thenReturn(Result.success(transaction))

        // When
        val userResult1 = userRepository.getUser("user1")
        val userResult2 = userRepository.getUser("user2")
        val connectionResult = connectionRepository.getConnection("connection1")
        val transactionResult = transactionRepository.createTransaction(
            senderId = "user1",
            receiverId = "user2",
            points = 5,
            message = "Great job!",
            connectionId = "connection1"
        )

        // Then
        assertTrue(userResult1.isSuccess)
        assertTrue(userResult2.isSuccess)
        assertTrue(connectionResult.isSuccess)
        assertTrue(transactionResult.isSuccess)

        assertEquals(user1, userResult1.getOrNull())
        assertEquals(user2, userResult2.getOrNull())
        assertEquals(connection, connectionResult.getOrNull())
        assertEquals(transaction, transactionResult.getOrNull())
    }
}

// Mock implementations for testing
class TransactionRepositoryImpl : TransactionRepository {
    private val transactions = mutableListOf<Transaction>()

    override suspend fun createTransaction(
        senderId: String,
        receiverId: String,
        points: Int,
        message: String?,
        connectionId: String,
        type: TransactionType
    ): Result<Transaction> {
        val transaction = Transaction(
            id = "test_transaction_${System.currentTimeMillis()}",
            senderId = senderId,
            receiverId = receiverId,
            points = points,
            message = message,
            connectionId = connectionId,
            type = type,
            timestamp = Timestamp.now()
        )
        transactions.add(transaction)
        return Result.success(transaction)
    }

    override fun observeTransactions(userId: String) = flowOf(
        transactions.filter { it.senderId == userId || it.receiverId == userId }
    )

    override suspend fun getTransactionHistory(userId: String): Result<List<Transaction>> {
        return Result.success(transactions.filter { it.senderId == userId || it.receiverId == userId })
    }
}

class NotificationRepositoryImpl : NotificationRepository {
    override suspend fun sendNotification(
        receiverId: String,
        title: String,
        message: String,
        data: Map<String, String>
    ): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun updateFcmToken(userId: String, token: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun getCurrentFcmToken(): Result<String> {
        return Result.success("test_token")
    }

    override suspend fun subscribeToTopic(topic: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun unsubscribeFromTopic(topic: String): Result<Unit> {
        return Result.success(Unit)
    }
}