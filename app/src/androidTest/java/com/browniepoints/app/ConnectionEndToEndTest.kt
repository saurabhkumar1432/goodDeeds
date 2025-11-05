package com.browniepoints.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.browniepoints.app.data.repository.ConnectionRepository
import com.browniepoints.app.data.repository.UserRepository
import com.browniepoints.app.data.model.Connection
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * End-to-end integration tests for connection establishment and partner interaction
 * 
 * These tests verify the complete workflow of:
 * - Matching code generation and validation
 * - Connection creation and bidirectional updates
 * - Partner data loading and real-time synchronization
 * 
 * Requirements tested: 2.1, 2.2, 2.3, 2.4, 2.5
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ConnectionEndToEndTest : BaseFirebaseTest() {
    
    @Inject
    lateinit var connectionRepository: ConnectionRepository
    
    @Inject
    lateinit var userRepository: UserRepository
    
    @Test
    fun testCompleteConnectionEstablishmentFlow() = runTest {
        // Requirement 2.1: Provide options to enter matching code or add friend
        // Requirement 2.2: Query Firestore to find corresponding user and establish connection
        // Requirement 2.3: Update both users' connection status and enable point exchange
        
        val user1Id = "connection-user-1"
        val user2Id = "connection-user-2"
        val user1MatchingCode = "CONN01"
        val user2MatchingCode = "CONN02"
        
        // Step 1: Create two users with unique matching codes
        createTestUser(user1Id, "Connection User 1", "conn1@example.com", user1MatchingCode)
        createTestUser(user2Id, "Connection User 2", "conn2@example.com", user2MatchingCode)
        
        // Step 2: User 1 attempts to connect using User 2's matching code
        val connectionResult = connectionRepository.createConnection(user1Id, user2MatchingCode)
        assertTrue("Connection creation should succeed", connectionResult.isSuccess)
        val connection = connectionResult.getOrNull()!!
        
        // Step 3: Verify connection was created correctly
        assertNotNull("Connection should have an ID", connection.id)
        assertTrue("Connection should involve user 1", 
            connection.user1Id == user1Id || connection.user2Id == user1Id)
        assertTrue("Connection should involve user 2", 
            connection.user1Id == user2Id || connection.user2Id == user2Id)
        assertTrue("Connection should be active", connection.isActive)
        assertNotNull("Connection should have creation timestamp", connection.createdAt)
        
        // Step 4: Verify connection was persisted in Firestore
        val firestoreConnection = firestore.collection("connections")
            .document(connection.id)
            .get()
            .await()
        
        assertTrue("Connection should exist in Firestore", firestoreConnection.exists())
        assertEquals("Firestore user1Id should match", connection.user1Id, firestoreConnection.getString("user1Id"))
        assertEquals("Firestore user2Id should match", connection.user2Id, firestoreConnection.getString("user2Id"))
        assertTrue("Firestore connection should be active", firestoreConnection.getBoolean("isActive") == true)
        
        // Step 5: Verify both users' connection status was updated
        val user1Doc = firestore.collection("users").document(user1Id).get().await()
        val user2Doc = firestore.collection("users").document(user2Id).get().await()
        
        assertEquals("User 1 should be connected to User 2", user2Id, user1Doc.getString("connectedUserId"))
        assertEquals("User 2 should be connected to User 1", user1Id, user2Doc.getString("connectedUserId"))
    }
    
    @Test
    fun testMatchingCodeValidation() = runTest {
        // Requirement 2.4: Display error for invalid matching code and allow retry
        
        val userId = "validation-user"
        val validMatchingCode = "VALID1"
        val invalidMatchingCode = "INVALID"
        
        // Create a user with a valid matching code
        createTestUser("target-user", "Target User", "target@example.com", validMatchingCode)
        createTestUser(userId, "Validation User", "validation@example.com", "VAL001")
        
        // Test 1: Valid matching code should succeed
        val validResult = connectionRepository.createConnection(userId, validMatchingCode)
        assertTrue("Valid matching code should succeed", validResult.isSuccess)
        
        // Clean up the connection for next test
        val connection = validResult.getOrNull()!!
        firestore.collection("connections").document(connection.id).delete().await()
        firestore.collection("users").document(userId).update("connectedUserId", null).await()
        firestore.collection("users").document("target-user").update("connectedUserId", null).await()
        
        // Test 2: Invalid matching code should fail
        val invalidResult = connectionRepository.createConnection(userId, invalidMatchingCode)
        assertTrue("Invalid matching code should fail", invalidResult.isFailure)
        
        val exception = invalidResult.exceptionOrNull()
        assertNotNull("Should have exception for invalid code", exception)
        assertTrue("Error message should indicate invalid code", 
            exception?.message?.contains("matching code") == true ||
            exception?.message?.contains("not found") == true ||
            exception?.message?.contains("invalid") == true)
        
        // Test 3: Empty matching code should fail
        val emptyResult = connectionRepository.createConnection(userId, "")
        assertTrue("Empty matching code should fail", emptyResult.isFailure)
        
        // Test 4: Self-connection should fail (user trying to connect to their own code)
        val selfResult = connectionRepository.createConnection(userId, "VAL001")
        assertTrue("Self-connection should fail", selfResult.isFailure)
        assertTrue("Should indicate cannot connect to self", 
            selfResult.exceptionOrNull()?.message?.contains("yourself") == true ||
            selfResult.exceptionOrNull()?.message?.contains("self") == true)
    }
    
    @Test
    fun testBidirectionalConnectionUpdates() = runTest {
        // Requirement 2.5: Store relationship in Firestore with real-time synchronization
        
        val user1Id = "bidirectional-user-1"
        val user2Id = "bidirectional-user-2"
        val user1Code = "BIDIR1"
        val user2Code = "BIDIR2"
        
        // Create users
        createTestUser(user1Id, "Bidirectional User 1", "bidir1@example.com", user1Code)
        createTestUser(user2Id, "Bidirectional User 2", "bidir2@example.com", user2Code)
        
        // Observe connection status for both users
        connectionRepository.observeConnectionStatus(user1Id).test {
            val initialUser1Status = awaitItem()
            assertFalse("User 1 should initially not be connected", initialUser1Status)
            
            connectionRepository.observeConnectionStatus(user2Id).test {
                val initialUser2Status = awaitItem()
                assertFalse("User 2 should initially not be connected", initialUser2Status)
                
                // Create connection
                val connectionResult = connectionRepository.createConnection(user1Id, user2Code)
                assertTrue("Connection should succeed", connectionResult.isSuccess)
                
                // Both users should receive connection status updates
                val user1Connected = awaitItem()
                assertTrue("User 1 should be connected", user1Connected)
                
                val user2Connected = awaitItem()
                assertTrue("User 2 should be connected", user2Connected)
                
                cancelAndIgnoreRemainingEvents()
            }
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun testPartnerDataLoadingAndSynchronization() = runTest {
        // Test loading partner data and real-time synchronization
        
        val user1Id = "partner-user-1"
        val user2Id = "partner-user-2"
        val user1Code = "PART01"
        val user2Code = "PART02"
        
        // Create users
        createTestUser(user1Id, "Partner User 1", "partner1@example.com", user1Code)
        createTestUser(user2Id, "Partner User 2", "partner2@example.com", user2Code)
        
        // Create connection
        val connectionResult = connectionRepository.createConnection(user1Id, user2Code)
        assertTrue("Connection should succeed", connectionResult.isSuccess)
        val connection = connectionResult.getOrNull()!!
        
        // Test loading partner data for User 1
        val partner1Result = connectionRepository.getConnectedPartner(user1Id)
        assertTrue("Getting partner for User 1 should succeed", partner1Result.isSuccess)
        val partner1 = partner1Result.getOrNull()!!
        
        assertEquals("Partner should be User 2", user2Id, partner1.uid)
        assertEquals("Partner name should match", "Partner User 2", partner1.displayName)
        assertEquals("Partner email should match", "partner2@example.com", partner1.email)
        
        // Test loading partner data for User 2
        val partner2Result = connectionRepository.getConnectedPartner(user2Id)
        assertTrue("Getting partner for User 2 should succeed", partner2Result.isSuccess)
        val partner2 = partner2Result.getOrNull()!!
        
        assertEquals("Partner should be User 1", user1Id, partner2.uid)
        assertEquals("Partner name should match", "Partner User 1", partner2.displayName)
        assertEquals("Partner email should match", "partner1@example.com", partner2.email)
        
        // Test real-time synchronization of partner data changes
        connectionRepository.observeConnectedPartner(user1Id).test {
            val initialPartner = awaitItem()
            assertNotNull("Should have initial partner", initialPartner)
            assertEquals("Initial partner should be User 2", user2Id, initialPartner?.uid)
            
            // Update User 2's profile
            val updatedUser2 = partner1.copy(displayName = "Updated Partner Name")
            val updateResult = userRepository.updateUser(updatedUser2)
            assertTrue("Update should succeed", updateResult.isSuccess)
            
            // Should receive updated partner data
            val updatedPartner = awaitItem()
            assertNotNull("Should receive updated partner", updatedPartner)
            assertEquals("Partner name should be updated", "Updated Partner Name", updatedPartner?.displayName)
            assertEquals("Partner ID should remain same", user2Id, updatedPartner?.uid)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun testConnectionStateManagement() = runTest {
        // Test connection state management and edge cases
        
        val user1Id = "state-user-1"
        val user2Id = "state-user-2"
        val user3Id = "state-user-3"
        val user1Code = "STATE1"
        val user2Code = "STATE2"
        val user3Code = "STATE3"
        
        // Create users
        createTestUser(user1Id, "State User 1", "state1@example.com", user1Code)
        createTestUser(user2Id, "State User 2", "state2@example.com", user2Code)
        createTestUser(user3Id, "State User 3", "state3@example.com", user3Code)
        
        // Test 1: User can only have one active connection
        val connection1Result = connectionRepository.createConnection(user1Id, user2Code)
        assertTrue("First connection should succeed", connection1Result.isSuccess)
        
        // Attempting second connection should fail
        val connection2Result = connectionRepository.createConnection(user1Id, user3Code)
        assertTrue("Second connection should fail", connection2Result.isFailure)
        assertTrue("Should indicate already connected", 
            connection2Result.exceptionOrNull()?.message?.contains("already connected") == true ||
            connection2Result.exceptionOrNull()?.message?.contains("existing connection") == true)
        
        // Test 2: Connection should be retrievable
        val getConnectionResult = connectionRepository.getConnection(user1Id)
        assertTrue("Getting connection should succeed", getConnectionResult.isSuccess)
        val retrievedConnection = getConnectionResult.getOrNull()!!
        
        assertEquals("Retrieved connection should match created", connection1Result.getOrNull()?.id, retrievedConnection.id)
        assertTrue("Retrieved connection should be active", retrievedConnection.isActive)
        
        // Test 3: Both users should see the same connection
        val user2ConnectionResult = connectionRepository.getConnection(user2Id)
        assertTrue("User 2 should also have connection", user2ConnectionResult.isSuccess)
        val user2Connection = user2ConnectionResult.getOrNull()!!
        
        assertEquals("Both users should see same connection", retrievedConnection.id, user2Connection.id)
    }
    
    @Test
    fun testConnectionDisconnection() = runTest {
        // Test disconnection functionality
        
        val user1Id = "disconnect-user-1"
        val user2Id = "disconnect-user-2"
        val user1Code = "DISC01"
        val user2Code = "DISC02"
        
        // Create users and connection
        createTestUser(user1Id, "Disconnect User 1", "disc1@example.com", user1Code)
        createTestUser(user2Id, "Disconnect User 2", "disc2@example.com", user2Code)
        
        val connectionResult = connectionRepository.createConnection(user1Id, user2Code)
        assertTrue("Connection should succeed", connectionResult.isSuccess)
        val connection = connectionResult.getOrNull()!!
        
        // Verify connection exists
        val beforeDisconnect = connectionRepository.getConnection(user1Id)
        assertTrue("Connection should exist before disconnect", beforeDisconnect.isSuccess)
        
        // Disconnect
        val disconnectResult = connectionRepository.disconnectUsers(user1Id)
        assertTrue("Disconnect should succeed", disconnectResult.isSuccess)
        
        // Verify connection is deactivated
        val afterDisconnect = connectionRepository.getConnection(user1Id)
        assertTrue("Getting connection after disconnect should fail or return inactive", 
            afterDisconnect.isFailure || afterDisconnect.getOrNull()?.isActive == false)
        
        // Verify both users are disconnected
        val user1Doc = firestore.collection("users").document(user1Id).get().await()
        val user2Doc = firestore.collection("users").document(user2Id).get().await()
        
        assertNull("User 1 should not have connected user", user1Doc.getString("connectedUserId"))
        assertNull("User 2 should not have connected user", user2Doc.getString("connectedUserId"))
        
        // Verify users can connect to others after disconnection
        val user3Id = "reconnect-user"
        val user3Code = "RECONN"
        createTestUser(user3Id, "Reconnect User", "reconnect@example.com", user3Code)
        
        val reconnectResult = connectionRepository.createConnection(user1Id, user3Code)
        assertTrue("Should be able to reconnect after disconnect", reconnectResult.isSuccess)
    }
    
    @Test
    fun testRealTimeConnectionSynchronization() = runTest {
        // Test real-time synchronization of connection changes
        
        val user1Id = "realtime-conn-user-1"
        val user2Id = "realtime-conn-user-2"
        val user1Code = "RTCONN1"
        val user2Code = "RTCONN2"
        
        // Create users
        createTestUser(user1Id, "Realtime Conn User 1", "rtconn1@example.com", user1Code)
        createTestUser(user2Id, "Realtime Conn User 2", "rtconn2@example.com", user2Code)
        
        // Observe connection changes for User 1
        connectionRepository.observeConnection(user1Id).test {
            // Initially no connection
            val initialConnection = awaitItem()
            assertNull("Should initially have no connection", initialConnection)
            
            // Create connection from User 2's side
            val connectionResult = connectionRepository.createConnection(user2Id, user1Code)
            assertTrue("Connection should succeed", connectionResult.isSuccess)
            
            // User 1 should receive the connection update
            val newConnection = awaitItem()
            assertNotNull("Should receive new connection", newConnection)
            assertTrue("Connection should be active", newConnection?.isActive == true)
            assertTrue("Connection should involve both users", 
                (newConnection?.user1Id == user1Id && newConnection?.user2Id == user2Id) ||
                (newConnection?.user1Id == user2Id && newConnection?.user2Id == user1Id))
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}