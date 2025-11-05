package com.browniepoints.app.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.browniepoints.app.BaseFirebaseTest
import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.repository.UserRepository
import com.google.firebase.Timestamp
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Integration tests for UserRepository with Firestore operations
 * 
 * These tests verify:
 * - User CRUD operations in Firestore
 * - Matching code generation and validation
 * - Real-time user data observation
 * 
 * Requirements tested: 1.2, 1.3, 2.2, 2.3
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UserRepositoryIntegrationTest : BaseFirebaseTest() {
    
    @Inject
    lateinit var userRepository: UserRepository
    
    @Test
    fun testCreateAndGetUser() = runTest {
        // Create a test user
        val testUser = User(
            uid = "test-user-1",
            displayName = "Test User",
            email = "test@example.com",
            matchingCode = "ABC123",
            totalPointsReceived = 0,
            createdAt = Timestamp.now()
        )
        
        // Create user in Firestore
        val createResult = userRepository.createUser(testUser)
        assertTrue("User creation should succeed", createResult.isSuccess)
        
        // Retrieve user from Firestore
        val getResult = userRepository.getUser("test-user-1")
        assertTrue("User retrieval should succeed", getResult.isSuccess)
        
        val retrievedUser = getResult.getOrNull()
        assertNotNull("Retrieved user should not be null", retrievedUser)
        assertEquals("User ID should match", testUser.uid, retrievedUser?.uid)
        assertEquals("Display name should match", testUser.displayName, retrievedUser?.displayName)
        assertEquals("Email should match", testUser.email, retrievedUser?.email)
        assertEquals("Matching code should match", testUser.matchingCode, retrievedUser?.matchingCode)
    }
    
    @Test
    fun testGetNonExistentUser() = runTest {
        // Try to get a user that doesn't exist
        val result = userRepository.getUser("non-existent-user")
        assertTrue("Getting non-existent user should succeed", result.isSuccess)
        assertNull("Non-existent user should return null", result.getOrNull())
    }
    
    @Test
    fun testUpdateUser() = runTest {
        // Create initial user
        val initialUser = User(
            uid = "test-user-2",
            displayName = "Initial Name",
            email = "initial@example.com",
            matchingCode = "INIT01",
            totalPointsReceived = 0,
            createdAt = Timestamp.now()
        )
        
        userRepository.createUser(initialUser)
        
        // Update user
        val updatedUser = initialUser.copy(
            displayName = "Updated Name",
            totalPointsReceived = 10
        )
        
        val updateResult = userRepository.updateUser(updatedUser)
        assertTrue("User update should succeed", updateResult.isSuccess)
        
        // Verify update
        val getResult = userRepository.getUser("test-user-2")
        val retrievedUser = getResult.getOrNull()
        
        assertEquals("Display name should be updated", "Updated Name", retrievedUser?.displayName)
        assertEquals("Points should be updated", 10, retrievedUser?.totalPointsReceived)
        assertEquals("Email should remain same", "initial@example.com", retrievedUser?.email)
    }
    
    @Test
    fun testObserveUser() = runTest {
        val testUserId = "test-user-3"
        
        // Start observing user
        userRepository.observeUser(testUserId).test {
            // Initially should be null
            assertNull("User should initially be null", awaitItem())
            
            // Create user
            val testUser = User(
                uid = testUserId,
                displayName = "Observable User",
                email = "observable@example.com",
                matchingCode = "OBS123",
                createdAt = Timestamp.now()
            )
            
            userRepository.createUser(testUser)
            
            // Should receive the created user
            val observedUser = awaitItem()
            assertNotNull("Should observe created user", observedUser)
            assertEquals("User ID should match", testUserId, observedUser?.uid)
            assertEquals("Display name should match", "Observable User", observedUser?.displayName)
            
            // Update user
            val updatedUser = testUser.copy(displayName = "Updated Observable User")
            userRepository.updateUser(updatedUser)
            
            // Should receive the updated user
            val updatedObservedUser = awaitItem()
            assertEquals("Should observe updated display name", "Updated Observable User", updatedObservedUser?.displayName)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun testGenerateMatchingCode() = runTest {
        // Generate multiple matching codes
        val code1 = userRepository.generateMatchingCode()
        val code2 = userRepository.generateMatchingCode()
        
        // Verify format
        assertEquals("Matching code should be 6 characters", 6, code1.length)
        assertEquals("Matching code should be 6 characters", 6, code2.length)
        assertTrue("Matching code should be alphanumeric", code1.all { it.isLetterOrDigit() })
        assertTrue("Matching code should be alphanumeric", code2.all { it.isLetterOrDigit() })
        
        // Codes should be different (very high probability)
        assertNotEquals("Generated codes should be different", code1, code2)
    }
    
    @Test
    fun testFindUserByMatchingCode() = runTest {
        // Create a user with a specific matching code
        val testUser = User(
            uid = "test-user-4",
            displayName = "Findable User",
            email = "findable@example.com",
            matchingCode = "FIND01",
            createdAt = Timestamp.now()
        )
        
        userRepository.createUser(testUser)
        
        // Find user by matching code
        val findResult = userRepository.findUserByMatchingCode("FIND01")
        assertTrue("Finding user should succeed", findResult.isSuccess)
        
        val foundUser = findResult.getOrNull()
        assertNotNull("Found user should not be null", foundUser)
        assertEquals("Found user ID should match", testUser.uid, foundUser?.uid)
        assertEquals("Found user matching code should match", "FIND01", foundUser?.matchingCode)
    }
    
    @Test
    fun testFindUserByInvalidMatchingCode() = runTest {
        // Test with invalid matching code format
        val result1 = userRepository.findUserByMatchingCode("ABC") // Too short
        assertTrue("Should handle short matching code", result1.isFailure)
        
        val result2 = userRepository.findUserByMatchingCode("ABCDEFG") // Too long
        assertTrue("Should handle long matching code", result2.isFailure)
        
        val result3 = userRepository.findUserByMatchingCode("ABC@#$") // Invalid characters
        assertTrue("Should handle invalid characters", result3.isFailure)
    }
    
    @Test
    fun testFindUserByNonExistentMatchingCode() = runTest {
        // Try to find user with non-existent matching code
        val result = userRepository.findUserByMatchingCode("NOEXST")
        assertTrue("Finding non-existent user should succeed", result.isSuccess)
        assertNull("Non-existent user should return null", result.getOrNull())
    }
}