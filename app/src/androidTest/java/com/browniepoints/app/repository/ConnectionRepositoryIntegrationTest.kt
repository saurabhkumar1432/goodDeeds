package com.browniepoints.app.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.browniepoints.app.BaseFirebaseTest
import com.browniepoints.app.data.repository.ConnectionRepository
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Integration tests for ConnectionRepository with Firestore operations
 * 
 * These tests verify:
 * - User connection establishment
 * - Connection state management
 * - Real-time connection observation
 * 
 * Requirements tested: 2.1, 2.2, 2.3, 2.5
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ConnectionRepositoryIntegrationTest : BaseFirebaseTest() {
    
    @Inject
    lateinit var connectionRepository: ConnectionRepository
    
    @Test
    fun testConnectUsers() = runTest {
        // Create test users
        createTestUser("user-a", "User A", "usera@example.com", "USERA1")
        createTestUser("user-b", "User B", "userb@example.com", "USERB1")
        
        // Connect users
        val result = connectionRepository.connectUsers("user-a", "user-b")
        assertTrue("Connection should succeed", result.isSuccess)
        
        val connection = result.getOrNull()
        assertNotNull("Connection should not be null", connection)
        assertTrue("Connection should contain user-a", connection?.containsUser("user-a") == true)
        assertTrue("Connection should contain user-b", connection?.containsUser("user-b") == true)
        assertTrue("Connection should be active", connection?.isActive == true)
        assertNotNull("Connection should have an ID", connection?.id)
        assertNotNull("Connection should have creation timestamp", connection?.createdAt)
    }
    
    @Test
    fun testConnectUsersUpdatesUserProfiles() = runTest {
        // Create test users
        createTestUser("user-c", "User C", "userc@example.com", "USERC1")
        createTestUser("user-d", "User D", "userd@example.com", "USERD1")
        
        // Verify users are not connected initially
        val initialUserC = firestore.collection("users").document("user-c").get().await()
        val initialUserD = firestore.collection("users").document("user-d").get().await()
        
        assertNull("User C should not have connected user initially", initialUserC.getString("connectedUserId"))
        assertNull("User D should not have connected user initially", initialUserD.getString("connectedUserId"))
        
        // Connect users
        val result = connectionRepository.connectUsers("user-c", "user-d")
        assertTrue("Connection should succeed", result.isSuccess)
        
        // Verify user profiles are updated
        val updatedUserC = firestore.collection("users").document("user-c").get().await()
        val updatedUserD = firestore.collection("users").document("user-d").get().await()
        
        assertEquals("User C should be connected to User D", "user-d", updatedUserC.getString("connectedUserId"))
        assertEquals("User D should be connected to User C", "user-c", updatedUserD.getString("connectedUserId"))
    }
    
    @Test
    fun testGetConnection() = runTest {
        // Create test users and connection
        createTestUser("user-e", "User E", "usere@example.com", "USERE1")
        createTestUser("user-f", "User F", "userf@example.com", "USERF1")
        
        val createResult = connectionRepository.connectUsers("user-e", "user-f")
        val createdConnection = createResult.getOrNull()
        assertNotNull("Connection should be created", createdConnection)
        
        // Get connection
        val getResult = connectionRepository.getConnection("user-e")
        assertTrue("Getting connection should succeed", getResult.isSuccess)
        
        val retrievedConnection = getResult.getOrNull()
        assertNotNull("Retrieved connection should not be null", retrievedConnection)
        assertEquals("Connection ID should match", createdConnection?.id, retrievedConnection?.id)
        assertTrue("Connection should contain user-e", retrievedConnection?.containsUser("user-e") == true)
        assertTrue("Connection should contain user-f", retrievedConnection?.containsUser("user-f") == true)
    }
    
    @Test
    fun testGetConnectionForUnconnectedUser() = runTest {
        // Create a user without connections
        createTestUser("user-g", "User G", "userg@example.com", "USERG1")
        
        // Try to get connection
        val result = connectionRepository.getConnection("user-g")
        assertTrue("Getting connection should succeed", result.isSuccess)
        assertNull("Connection should be null for unconnected user", result.getOrNull())
    }
    
    @Test
    fun testObserveConnection() = runTest {
        val userId = "user-h"
        val partnerId = "user-i"
        
        // Create test users
        createTestUser(userId, "User H", "userh@example.com", "USERH1")
        createTestUser(partnerId, "User I", "useri@example.com", "USERI1")
        
        // Start observing connection
        connectionRepository.observeConnection(userId).test {
            // Initially should be null
            assertNull("Connection should initially be null", awaitItem())
            
            // Create connection
            connectionRepository.connectUsers(userId, partnerId)
            
            // Should receive the new connection
            val observedConnection = awaitItem()
            assertNotNull("Should observe created connection", observedConnection)
            assertTrue("Connection should contain user", observedConnection?.containsUser(userId) == true)
            assertTrue("Connection should contain partner", observedConnection?.containsUser(partnerId) == true)
            assertTrue("Connection should be active", observedConnection?.isActive == true)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun testDisconnectUsers() = runTest {
        // Create test users and connection
        createTestUser("user-j", "User J", "userj@example.com", "USERJ1")
        createTestUser("user-k", "User K", "userk@example.com", "USERK1")
        
        val connectResult = connectionRepository.connectUsers("user-j", "user-k")
        assertTrue("Connection should succeed", connectResult.isSuccess)
        
        // Disconnect users
        val disconnectResult = connectionRepository.disconnectUsers("user-j")
        assertTrue("Disconnection should succeed", disconnectResult.isSuccess)
        
        // Verify connection is deactivated
        val connectionResult = connectionRepository.getConnection("user-j")
        val connection = connectionResult.getOrNull()
        
        if (connection != null) {
            assertFalse("Connection should be inactive", connection.isActive)
        }
        
        // Verify user profiles are updated
        val userJ = firestore.collection("users").document("user-j").get().await()
        val userK = firestore.collection("users").document("user-k").get().await()
        
        assertNull("User J should not have connected user", userJ.getString("connectedUserId"))
        assertNull("User K should not have connected user", userK.getString("connectedUserId"))
    }
    
    @Test
    fun testConnectUserToThemselves() = runTest {
        // Create test user
        createTestUser("user-l", "User L", "userl@example.com", "USERL1")
        
        // Try to connect user to themselves
        val result = connectionRepository.connectUsers("user-l", "user-l")
        assertTrue("Self-connection should fail", result.isFailure)
    }
    
    @Test
    fun testConnectAlreadyConnectedUser() = runTest {
        // Create test users
        createTestUser("user-m", "User M", "userm@example.com", "USERM1")
        createTestUser("user-n", "User N", "usern@example.com", "USERN1")
        createTestUser("user-o", "User O", "usero@example.com", "USERO1")
        
        // Connect user-m and user-n
        val firstConnection = connectionRepository.connectUsers("user-m", "user-n")
        assertTrue("First connection should succeed", firstConnection.isSuccess)
        
        // Try to connect user-m to user-o (user-m is already connected)
        val secondConnection = connectionRepository.connectUsers("user-m", "user-o")
        assertTrue("Second connection should fail", secondConnection.isFailure)
    }
    
    @Test
    fun testGetPartnerUser() = runTest {
        // Create test users and connection
        createTestUser("user-p", "User P", "userp@example.com", "USERP1")
        createTestUser("user-q", "User Q", "userq@example.com", "USERQ1")
        
        connectionRepository.connectUsers("user-p", "user-q")
        
        // Get partner for user-p
        val partnerResult = connectionRepository.getPartnerUser("user-p")
        assertTrue("Getting partner should succeed", partnerResult.isSuccess)
        
        val partner = partnerResult.getOrNull()
        assertNotNull("Partner should not be null", partner)
        assertEquals("Partner should be user-q", "user-q", partner?.uid)
        assertEquals("Partner display name should match", "User Q", partner?.displayName)
    }
    
    @Test
    fun testGetPartnerUserForUnconnectedUser() = runTest {
        // Create unconnected user
        createTestUser("user-r", "User R", "userr@example.com", "USERR1")
        
        // Try to get partner
        val result = connectionRepository.getPartnerUser("user-r")
        assertTrue("Getting partner should succeed", result.isSuccess)
        assertNull("Partner should be null for unconnected user", result.getOrNull())
    }
}