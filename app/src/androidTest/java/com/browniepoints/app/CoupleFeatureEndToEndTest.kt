package com.browniepoints.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.browniepoints.app.data.repository.TimeoutRepository
import com.browniepoints.app.data.repository.TransactionRepository
import com.browniepoints.app.data.repository.UserRepository
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * End-to-end integration tests for couple-specific features
 * 
 * These tests verify the complete workflow of:
 * - Point deduction system for conflict management
 * - Daily timeout system for cooling down during arguments
 * - Integration between timeout and transaction systems
 * 
 * Requirements tested: 7.1-7.7, 8.1-8.10
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CoupleFeatureEndToEndTest : BaseFirebaseTest() {
    
    @Inject
    lateinit var transactionRepository: TransactionRepository
    
    @Inject
    lateinit var timeoutRepository: TimeoutRepository
    
    @Inject
    lateinit var userRepository: UserRepository
    
    @Test
    fun testCompleteConflictResolutionWorkflow() = runTest {
        val user1Id = "couple-user-1"
        val user2Id = "couple-user-2"
        val connectionId = "couple-connection-1"
        
        // Create couple users
        createTestUser(user1Id, "Partner 1", "partner1@example.com", "CPL001")
        createTestUser(user2Id, "Partner 2", "partner2@example.com", "CPL002")
        
        // Step 1: Partners give each other points initially
        transactionRepository.givePoints(
            senderId = user1Id,
            receiverId = user2Id,
            points = 8,
            message = "Thanks for cooking dinner!",
            connectionId = connectionId
        )
        
        transactionRepository.givePoints(
            senderId = user2Id,
            receiverId = user1Id,
            points = 6,
            message = "Thanks for doing the dishes!",
            connectionId = connectionId
        )
        
        // Verify initial positive balances
        val user1Doc = firestore.collection("users").document(user1Id).get().await()
        val user2Doc = firestore.collection("users").document(user2Id).get().await()
        assertEquals("User 1 should have 6 points", 6L, user1Doc.getLong("totalPointsReceived"))
        assertEquals("User 2 should have 8 points", 8L, user2Doc.getLong("totalPointsReceived"))
        
        // Step 2: Conflict occurs - partners deduct points from each other
        transactionRepository.deductPoints(
            senderId = user1Id,
            receiverId = user2Id,
            points = 3,
            reason = "Left dishes in the sink",
            connectionId = connectionId
        )
        
        transactionRepository.deductPoints(
            senderId = user2Id,
            receiverId = user1Id,
            points = 4,
            reason = "Didn't take out the trash",
            connectionId = connectionId
        )
        
        // Verify points were deducted
        val afterDeductUser1Doc = firestore.collection("users").document(user1Id).get().await()
        val afterDeductUser2Doc = firestore.collection("users").document(user2Id).get().await()
        assertEquals("User 1 should have 2 points after deduction", 2L, afterDeductUser1Doc.getLong("totalPointsReceived"))
        assertEquals("User 2 should have 5 points after deduction", 5L, afterDeductUser2Doc.getLong("totalPointsReceived"))
        
        // Step 3: Conflict escalates - one partner requests timeout
        val timeoutResult = timeoutRepository.createTimeout(user1Id, connectionId)
        assertTrue("Timeout creation should succeed", timeoutResult.isSuccess)
        val timeout = timeoutResult.getOrNull()!!
        
        // Verify timeout is active
        val activeTimeoutResult = timeoutRepository.getActiveTimeout(connectionId)
        assertTrue("Getting active timeout should succeed", activeTimeoutResult.isSuccess)
        assertNotNull("Should have active timeout", activeTimeoutResult.getOrNull())
        assertEquals("Active timeout should match created timeout", timeout.id, activeTimeoutResult.getOrNull()?.id)
        
        // Step 4: Verify timeout status is observed correctly
        timeoutRepository.observeTimeoutStatus(connectionId).test {
            val isActive = awaitItem()
            assertTrue("Timeout should be active", isActive)
            
            // Step 5: Timeout expires (simulate by manually expiring)
            timeoutRepository.expireTimeout(timeout.id)
            
            val isInactive = awaitItem()
            assertFalse("Timeout should be inactive after expiration", isInactive)
            
            cancelAndIgnoreRemainingEvents()
        }
        
        // Step 6: After timeout, partners make up with positive transactions
        transactionRepository.givePoints(
            senderId = user1Id,
            receiverId = user2Id,
            points = 5,
            message = "Sorry about the argument, I love you",
            connectionId = connectionId
        )
        
        transactionRepository.givePoints(
            senderId = user2Id,
            receiverId = user1Id,
            points = 4,
            message = "I love you too, let's not fight",
            connectionId = connectionId
        )
        
        // Verify final positive balances
        val finalUser1Doc = firestore.collection("users").document(user1Id).get().await()
        val finalUser2Doc = firestore.collection("users").document(user2Id).get().await()
        assertEquals("User 1 should have 6 points finally", 6L, finalUser1Doc.getLong("totalPointsReceived"))
        assertEquals("User 2 should have 10 points finally", 10L, finalUser2Doc.getLong("totalPointsReceived"))
        
        // Step 7: Verify complete transaction history shows the full story
        val user1HistoryResult = transactionRepository.getTransactionHistory(user1Id)
        assertTrue("Getting user 1 history should succeed", user1HistoryResult.isSuccess)
        val user1History = user1HistoryResult.getOrNull()!!
        
        // Should have 6 transactions total (3 sent, 3 received)
        assertEquals("User 1 should have 6 transactions in history", 6, user1History.size)
        
        // Count transaction types
        val giveTransactions = user1History.filter { it.type == com.browniepoints.app.data.model.TransactionType.GIVE }
        val deductTransactions = user1History.filter { it.type == com.browniepoints.app.data.model.TransactionType.DEDUCT }
        
        assertEquals("Should have 4 give transactions", 4, giveTransactions.size)
        assertEquals("Should have 2 deduct transactions", 2, deductTransactions.size)
        
        // Verify the story in chronological order (most recent first)
        val sortedHistory = user1History.sortedByDescending { it.timestamp }
        
        // Most recent should be the makeup transactions
        assertTrue("Most recent should be positive", sortedHistory[0].points > 0)
        assertTrue("Second most recent should be positive", sortedHistory[1].points > 0)
        
        // Middle should be the conflict deductions
        assertTrue("Third should be negative", sortedHistory[2].points < 0)
        assertTrue("Fourth should be negative", sortedHistory[3].points < 0)
        
        // Oldest should be the initial positive transactions
        assertTrue("Fifth should be positive", sortedHistory[4].points > 0)
        assertTrue("Oldest should be positive", sortedHistory[5].points > 0)
    }
    
    @Test
    fun testTimeoutPreventsMultipleRequestsSameDay() = runTest {
        val userId = "timeout-limit-user"
        val connectionId = "timeout-limit-connection"
        
        // Create test user
        createTestUser(userId, "Timeout Limit User", "timeoutlimit@example.com", "TLU001")
        
        // First timeout should succeed
        val firstResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("First timeout should succeed", firstResult.isSuccess)
        
        // Second timeout same day should fail
        val secondResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Second timeout should fail", secondResult.isFailure)
        assertTrue("Error should mention daily limit", 
            secondResult.exceptionOrNull()?.message?.contains("already requested timeout today") == true)
        
        // Verify canRequestTimeout returns false
        val canRequestResult = timeoutRepository.canRequestTimeout(userId)
        assertTrue("Can request check should succeed", canRequestResult.isSuccess)
        assertFalse("Should not be able to request timeout", canRequestResult.getOrNull() == true)
        
        // Verify timeout count is 1
        val countResult = timeoutRepository.getTodayTimeoutCount(userId)
        assertTrue("Get count should succeed", countResult.isSuccess)
        assertEquals("Should have 1 timeout today", 1, countResult.getOrNull())
    }
    
    @Test
    fun testTimeoutHistoryTracking() = runTest {
        val userId = "history-user"
        val connectionId1 = "history-connection-1"
        val connectionId2 = "history-connection-2"
        
        // Create test user
        createTestUser(userId, "History User", "history@example.com", "HST001")
        
        // Create first timeout
        val firstResult = timeoutRepository.createTimeout(userId, connectionId1)
        assertTrue("First timeout should succeed", firstResult.isSuccess)
        val firstTimeout = firstResult.getOrNull()!!
        
        // Expire first timeout to allow creating another (simulate different day)
        timeoutRepository.expireTimeout(firstTimeout.id)
        
        // Wait a bit for different timestamps
        delay(100)
        
        // Manually create second timeout for different day
        val secondTimeoutId = "manual-history-timeout"
        val secondTimeout = mapOf(
            "id" to secondTimeoutId,
            "userId" to userId,
            "connectionId" to connectionId2,
            "startTime" to com.google.firebase.Timestamp.now(),
            "duration" to com.browniepoints.app.data.model.Timeout.DEFAULT_DURATION_MS,
            "isActive" to false,
            "createdDate" to "2024-01-16" // Different date
        )
        firestore.collection("timeouts").document(secondTimeoutId).set(secondTimeout).await()
        
        // Get timeout history
        val historyResult = timeoutRepository.getTimeoutHistory(userId)
        assertTrue("Get history should succeed", historyResult.isSuccess)
        
        val history = historyResult.getOrNull()!!
        assertEquals("Should have 2 timeouts in history", 2, history.size)
        
        // Verify both timeouts belong to user
        assertTrue("All timeouts should belong to user", history.all { it.userId == userId })
        
        // Verify different connection IDs
        val connectionIds = history.map { it.connectionId }.toSet()
        assertTrue("Should have timeouts for different connections", connectionIds.contains(connectionId1))
        assertTrue("Should have manual timeout connection", connectionIds.contains(connectionId2))
    }
    
    @Test
    fun testNegativeBalanceScenario() = runTest {
        val user1Id = "negative-user-1"
        val user2Id = "negative-user-2"
        val connectionId = "negative-connection"
        
        // Create couple users
        createTestUser(user1Id, "Negative User 1", "negative1@example.com", "NEG001")
        createTestUser(user2Id, "Negative User 2", "negative2@example.com", "NEG002")
        
        // Start with small positive balance
        transactionRepository.givePoints(
            senderId = user1Id,
            receiverId = user2Id,
            points = 3,
            message = "Small initial points",
            connectionId = connectionId
        )
        
        // Verify initial balance
        val initialDoc = firestore.collection("users").document(user2Id).get().await()
        assertEquals("Should have 3 initial points", 3L, initialDoc.getLong("totalPointsReceived"))
        
        // Major conflict - deduct more points than available
        transactionRepository.deductPoints(
            senderId = user1Id,
            receiverId = user2Id,
            points = 8,
            reason = "Major argument about finances",
            connectionId = connectionId
        )
        
        // Verify negative balance is allowed
        val negativeDoc = firestore.collection("users").document(user2Id).get().await()
        assertEquals("Should have negative balance", -5L, negativeDoc.getLong("totalPointsReceived"))
        
        // Recovery - give points to get back to positive
        transactionRepository.givePoints(
            senderId = user1Id,
            receiverId = user2Id,
            points = 10,
            message = "Making up after big fight",
            connectionId = connectionId
        )
        
        // Verify recovery to positive balance
        val recoveryDoc = firestore.collection("users").document(user2Id).get().await()
        assertEquals("Should recover to positive balance", 5L, recoveryDoc.getLong("totalPointsReceived"))
        
        // Verify transaction history shows the journey
        val historyResult = transactionRepository.getTransactionHistory(user2Id)
        assertTrue("Get history should succeed", historyResult.isSuccess)
        
        val history = historyResult.getOrNull()!!
        assertEquals("Should have 3 transactions", 3, history.size)
        
        // Find the deduction transaction
        val deductTransaction = history.find { it.points < 0 }
        assertNotNull("Should have deduction transaction", deductTransaction)
        assertEquals("Deduction should be -8 points", -8, deductTransaction?.points)
        assertEquals("Deduction reason should match", "Major argument about finances", deductTransaction?.message)
    }
    
    @Test
    fun testRealTimeUpdatesForCoupleFeatures() = runTest {
        val user1Id = "realtime-user-1"
        val user2Id = "realtime-user-2"
        val connectionId = "realtime-connection"
        
        // Create couple users
        createTestUser(user1Id, "Realtime User 1", "realtime1@example.com", "RT001")
        createTestUser(user2Id, "Realtime User 2", "realtime2@example.com", "RT002")
        
        // Start observing transactions for user 1
        transactionRepository.observeTransactions(user1Id).test {
            // Initially empty
            val initial = awaitItem()
            assertTrue("Initially should be empty", initial.isEmpty())
            
            // Partner gives points
            transactionRepository.givePoints(
                senderId = user2Id,
                receiverId = user1Id,
                points = 5,
                message = "Love you!",
                connectionId = connectionId
            )
            
            // Should receive the give transaction
            val afterGive = awaitItem()
            assertEquals("Should have 1 transaction", 1, afterGive.size)
            assertEquals("Should be give transaction", com.browniepoints.app.data.model.TransactionType.GIVE, afterGive[0].type)
            
            // Partner deducts points
            transactionRepository.deductPoints(
                senderId = user2Id,
                receiverId = user1Id,
                points = 2,
                reason = "Forgot to call",
                connectionId = connectionId
            )
            
            // Should receive both transactions
            val afterDeduct = awaitItem()
            assertEquals("Should have 2 transactions", 2, afterDeduct.size)
            
            val deductTransaction = afterDeduct.find { it.type == com.browniepoints.app.data.model.TransactionType.DEDUCT }
            assertNotNull("Should have deduct transaction", deductTransaction)
            assertEquals("Deduct should be -2 points", -2, deductTransaction?.points)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}