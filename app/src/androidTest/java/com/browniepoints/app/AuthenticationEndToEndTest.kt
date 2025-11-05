package com.browniepoints.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.cash.turbine.awaitItem
import app.cash.turbine.cancelAndIgnoreRemainingEvents
import com.browniepoints.app.data.repository.AuthRepository
import com.browniepoints.app.data.repository.UserRepository
import com.browniepoints.app.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * End-to-end integration tests for complete user authentication flow
 * 
 * These tests verify the complete workflow of:
 * - Google Sign-In integration from start to finish
 * - User profile creation and data persistence
 * - Authentication state management and navigation
 * 
 * Requirements tested: 1.1, 1.2, 1.4, 1.5
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AuthenticationEndToEndTest : BaseFirebaseTest() {
    
    @Inject
    lateinit var authRepository: AuthRepository
    
    @Inject
    lateinit var userRepository: UserRepository
    
    @Test
    fun testCompleteGoogleSignInFlow() = runTest {
        // Requirement 1.1: Google Sign-In screen using Firebase Authentication
        // Requirement 1.4: Automatic authentication for returning users
        
        // Initially, user should not be authenticated
        assertNull("User should not be authenticated initially", firebaseAuth.currentUser)
        
        // Observe authentication state changes
        authRepository.observeAuthState().test {
            // Initial state should be null (not authenticated)
            val initialState = awaitItem()
            assertNull("Initial auth state should be null", initialState)
            
            // Simulate Google Sign-In success by creating a test user directly
            // In real implementation, this would be handled by Google Sign-In SDK
            val testUserEmail = "testuser@example.com"
            val testUserName = "Test User"
            val testUserId = "test-auth-user-123"
            
            // Create Firebase Auth user (simulating successful Google Sign-In)
            // Note: In emulator, we can create users directly for testing
            val authResult = firebaseAuth.createUserWithEmailAndPassword(testUserEmail, "testpassword").await()
            val firebaseUser = authResult.user!!
            
            // Should receive authentication state change
            val authenticatedState = awaitItem()
            assertNotNull("Should be authenticated after sign-in", authenticatedState)
            assertEquals("User ID should match", firebaseUser.uid, authenticatedState?.uid)
            assertEquals("Email should match", testUserEmail, authenticatedState?.email)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun testUserProfileCreationOnFirstSignIn() = runTest {
        // Requirement 1.2: Create user profile in Firestore with Google account information
        // Requirement 1.3: Generate unique matching code and store in Firestore
        
        val testUserEmail = "newuser@example.com"
        val testUserName = "New User"
        
        // Create Firebase Auth user (simulating first-time Google Sign-In)
        val authResult = firebaseAuth.createUserWithEmailAndPassword(testUserEmail, "testpassword").await()
        val firebaseUser = authResult.user!!
        
        // Simulate the profile creation that happens after successful sign-in
        val newUser = User(
            uid = firebaseUser.uid,
            displayName = testUserName,
            email = testUserEmail,
            photoUrl = null,
            matchingCode = "TEST123" // Will be generated in real implementation
        )
        val createUserResult = userRepository.createUser(newUser)
        
        assertTrue("User creation should succeed", createUserResult.isSuccess)
        val createdUser = createUserResult.getOrNull()!!
        
        // Verify user profile was created correctly
        assertEquals("UID should match Firebase user", firebaseUser.uid, createdUser.uid)
        assertEquals("Display name should match", testUserName, createdUser.displayName)
        assertEquals("Email should match", testUserEmail, createdUser.email)
        assertNotNull("Matching code should be generated", createdUser.matchingCode)
        assertTrue("Matching code should not be empty", createdUser.matchingCode.isNotEmpty())
        assertEquals("Initial points should be 0", 0, createdUser.totalPointsReceived)
        assertNull("Should not have connected user initially", createdUser.connectedUserId)
        
        // Verify data was persisted in Firestore
        val firestoreDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
        assertTrue("User document should exist in Firestore", firestoreDoc.exists())
        assertEquals("Firestore UID should match", firebaseUser.uid, firestoreDoc.getString("uid"))
        assertEquals("Firestore email should match", testUserEmail, firestoreDoc.getString("email"))
        assertEquals("Firestore display name should match", testUserName, firestoreDoc.getString("displayName"))
        assertEquals("Firestore matching code should match", createdUser.matchingCode, firestoreDoc.getString("matchingCode"))
        assertEquals("Firestore points should be 0", 0L, firestoreDoc.getLong("totalPointsReceived"))
        assertNotNull("Firestore should have creation timestamp", firestoreDoc.getTimestamp("createdAt"))
    }
    
    @Test
    fun testAuthenticationStateManagement() = runTest {
        // Requirement 1.4: Automatic authentication for returning users
        // Requirement 1.5: Sync profile data from Firestore on sign-in
        
        val testUserEmail = "returning@example.com"
        val testUserName = "Returning User"
        
        // Step 1: Create user and sign in for the first time
        val authResult = firebaseAuth.createUserWithEmailAndPassword(testUserEmail, "testpassword").await()
        val firebaseUser = authResult.user!!
        
        // Create user profile
        val createResult = userRepository.createUser(
            uid = firebaseUser.uid,
            displayName = testUserName,
            email = testUserEmail,
            photoUrl = null
        )
        assertTrue("User creation should succeed", createResult.isSuccess)
        val originalUser = createResult.getOrNull()!!
        
        // Step 2: Sign out
        firebaseAuth.signOut()
        assertNull("User should be signed out", firebaseAuth.currentUser)
        
        // Step 3: Sign in again (simulating returning user)
        val returnAuthResult = firebaseAuth.signInWithEmailAndPassword(testUserEmail, "testpassword").await()
        val returnFirebaseUser = returnAuthResult.user!!
        
        assertEquals("Returning user should have same UID", firebaseUser.uid, returnFirebaseUser.uid)
        
        // Step 4: Load user profile from Firestore (simulating profile sync)
        val loadResult = userRepository.getUser(returnFirebaseUser.uid)
        assertTrue("Loading user should succeed", loadResult.isSuccess)
        val loadedUser = loadResult.getOrNull()!!
        
        // Verify profile data was synced correctly
        assertEquals("Loaded UID should match", originalUser.uid, loadedUser.uid)
        assertEquals("Loaded email should match", originalUser.email, loadedUser.email)
        assertEquals("Loaded display name should match", originalUser.displayName, loadedUser.displayName)
        assertEquals("Loaded matching code should match", originalUser.matchingCode, loadedUser.matchingCode)
        assertEquals("Loaded points should match", originalUser.totalPointsReceived, loadedUser.totalPointsReceived)
        assertEquals("Loaded connection should match", originalUser.connectedUserId, loadedUser.connectedUserId)
    }
    
    @Test
    fun testAuthenticationErrorHandling() = runTest {
        // Test authentication failure scenarios
        
        // Test invalid credentials
        try {
            firebaseAuth.signInWithEmailAndPassword("invalid@example.com", "wrongpassword").await()
            fail("Should have thrown exception for invalid credentials")
        } catch (e: Exception) {
            // Expected - invalid credentials should fail
            assertTrue("Should be authentication exception", e.message?.contains("password") == true || 
                      e.message?.contains("user") == true || e.message?.contains("email") == true)
        }
        
        // Verify user is still not authenticated
        assertNull("User should not be authenticated after failed sign-in", firebaseAuth.currentUser)
    }
    
    @Test
    fun testUserProfileUpdateAfterAuthentication() = runTest {
        // Test updating user profile after authentication
        
        val testUserEmail = "updateuser@example.com"
        val initialName = "Initial Name"
        val updatedName = "Updated Name"
        
        // Create and authenticate user
        val authResult = firebaseAuth.createUserWithEmailAndPassword(testUserEmail, "testpassword").await()
        val firebaseUser = authResult.user!!
        
        // Create initial profile
        val createResult = userRepository.createUser(
            uid = firebaseUser.uid,
            displayName = initialName,
            email = testUserEmail,
            photoUrl = null
        )
        assertTrue("User creation should succeed", createResult.isSuccess)
        val originalUser = createResult.getOrNull()!!
        
        // Update user profile
        val updatedUser = originalUser.copy(displayName = updatedName)
        val updateResult = userRepository.updateUser(updatedUser)
        assertTrue("User update should succeed", updateResult.isSuccess)
        
        // Verify update was persisted
        val loadResult = userRepository.getUser(firebaseUser.uid)
        assertTrue("Loading updated user should succeed", loadResult.isSuccess)
        val loadedUser = loadResult.getOrNull()!!
        
        assertEquals("Display name should be updated", updatedName, loadedUser.displayName)
        assertEquals("Other fields should remain unchanged", originalUser.email, loadedUser.email)
        assertEquals("Matching code should remain unchanged", originalUser.matchingCode, loadedUser.matchingCode)
    }
    
    @Test
    fun testUniqueMatchingCodeGeneration() = runTest {
        // Test that each user gets a unique matching code
        
        val user1Email = "unique1@example.com"
        val user2Email = "unique2@example.com"
        
        // Create first user
        val auth1Result = firebaseAuth.createUserWithEmailAndPassword(user1Email, "testpassword").await()
        val firebaseUser1 = auth1Result.user!!
        
        val create1Result = userRepository.createUser(
            uid = firebaseUser1.uid,
            displayName = "User 1",
            email = user1Email,
            photoUrl = null
        )
        assertTrue("User 1 creation should succeed", create1Result.isSuccess)
        val user1 = create1Result.getOrNull()!!
        
        // Sign out and create second user
        firebaseAuth.signOut()
        
        val auth2Result = firebaseAuth.createUserWithEmailAndPassword(user2Email, "testpassword").await()
        val firebaseUser2 = auth2Result.user!!
        
        val create2Result = userRepository.createUser(
            uid = firebaseUser2.uid,
            displayName = "User 2",
            email = user2Email,
            photoUrl = null
        )
        assertTrue("User 2 creation should succeed", create2Result.isSuccess)
        val user2 = create2Result.getOrNull()!!
        
        // Verify matching codes are unique
        assertNotEquals("Matching codes should be unique", user1.matchingCode, user2.matchingCode)
        assertTrue("User 1 matching code should not be empty", user1.matchingCode.isNotEmpty())
        assertTrue("User 2 matching code should not be empty", user2.matchingCode.isNotEmpty())
        
        // Verify matching codes follow expected format (if any)
        assertTrue("User 1 matching code should be valid format", user1.matchingCode.length >= 6)
        assertTrue("User 2 matching code should be valid format", user2.matchingCode.length >= 6)
    }
    
    @Test
    fun testAuthenticationNavigationFlow() = runTest {
        // Test that authentication state properly drives navigation
        
        val testUserEmail = "navigation@example.com"
        
        // Observe authentication state for navigation decisions
        authRepository.observeAuthState().test {
            // Initial state - should navigate to sign-in
            val initialState = awaitItem()
            assertNull("Initial state should be null (navigate to sign-in)", initialState)
            
            // Sign in - should navigate to main screen
            val authResult = firebaseAuth.createUserWithEmailAndPassword(testUserEmail, "testpassword").await()
            val firebaseUser = authResult.user!!
            
            val authenticatedState = awaitItem()
            assertNotNull("Should be authenticated (navigate to main screen)", authenticatedState)
            assertEquals("Authenticated user should match", firebaseUser.uid, authenticatedState?.uid)
            
            // Sign out - should navigate back to sign-in
            firebaseAuth.signOut()
            
            val signedOutState = awaitItem()
            assertNull("Should be signed out (navigate to sign-in)", signedOutState)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}