package com.browniepoints.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.browniepoints.app.data.repository.TimeoutRepository
import com.browniepoints.app.data.repository.TransactionRepository
import com.browniepoints.app.data.repository.UserRepository
import com.browniepoints.app.data.model.Timeout
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * End-to-end integration tests for timeout system functionality
 * 
 * These tests verify the complete workflow of:
 * - Timeout request validation and daily limits
 * - Timeout countdown and automatic expiration
 * - Transaction disabling during active timeouts
 * 
 * Requirements tested: 8.1, 8.2, 8.4, 8.5, 8.6, 8.7, 8.9, 8.10
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TimeoutEndToEndTest : BaseFirebaseTest() {
    
    @Inject
    lateinit var timeoutRepository: TimeoutRepository
    
    @Inject
    lateinit var transactionRepository: TransactionRepository
    
    @Inject
    lateinit var userRepository: UserRepository
    
    @Test
    fun testCompleteTimeoutRequestFlow() = runTest {
        // Requirements 8.1, 8.4: Timeout request and 30-minute duration
        
        val userId = "timeout-user"
        val connectionId = "timeout-connection"
        
        // Create test user
        createTestUser(userId, "Timeout User", "timeout@example.com", "TOUT01")
        
        // Initially should be able to request timeout
        val canRequestResult = timeoutRepository.canRequestTimeout(userId)
        assertTrue("Can request check should succeed", canRequestResult.isSuccess)
        assertTrue("Should be able to request timeout initially", canRequestResult.getOrNull() == true)
        
        // Request timeout
        val timeoutResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Timeout creation should succeed", timeoutResult.isSuccess)
        val timeout = timeoutResult.getOrNull()!!
        
        // Verify timeout details
        assertEquals("User ID should match", userId, timeout.userId)
        assertEquals("Connection ID should match", connectionId, timeout.connectionId)
        assertEquals("Duration should be 30 minutes", Timeout.DEFAULT_DURATION_MS, timeout.duration)
        assertTrue("Timeout should be active", timeout.isActive)
        assertNotNull("Should have start time", timeout.startTime)
        assertNotNull("Should have timeout ID", timeout.id)
        
        // Verify timeout was persisted in Firestore
        val firestoreTimeout = firestore.collection("timeouts")
            .document(timeout.id)
            .get()
            .await()
        
        assertTrue("Timeout should exist in Firestore", firestoreTimeout.exists())
        assertEquals("Firestore user ID should match", userId, firestoreTimeout.getString("userId"))
        assertEquals("Firestore connection ID should match", connectionId, firestoreTimeout.getString("connectionId"))
        assertTrue("Firestore timeout should be active", firestoreTimeout.getBoolean("isActive") == true)
        assertEquals("Firestore duration should match", Timeout.DEFAULT_DURATION_MS, firestoreTimeout.getLong("duration"))
        
        // Verify active timeout can be retrieved
        val activeTimeoutResult = timeoutRepository.getActiveTimeout(connectionId)
        assertTrue("Getting active timeout should succeed", activeTimeoutResult.isSuccess)
        val activeTimeout = activeTimeoutResult.getOrNull()!!
        assertEquals("Active timeout should match created timeout", timeout.id, activeTimeout.id)
    }
    
    @Test
    fun testDailyTimeoutLimitValidation() = runTest {
        // Requirements 8.1, 8.2: Once per day limit validation
        
        val userId = "daily-limit-user"
        val connectionId = "daily-limit-connection"
        
        // Create test user
        createTestUser(userId, "Daily Limit User", "dailylimit@example.com", "DAILY1")
        
        // First timeout should succeed
        val firstResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("First timeout should succeed", firstResult.isSuccess)
        val firstTimeout = firstResult.getOrNull()!!
        
        // Verify daily count is now 1
        val countResult = timeoutRepository.getTodayTimeoutCount(userId)
        assertTrue("Get count should succeed", countResult.isSuccess)
        assertEquals("Should have 1 timeout today", 1, countResult.getOrNull())
        
        // Second timeout same day should fail
        val secondResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Second timeout should fail", secondResult.isFailure)
        assertTrue("Error should mention daily limit", 
            secondResult.exceptionOrNull()?.message?.contains("already requested timeout today") == true ||
            secondResult.exceptionOrNull()?.message?.contains("daily limit") == true ||
            secondResult.exceptionOrNull()?.message?.contains("once per day") == true)
        
        // Can request check should return false
        val canRequestAfterLimit = timeoutRepository.canRequestTimeout(userId)
        assertTrue("Can request check should succeed", canRequestAfterLimit.isSuccess)
        assertFalse("Should not be able to request timeout after daily limit", canRequestAfterLimit.getOrNull() == true)
        
        // Verify count remains 1 (second request didn't create timeout)
        val finalCountResult = timeoutRepository.getTodayTimeoutCount(userId)
        assertTrue("Final count check should succeed", finalCountResult.isSuccess)
        assertEquals("Should still have only 1 timeout today", 1, finalCountResult.getOrNull())
    }
    
    @Test
    fun testTimeoutCountdownAndExpiration() = runTest {
        // Requirements 8.4, 8.6: Timeout countdown and automatic expiration
        
        val userId = "countdown-user"
        val connectionId = "countdown-connection"
        
        // Create test user
        createTestUser(userId, "Countdown User", "countdown@example.com", "COUNT1")
        
        // Create timeout
        val timeoutResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Timeout creation should succeed", timeoutResult.isSuccess)
        val timeout = timeoutResult.getOrNull()!!
        
        // Observe timeout status
        timeoutRepository.observeTimeoutStatus(connectionId).test {
            // Should initially be active
            val initialStatus = awaitItem()
            assertTrue("Timeout should initially be active", initialStatus)
            
            // Manually expire the timeout (simulating 30 minutes passing)
            val expireResult = timeoutRepository.expireTimeout(timeout.id)
            assertTrue("Timeout expiration should succeed", expireResult.isSuccess)
            
            // Should receive status change to inactive
            val expiredStatus = awaitItem()
            assertFalse("Timeout should be inactive after expiration", expiredStatus)
            
            cancelAndIgnoreRemainingEvents()
        }
        
        // Verify timeout is no longer active
        val activeTimeoutAfterExpiry = timeoutRepository.getActiveTimeout(connectionId)
        assertTrue("Getting active timeout should succeed", activeTimeoutAfterExpiry.isSuccess)
        assertNull("Should not have active timeout after expiration", activeTimeoutAfterExpiry.getOrNull())
        
        // Verify timeout document was updated in Firestore
        val expiredTimeoutDoc = firestore.collection("timeouts")
            .document(timeout.id)
            .get()
            .await()
        
        assertTrue("Expired timeout should still exist in Firestore", expiredTimeoutDoc.exists())
        assertFalse("Expired timeout should not be active", expiredTimeoutDoc.getBoolean("isActive") == true)
    }
    
    @Test
    fun testTransactionDisablingDuringTimeout() = runTest {
        // Requirements 8.5, 8.7: Disable transactions during active timeout
        
        val user1Id = "disable-user-1"
        val user2Id = "disable-user-2"
        val connectionId = "disable-connection"
        
        // Create users
        createTestUser(user1Id, "Disable User 1", "disable1@example.com", "DIS001")
        createTestUser(user2Id, "Disable User 2", "disable2@example.com", "DIS002")
        
        // Initially, transactions should be allowed
        val initialGiveResult = transactionRepository.givePoints(
            senderId = user1Id,
            receiverId = user2Id,
            points = 5,
            message = "Before timeout",
            connectionId = connectionId
        )
        assertTrue("Transaction should succeed before timeout", initialGiveResult.isSuccess)
        
        // Create timeout
        val timeoutResult = timeoutRepository.createTimeout(user1Id, connectionId)
        assertTrue("Timeout creation should succeed", timeoutResult.isSuccess)
        val timeout = timeoutResult.getOrNull()!!
        
        // During timeout, transactions should be disabled
        val duringTimeoutGiveResult = transactionRepository.givePoints(
            senderId = user1Id,
            receiverId = user2Id,
            points = 3,
            message = "During timeout",
            connectionId = connectionId
        )
        assertTrue("Give points should fail during timeout", duringTimeoutGiveResult.isFailure)
        assertTrue("Error should mention timeout", 
            duringTimeoutGiveResult.exceptionOrNull()?.message?.contains("timeout") == true ||
            duringTimeoutGiveResult.exceptionOrNull()?.message?.contains("disabled") == true)
        
        val duringTimeoutDeductResult = transactionRepository.deductPoints(
            senderId = user2Id,
            receiverId = user1Id,
            points = 2,
            reason = "During timeout",
            connectionId = connectionId
        )
        assertTrue("Deduct points should fail during timeout", duringTimeoutDeductResult.isFailure)
        
        // Expire timeout
        val expireResult = timeoutRepository.expireTimeout(timeout.id)
        assertTrue("Timeout expiration should succeed", expireResult.isSuccess)
        
        // After timeout, transactions should be re-enabled
        val afterTimeoutGiveResult = transactionRepository.givePoints(
            senderId = user2Id,
            receiverId = user1Id,
            points = 4,
            message = "After timeout",
            connectionId = connectionId
        )
        assertTrue("Transaction should succeed after timeout", afterTimeoutGiveResult.isSuccess)
        
        // Verify only 2 transactions were created (before and after timeout)
        val historyResult = transactionRepository.getTransactionHistory(user1Id)
        assertTrue("Getting history should succeed", historyResult.isSuccess)
        val history = historyResult.getOrNull()!!
        
        assertEquals("Should have 2 transactions (before and after timeout)", 2, history.size)
        
        val beforeTimeoutTx = history.find { it.message == "Before timeout" }
        val afterTimeoutTx = history.find { it.message == "After timeout" }
        
        assertNotNull("Should have before timeout transaction", beforeTimeoutTx)
        assertNotNull("Should have after timeout transaction", afterTimeoutTx)
        assertNull("Should not have during timeout transaction", history.find { it.message == "During timeout" })
    }
    
    @Test
    fun testTimeoutStatusSynchronizationBetweenPartners() = runTest {
        // Requirements 8.7, 8.10: Timeout status shared between partners
        
        val user1Id = "sync-user-1"
        val user2Id = "sync-user-2"
        val connectionId = "sync-connection"
        
        // Create users
        createTestUser(user1Id, "Sync User 1", "sync1@example.com", "SYNC01")
        createTestUser(user2Id, "Sync User 2", "sync2@example.com", "SYNC02")
        
        // Both users should observe the same timeout status
        timeoutRepository.observeTimeoutStatus(connectionId).test {
            // Initially no timeout
            val initialStatus = awaitItem()
            assertFalse("Should initially have no timeout", initialStatus)
            
            // User 1 requests timeout
            val timeoutResult = timeoutRepository.createTimeout(user1Id, connectionId)
            assertTrue("Timeout creation should succeed", timeoutResult.isSuccess)
            
            // Both users should see timeout is active
            val activeStatus = awaitItem()
            assertTrue("Both users should see timeout is active", activeStatus)
            
            // Verify User 2 also sees the timeout
            val user2TimeoutCheck = timeoutRepository.getActiveTimeout(connectionId)
            assertTrue("User 2 should see active timeout", user2TimeoutCheck.isSuccess)
            assertNotNull("User 2 should have active timeout", user2TimeoutCheck.getOrNull())
            
            // Expire timeout
            val timeout = timeoutResult.getOrNull()!!
            timeoutRepository.expireTimeout(timeout.id)
            
            // Both users should see timeout is inactive
            val inactiveStatus = awaitItem()
            assertFalse("Both users should see timeout is inactive", inactiveStatus)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun testTimeoutHistoryAndTracking() = runTest {
        // Test timeout history tracking and retrieval
        
        val userId = "history-timeout-user"
        val connectionId1 = "history-connection-1"
        val connectionId2 = "history-connection-2"
        
        // Create test user
        createTestUser(userId, "History Timeout User", "histimeout@example.com", "HIST01")
        
        // Create first timeout
        val timeout1Result = timeoutRepository.createTimeout(userId, connectionId1)
        assertTrue("First timeout should succeed", timeout1Result.isSuccess)
        val timeout1 = timeout1Result.getOrNull()!!
        
        // Expire first timeout
        timeoutRepository.expireTimeout(timeout1.id)
        
        // Wait a bit for different timestamps
        delay(100)
        
        // Manually create second timeout for different day (to test history across days)
        val secondTimeoutId = "manual-history-timeout-2"
        val secondTimeout = mapOf(
            "id" to secondTimeoutId,
            "userId" to userId,
            "connectionId" to connectionId2,
            "startTime" to com.google.firebase.Timestamp.now(),
            "duration" to Timeout.DEFAULT_DURATION_MS,
            "isActive" to false,
            "createdDate" to "2024-01-15" // Different date
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
        assertTrue("Should have timeouts for different connections", connectionIds.size == 2)
        assertTrue("Should include first connection", connectionIds.contains(connectionId1))
        assertTrue("Should include second connection", connectionIds.contains(connectionId2))
        
        // Verify timeouts are ordered by most recent first
        val sortedHistory = history.sortedByDescending { it.startTime }
        assertEquals("History should match sorted order", sortedHistory, history)
    }
    
    @Test
    fun testDailyTimeoutResetAtMidnight() = runTest {
        // Requirements 8.9: Daily timeout allowance resets at midnight
        
        val userId = "reset-user"
        val connectionId = "reset-connection"
        
        // Create test user
        createTestUser(userId, "Reset User", "reset@example.com", "RESET1")
        
        // Create timeout for today
        val todayResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Today's timeout should succeed", todayResult.isSuccess)
        
        // Verify cannot create another timeout today
        val secondTodayResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Second timeout today should fail", secondTodayResult.isFailure)
        
        // Simulate next day by manually updating the timeout date
        val timeout = todayResult.getOrNull()!!
        val yesterdayDate = "2024-01-14" // Simulate yesterday
        
        firestore.collection("timeouts")
            .document(timeout.id)
            .update("createdDate", yesterdayDate)
            .await()
        
        // Now should be able to create timeout (simulating new day)
        val nextDayResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Next day timeout should succeed", nextDayResult.isSuccess)
        
        // Verify we now have 2 timeouts in history (different days)
        val historyResult = timeoutRepository.getTimeoutHistory(userId)
        assertTrue("Get history should succeed", historyResult.isSuccess)
        val history = historyResult.getOrNull()!!
        
        assertEquals("Should have 2 timeouts in history", 2, history.size)
        
        // Verify different dates
        val dates = history.map { it.createdDate }.toSet()
        assertEquals("Should have timeouts for 2 different dates", 2, dates.size)
    }
    
    @Test
    fun testTimeoutNotificationAndPartnerAwareness() = runTest {
        // Requirements 8.7: Timeout notifications to partner
        
        val user1Id = "notification-user-1"
        val user2Id = "notification-user-2"
        val connectionId = "notification-connection"
        
        // Create users
        createTestUser(user1Id, "Notification User 1", "notif1@example.com", "NOTIF1")
        createTestUser(user2Id, "Notification User 2", "notif2@example.com", "NOTIF2")
        
        // User 1 requests timeout
        val timeoutResult = timeoutRepository.createTimeout(user1Id, connectionId)
        assertTrue("Timeout creation should succeed", timeoutResult.isSuccess)
        val timeout = timeoutResult.getOrNull()!!
        
        // Verify timeout information is available for notifications
        assertNotNull("Timeout should have ID for notification", timeout.id)
        assertEquals("Timeout should have requesting user", user1Id, timeout.userId)
        assertEquals("Timeout should have connection ID", connectionId, timeout.connectionId)
        assertTrue("Timeout should be active for notification", timeout.isActive)
        
        // Verify timeout can be retrieved by partner (User 2)
        val partnerTimeoutResult = timeoutRepository.getActiveTimeout(connectionId)
        assertTrue("Partner should be able to get timeout", partnerTimeoutResult.isSuccess)
        val partnerTimeout = partnerTimeoutResult.getOrNull()!!
        
        assertEquals("Partner should see same timeout", timeout.id, partnerTimeout.id)
        assertEquals("Partner should see requesting user", user1Id, partnerTimeout.userId)
        
        // Test timeout expiration notification
        val expireResult = timeoutRepository.expireTimeout(timeout.id)
        assertTrue("Timeout expiration should succeed", expireResult.isSuccess)
        
        // Verify expiration is visible to both users
        val expiredTimeoutResult = timeoutRepository.getActiveTimeout(connectionId)
        assertTrue("Getting expired timeout should succeed", expiredTimeoutResult.isSuccess)
        assertNull("Should not have active timeout after expiration", expiredTimeoutResult.getOrNull())
    }
    
    @Test
    fun testTimeoutEdgeCasesAndErrorHandling() = runTest {
        // Test various edge cases and error conditions
        
        val userId = "edge-case-user"
        val connectionId = "edge-case-connection"
        val nonExistentUserId = "non-existent-user"
        val nonExistentConnectionId = "non-existent-connection"
        
        // Create test user
        createTestUser(userId, "Edge Case User", "edgecase@example.com", "EDGE01")
        
        // Test 1: Cannot create timeout for non-existent user
        val nonExistentUserResult = timeoutRepository.createTimeout(nonExistentUserId, connectionId)
        assertTrue("Timeout for non-existent user should fail", nonExistentUserResult.isFailure)
        
        // Test 2: Can request timeout check for non-existent user (should return false)
        val canRequestNonExistent = timeoutRepository.canRequestTimeout(nonExistentUserId)
        assertTrue("Can request check should succeed even for non-existent user", canRequestNonExistent.isSuccess)
        assertFalse("Non-existent user should not be able to request timeout", canRequestNonExistent.getOrNull() == true)
        
        // Test 3: Getting active timeout for non-existent connection should return null
        val nonExistentConnectionResult = timeoutRepository.getActiveTimeout(nonExistentConnectionId)
        assertTrue("Getting timeout for non-existent connection should succeed", nonExistentConnectionResult.isSuccess)
        assertNull("Non-existent connection should not have timeout", nonExistentConnectionResult.getOrNull())
        
        // Test 4: Expiring non-existent timeout should fail gracefully
        val expireNonExistentResult = timeoutRepository.expireTimeout("non-existent-timeout-id")
        assertTrue("Expiring non-existent timeout should fail", expireNonExistentResult.isFailure)
        
        // Test 5: Multiple timeout requests in rapid succession
        val rapidResult1 = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("First rapid timeout should succeed", rapidResult1.isSuccess)
        
        val rapidResult2 = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Second rapid timeout should fail", rapidResult2.isFailure)
        
        val rapidResult3 = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Third rapid timeout should fail", rapidResult3.isFailure)
        
        // Verify only one timeout was created
        val countResult = timeoutRepository.getTodayTimeoutCount(userId)
        assertTrue("Get count should succeed", countResult.isSuccess)
        assertEquals("Should have only 1 timeout despite multiple requests", 1, countResult.getOrNull())
    }
}