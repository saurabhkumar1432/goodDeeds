package com.browniepoints.app.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.browniepoints.app.BaseFirebaseTest
import com.browniepoints.app.data.repository.TimeoutRepository
import com.google.firebase.Timestamp
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
 * Integration tests for TimeoutRepository with Firestore operations
 * 
 * These tests verify:
 * - Timeout creation and validation
 * - Daily timeout limit enforcement
 * - Active timeout queries and real-time observation
 * - Timeout expiration handling
 * - Timeout history tracking
 * 
 * Requirements tested: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.9
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TimeoutRepositoryIntegrationTest : BaseFirebaseTest() {
    
    @Inject
    lateinit var timeoutRepository: TimeoutRepository
    
    @Test
    fun testCreateTimeout() = runTest {
        val userId = "timeout-user-1"
        val connectionId = "timeout-connection-1"
        
        // Create test user
        createTestUser(userId, "Timeout User 1", "timeout1@example.com", "TMO001")
        
        // Create timeout
        val result = timeoutRepository.createTimeout(userId, connectionId)
        
        assertTrue("Timeout creation should succeed", result.isSuccess)
        
        val timeout = result.getOrNull()
        assertNotNull("Timeout should not be null", timeout)
        assertEquals("User ID should match", userId, timeout?.userId)
        assertEquals("Connection ID should match", connectionId, timeout?.connectionId)
        assertTrue("Timeout should be active", timeout?.isActive == true)
        assertEquals("Duration should be default", com.browniepoints.app.data.model.Timeout.DEFAULT_DURATION_MS, timeout?.duration)
        assertNotNull("Timeout should have an ID", timeout?.id)
        assertNotNull("Timeout should have a start time", timeout?.startTime)
        
        // Verify timeout was saved to Firestore
        val timeoutDoc = firestore.collection("timeouts").document(timeout!!.id).get().await()
        assertTrue("Timeout document should exist in Firestore", timeoutDoc.exists())
        assertEquals("Firestore timeout user ID should match", userId, timeoutDoc.getString("userId"))
        assertEquals("Firestore timeout connection ID should match", connectionId, timeoutDoc.getString("connectionId"))
        assertTrue("Firestore timeout should be active", timeoutDoc.getBoolean("isActive") == true)
    }
    
    @Test
    fun testCanRequestTimeoutInitially() = runTest {
        val userId = "timeout-user-2"
        
        // Create test user
        createTestUser(userId, "Timeout User 2", "timeout2@example.com", "TMO002")
        
        // User should be able to request timeout initially
        val result = timeoutRepository.canRequestTimeout(userId)
        
        assertTrue("Can request timeout check should succeed", result.isSuccess)
        assertTrue("User should be able to request timeout initially", result.getOrNull() == true)
    }
    
    @Test
    fun testCannotRequestTimeoutAfterDailyLimit() = runTest {
        val userId = "timeout-user-3"
        val connectionId = "timeout-connection-3"
        
        // Create test user
        createTestUser(userId, "Timeout User 3", "timeout3@example.com", "TMO003")
        
        // Create first timeout (should succeed)
        val firstResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("First timeout creation should succeed", firstResult.isSuccess)
        
        // Try to create second timeout (should fail due to daily limit)
        val secondResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Second timeout creation should fail", secondResult.isFailure)
        assertTrue("Error should mention daily limit", 
            secondResult.exceptionOrNull()?.message?.contains("already requested timeout today") == true)
        
        // Verify canRequestTimeout returns false
        val canRequestResult = timeoutRepository.canRequestTimeout(userId)
        assertTrue("Can request timeout check should succeed", canRequestResult.isSuccess)
        assertFalse("User should not be able to request timeout after daily limit", canRequestResult.getOrNull() == true)
    }
    
    @Test
    fun testGetActiveTimeout() = runTest {
        val userId = "timeout-user-4"
        val connectionId = "timeout-connection-4"
        
        // Create test user
        createTestUser(userId, "Timeout User 4", "timeout4@example.com", "TMO004")
        
        // Initially no active timeout
        val initialResult = timeoutRepository.getActiveTimeout(connectionId)
        assertTrue("Get active timeout should succeed", initialResult.isSuccess)
        assertNull("Initially should have no active timeout", initialResult.getOrNull())
        
        // Create timeout
        val createResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Timeout creation should succeed", createResult.isSuccess)
        val createdTimeout = createResult.getOrNull()!!
        
        // Should now have active timeout
        val activeResult = timeoutRepository.getActiveTimeout(connectionId)
        assertTrue("Get active timeout should succeed", activeResult.isSuccess)
        val activeTimeout = activeResult.getOrNull()
        assertNotNull("Should have active timeout", activeTimeout)
        assertEquals("Active timeout ID should match", createdTimeout.id, activeTimeout?.id)
        assertEquals("Active timeout user ID should match", userId, activeTimeout?.userId)
        assertEquals("Active timeout connection ID should match", connectionId, activeTimeout?.connectionId)
        assertTrue("Active timeout should be active", activeTimeout?.isActive == true)
    }
    
    @Test
    fun testObserveActiveTimeout() = runTest {
        val userId = "timeout-user-5"
        val connectionId = "timeout-connection-5"
        
        // Create test user
        createTestUser(userId, "Timeout User 5", "timeout5@example.com", "TMO005")
        
        // Start observing active timeout
        timeoutRepository.observeActiveTimeout(connectionId).test {
            // Initially should be null
            val initialTimeout = awaitItem()
            assertNull("Initially should have no active timeout", initialTimeout)
            
            // Create timeout
            val createResult = timeoutRepository.createTimeout(userId, connectionId)
            assertTrue("Timeout creation should succeed", createResult.isSuccess)
            val createdTimeout = createResult.getOrNull()!!
            
            // Should receive the new timeout
            val activeTimeout = awaitItem()
            assertNotNull("Should receive active timeout", activeTimeout)
            assertEquals("Active timeout ID should match", createdTimeout.id, activeTimeout?.id)
            assertEquals("Active timeout user ID should match", userId, activeTimeout?.userId)
            assertTrue("Active timeout should be active", activeTimeout?.isActive == true)
            
            // Expire the timeout
            timeoutRepository.expireTimeout(createdTimeout.id)
            
            // Should receive null (no active timeout)
            val expiredTimeout = awaitItem()
            assertNull("Should receive null after timeout expiration", expiredTimeout)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun testExpireTimeout() = runTest {
        val userId = "timeout-user-6"
        val connectionId = "timeout-connection-6"
        
        // Create test user
        createTestUser(userId, "Timeout User 6", "timeout6@example.com", "TMO006")
        
        // Create timeout
        val createResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Timeout creation should succeed", createResult.isSuccess)
        val createdTimeout = createResult.getOrNull()!!
        
        // Verify timeout is initially active
        val initialDoc = firestore.collection("timeouts").document(createdTimeout.id).get().await()
        assertTrue("Timeout should initially be active", initialDoc.getBoolean("isActive") == true)
        
        // Expire timeout
        val expireResult = timeoutRepository.expireTimeout(createdTimeout.id)
        assertTrue("Timeout expiration should succeed", expireResult.isSuccess)
        
        // Verify timeout is no longer active
        val expiredDoc = firestore.collection("timeouts").document(createdTimeout.id).get().await()
        assertFalse("Timeout should no longer be active", expiredDoc.getBoolean("isActive") == true)
        
        // Verify getActiveTimeout returns null
        val activeResult = timeoutRepository.getActiveTimeout(connectionId)
        assertTrue("Get active timeout should succeed", activeResult.isSuccess)
        assertNull("Should have no active timeout after expiration", activeResult.getOrNull())
    }
    
    @Test
    fun testGetTimeoutHistory() = runTest {
        val userId = "timeout-user-7"
        val connectionId1 = "timeout-connection-7a"
        val connectionId2 = "timeout-connection-7b"
        
        // Create test user
        createTestUser(userId, "Timeout User 7", "timeout7@example.com", "TMO007")
        
        // Initially should have empty history
        val initialResult = timeoutRepository.getTimeoutHistory(userId)
        assertTrue("Get timeout history should succeed", initialResult.isSuccess)
        assertTrue("Initial history should be empty", initialResult.getOrNull()?.isEmpty() == true)
        
        // Create first timeout
        val firstResult = timeoutRepository.createTimeout(userId, connectionId1)
        assertTrue("First timeout creation should succeed", firstResult.isSuccess)
        val firstTimeout = firstResult.getOrNull()!!
        
        // Expire first timeout to allow creating second one
        timeoutRepository.expireTimeout(firstTimeout.id)
        
        // Wait a bit to ensure different timestamps
        delay(100)
        
        // Manually create second timeout for different day (simulate next day)
        val secondTimeoutId = "manual-timeout-2"
        val secondTimeout = mapOf(
            "id" to secondTimeoutId,
            "userId" to userId,
            "connectionId" to connectionId2,
            "startTime" to Timestamp.now(),
            "duration" to com.browniepoints.app.data.model.Timeout.DEFAULT_DURATION_MS,
            "isActive" to false,
            "createdDate" to "2024-01-16" // Different date
        )
        firestore.collection("timeouts").document(secondTimeoutId).set(secondTimeout).await()
        
        // Get timeout history
        val historyResult = timeoutRepository.getTimeoutHistory(userId)
        assertTrue("Get timeout history should succeed", historyResult.isSuccess)
        
        val history = historyResult.getOrNull()
        assertNotNull("History should not be null", history)
        assertEquals("Should have 2 timeouts in history", 2, history?.size)
        
        // Verify timeouts are sorted by start time (most recent first)
        val sortedHistory = history?.sortedByDescending { it.startTime }
        assertEquals("History should be sorted by start time", sortedHistory, history)
        
        // Verify both timeouts belong to the user
        assertTrue("All timeouts should belong to the user", 
            history?.all { it.userId == userId } == true)
    }
    
    @Test
    fun testGetTodayTimeoutCount() = runTest {
        val userId = "timeout-user-8"
        val connectionId = "timeout-connection-8"
        
        // Create test user
        createTestUser(userId, "Timeout User 8", "timeout8@example.com", "TMO008")
        
        // Initially should have 0 timeouts today
        val initialResult = timeoutRepository.getTodayTimeoutCount(userId)
        assertTrue("Get today timeout count should succeed", initialResult.isSuccess)
        assertEquals("Initial count should be 0", 0, initialResult.getOrNull())
        
        // Create timeout
        val createResult = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Timeout creation should succeed", createResult.isSuccess)
        
        // Should now have 1 timeout today
        val updatedResult = timeoutRepository.getTodayTimeoutCount(userId)
        assertTrue("Get today timeout count should succeed", updatedResult.isSuccess)
        assertEquals("Count should be 1 after creating timeout", 1, updatedResult.getOrNull())
    }
    
    @Test
    fun testObserveTimeoutStatus() = runTest {
        val userId = "timeout-user-9"
        val connectionId = "timeout-connection-9"
        
        // Create test user
        createTestUser(userId, "Timeout User 9", "timeout9@example.com", "TMO009")
        
        // Start observing timeout status
        timeoutRepository.observeTimeoutStatus(connectionId).test {
            // Initially should be false (no active timeout)
            val initialStatus = awaitItem()
            assertFalse("Initially should have no active timeout", initialStatus)
            
            // Create timeout
            val createResult = timeoutRepository.createTimeout(userId, connectionId)
            assertTrue("Timeout creation should succeed", createResult.isSuccess)
            val createdTimeout = createResult.getOrNull()!!
            
            // Should receive true (active timeout)
            val activeStatus = awaitItem()
            assertTrue("Should have active timeout", activeStatus)
            
            // Expire the timeout
            timeoutRepository.expireTimeout(createdTimeout.id)
            
            // Should receive false (no active timeout)
            val expiredStatus = awaitItem()
            assertFalse("Should have no active timeout after expiration", expiredStatus)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun testCleanupExpiredTimeouts() = runTest {
        val userId1 = "timeout-user-10"
        val userId2 = "timeout-user-11"
        val connectionId1 = "timeout-connection-10"
        val connectionId2 = "timeout-connection-11"
        
        // Create test users
        createTestUser(userId1, "Timeout User 10", "timeout10@example.com", "TMO010")
        createTestUser(userId2, "Timeout User 11", "timeout11@example.com", "TMO011")
        
        // Create expired timeout (manually set old start time)
        val expiredTimeoutId = "expired-timeout-1"
        val expiredTimeout = mapOf(
            "id" to expiredTimeoutId,
            "userId" to userId1,
            "connectionId" to connectionId1,
            "startTime" to Timestamp(Date(System.currentTimeMillis() - (35 * 60 * 1000))), // 35 minutes ago
            "duration" to com.browniepoints.app.data.model.Timeout.DEFAULT_DURATION_MS,
            "isActive" to true, // Still marked as active but should be expired
            "createdDate" to getCurrentDateString()
        )
        firestore.collection("timeouts").document(expiredTimeoutId).set(expiredTimeout).await()
        
        // Create active timeout
        val activeResult = timeoutRepository.createTimeout(userId2, connectionId2)
        assertTrue("Active timeout creation should succeed", activeResult.isSuccess)
        val activeTimeout = activeResult.getOrNull()!!
        
        // Verify initial state
        val expiredDoc = firestore.collection("timeouts").document(expiredTimeoutId).get().await()
        assertTrue("Expired timeout should initially be marked as active", expiredDoc.getBoolean("isActive") == true)
        
        val activeDoc = firestore.collection("timeouts").document(activeTimeout.id).get().await()
        assertTrue("Active timeout should be marked as active", activeDoc.getBoolean("isActive") == true)
        
        // Run cleanup
        val cleanupResult = timeoutRepository.cleanupExpiredTimeouts()
        assertTrue("Cleanup should succeed", cleanupResult.isSuccess)
        
        // Verify expired timeout was marked as inactive
        val cleanedExpiredDoc = firestore.collection("timeouts").document(expiredTimeoutId).get().await()
        assertFalse("Expired timeout should be marked as inactive after cleanup", cleanedExpiredDoc.getBoolean("isActive") == true)
        
        // Verify active timeout remains active
        val stillActiveDoc = firestore.collection("timeouts").document(activeTimeout.id).get().await()
        assertTrue("Active timeout should still be marked as active after cleanup", stillActiveDoc.getBoolean("isActive") == true)
    }
    
    @Test
    fun testTimeoutValidation() = runTest {
        val userId = "timeout-user-12"
        val connectionId = "timeout-connection-12"
        
        // Create test user
        createTestUser(userId, "Timeout User 12", "timeout12@example.com", "TMO012")
        
        // Create timeout
        val result = timeoutRepository.createTimeout(userId, connectionId)
        assertTrue("Timeout creation should succeed", result.isSuccess)
        
        val timeout = result.getOrNull()!!
        
        // Verify timeout data is valid
        assertFalse("Timeout ID should not be empty", timeout.id.isEmpty())
        assertFalse("User ID should not be empty", timeout.userId.isEmpty())
        assertFalse("Connection ID should not be empty", timeout.connectionId.isEmpty())
        assertTrue("Duration should be positive", timeout.duration > 0)
        assertFalse("Created date should not be empty", timeout.createdDate.isEmpty())
        
        // Verify created date format (YYYY-MM-DD)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        try {
            dateFormat.isLenient = false
            dateFormat.parse(timeout.createdDate)
            // If we get here, the date format is valid
        } catch (e: Exception) {
            fail("Created date should be in YYYY-MM-DD format: ${timeout.createdDate}")
        }
    }
    
    private fun getCurrentDateString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}