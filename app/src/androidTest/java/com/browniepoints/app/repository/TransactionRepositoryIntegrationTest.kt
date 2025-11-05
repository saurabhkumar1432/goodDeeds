package com.browniepoints.app.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.browniepoints.app.BaseFirebaseTest
import com.browniepoints.app.data.repository.TransactionRepository
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Integration tests for TransactionRepository with Firestore operations
 * 
 * These tests verify:
 * - Atomic transaction creation with point balance updates
 * - Transaction history queries
 * - Real-time transaction observation
 * 
 * Requirements tested: 3.3, 3.4, 5.1, 5.2, 5.4, 5.5
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TransactionRepositoryIntegrationTest : BaseFirebaseTest() {
    
    @Inject
    lateinit var transactionRepository: TransactionRepository
    
    @Test
    fun testCreateTransaction() = runTest {
        // Create test users first
        createTestUser("sender-1", "Sender User", "sender@example.com", "SEND01")
        createTestUser("receiver-1", "Receiver User", "receiver@example.com", "RECV01")
        
        // Create a transaction
        val result = transactionRepository.createTransaction(
            senderId = "sender-1",
            receiverId = "receiver-1",
            points = 5,
            message = "Great job!",
            connectionId = "connection-1"
        )
        
        assertTrue("Transaction creation should succeed", result.isSuccess)
        
        val transaction = result.getOrNull()
        assertNotNull("Transaction should not be null", transaction)
        assertEquals("Sender ID should match", "sender-1", transaction?.senderId)
        assertEquals("Receiver ID should match", "receiver-1", transaction?.receiverId)
        assertEquals("Points should match", 5, transaction?.points)
        assertEquals("Message should match", "Great job!", transaction?.message)
        assertEquals("Connection ID should match", "connection-1", transaction?.connectionId)
        assertNotNull("Transaction should have an ID", transaction?.id)
        assertNotNull("Transaction should have a timestamp", transaction?.timestamp)
    }
    
    @Test
    fun testCreateTransactionUpdatesReceiverPoints() = runTest {
        // Create test users
        createTestUser("sender-2", "Sender User 2", "sender2@example.com", "SND002")
        createTestUser("receiver-2", "Receiver User 2", "receiver2@example.com", "RCV002")
        
        // Verify initial points
        val initialReceiver = firestore.collection("users").document("receiver-2").get().await()
        val initialPoints = initialReceiver.getLong("totalPointsReceived") ?: 0L
        assertEquals("Initial points should be 0", 0L, initialPoints)
        
        // Create transaction
        val result = transactionRepository.createTransaction(
            senderId = "sender-2",
            receiverId = "receiver-2",
            points = 7,
            message = null,
            connectionId = "connection-2"
        )
        
        assertTrue("Transaction creation should succeed", result.isSuccess)
        
        // Verify receiver's points were updated
        val updatedReceiver = firestore.collection("users").document("receiver-2").get().await()
        val updatedPoints = updatedReceiver.getLong("totalPointsReceived") ?: 0L
        assertEquals("Receiver points should be updated", 7L, updatedPoints)
    }
    
    @Test
    fun testCreateMultipleTransactionsUpdatesPointsCorrectly() = runTest {
        // Create test users
        createTestUser("sender-3", "Sender User 3", "sender3@example.com", "SND003")
        createTestUser("receiver-3", "Receiver User 3", "receiver3@example.com", "RCV003")
        
        // Create first transaction
        transactionRepository.createTransaction(
            senderId = "sender-3",
            receiverId = "receiver-3",
            points = 3,
            message = "First transaction",
            connectionId = "connection-3"
        )
        
        // Create second transaction
        transactionRepository.createTransaction(
            senderId = "sender-3",
            receiverId = "receiver-3",
            points = 4,
            message = "Second transaction",
            connectionId = "connection-3"
        )
        
        // Verify total points
        val receiverDoc = firestore.collection("users").document("receiver-3").get().await()
        val totalPoints = receiverDoc.getLong("totalPointsReceived") ?: 0L
        assertEquals("Total points should be sum of all transactions", 7L, totalPoints)
    }
    
    @Test
    fun testGetTransactionHistory() = runTest {
        // Create test users
        createTestUser("user-4", "User 4", "user4@example.com", "USR004")
        createTestUser("user-5", "User 5", "user5@example.com", "USR005")
        
        // Create transactions where user-4 sends and receives
        transactionRepository.createTransaction(
            senderId = "user-4",
            receiverId = "user-5",
            points = 2,
            message = "Sent transaction",
            connectionId = "connection-4"
        )
        
        transactionRepository.createTransaction(
            senderId = "user-5",
            receiverId = "user-4",
            points = 3,
            message = "Received transaction",
            connectionId = "connection-4"
        )
        
        // Get transaction history for user-4
        val result = transactionRepository.getTransactionHistory("user-4")
        assertTrue("Getting transaction history should succeed", result.isSuccess)
        
        val transactions = result.getOrNull()
        assertNotNull("Transactions should not be null", transactions)
        assertEquals("Should have 2 transactions", 2, transactions?.size)
        
        // Verify transactions are sorted by timestamp (most recent first)
        val sortedTransactions = transactions?.sortedByDescending { it.timestamp }
        assertEquals("Transactions should be sorted by timestamp", sortedTransactions, transactions)
        
        // Verify both sent and received transactions are included
        val sentTransaction = transactions?.find { it.senderId == "user-4" }
        val receivedTransaction = transactions?.find { it.receiverId == "user-4" }
        
        assertNotNull("Should include sent transaction", sentTransaction)
        assertNotNull("Should include received transaction", receivedTransaction)
        assertEquals("Sent transaction message should match", "Sent transaction", sentTransaction?.message)
        assertEquals("Received transaction message should match", "Received transaction", receivedTransaction?.message)
    }
    
    @Test
    fun testObserveTransactions() = runTest {
        val userId = "user-6"
        val partnerId = "user-7"
        
        // Create test users
        createTestUser(userId, "User 6", "user6@example.com", "USR006")
        createTestUser(partnerId, "User 7", "user7@example.com", "USR007")
        
        // Start observing transactions
        transactionRepository.observeTransactions(userId).test {
            // Initially should be empty
            val initialTransactions = awaitItem()
            assertTrue("Initial transactions should be empty", initialTransactions.isEmpty())
            
            // Create a transaction
            transactionRepository.createTransaction(
                senderId = userId,
                receiverId = partnerId,
                points = 4,
                message = "First observed transaction",
                connectionId = "connection-6"
            )
            
            // Should receive the new transaction
            val updatedTransactions = awaitItem()
            assertEquals("Should have 1 transaction", 1, updatedTransactions.size)
            assertEquals("Transaction message should match", "First observed transaction", updatedTransactions[0].message)
            assertEquals("Transaction points should match", 4, updatedTransactions[0].points)
            
            // Create another transaction (received)
            transactionRepository.createTransaction(
                senderId = partnerId,
                receiverId = userId,
                points = 6,
                message = "Second observed transaction",
                connectionId = "connection-6"
            )
            
            // Should receive both transactions
            val allTransactions = awaitItem()
            assertEquals("Should have 2 transactions", 2, allTransactions.size)
            
            // Verify transactions are sorted by timestamp (most recent first)
            assertTrue("Transactions should be sorted by timestamp", 
                allTransactions[0].timestamp >= allTransactions[1].timestamp)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun testTransactionWithoutMessage() = runTest {
        // Create test users
        createTestUser("sender-8", "Sender User 8", "sender8@example.com", "SND008")
        createTestUser("receiver-8", "Receiver User 8", "receiver8@example.com", "RCV008")
        
        // Create transaction without message
        val result = transactionRepository.createTransaction(
            senderId = "sender-8",
            receiverId = "receiver-8",
            points = 1,
            message = null,
            connectionId = "connection-8"
        )
        
        assertTrue("Transaction creation should succeed", result.isSuccess)
        
        val transaction = result.getOrNull()
        assertNotNull("Transaction should not be null", transaction)
        assertNull("Message should be null", transaction?.message)
        assertEquals("Points should match", 1, transaction?.points)
    }
    
    @Test
    fun testTransactionAtomicity() = runTest {
        // This test verifies that transaction creation and point updates are atomic
        // If one fails, both should fail
        
        // Create test users
        createTestUser("sender-9", "Sender User 9", "sender9@example.com", "SND009")
        createTestUser("receiver-9", "Receiver User 9", "receiver9@example.com", "RCV009")
        
        // Get initial receiver points
        val initialDoc = firestore.collection("users").document("receiver-9").get().await()
        val initialPoints = initialDoc.getLong("totalPointsReceived") ?: 0L
        
        // Create transaction
        val result = transactionRepository.createTransaction(
            senderId = "sender-9",
            receiverId = "receiver-9",
            points = 8,
            message = "Atomicity test",
            connectionId = "connection-9"
        )
        
        if (result.isSuccess) {
            // If transaction creation succeeded, points should be updated
            val updatedDoc = firestore.collection("users").document("receiver-9").get().await()
            val updatedPoints = updatedDoc.getLong("totalPointsReceived") ?: 0L
            assertEquals("Points should be updated atomically", initialPoints + 8, updatedPoints)
            
            // Transaction should exist in Firestore
            val transactionId = result.getOrNull()?.id
            assertNotNull("Transaction ID should not be null", transactionId)
            
            val transactionDoc = firestore.collection("transactions").document(transactionId!!).get().await()
            assertTrue("Transaction document should exist", transactionDoc.exists())
        }
    }

    // Tests for new couple features - Point deduction functionality

    @Test
    fun testDeductPoints() = runTest {
        // Create test users
        createTestUser("deduct-sender-1", "Deduct Sender 1", "deductsender1@example.com", "DED001")
        createTestUser("deduct-receiver-1", "Deduct Receiver 1", "deductreceiver1@example.com", "DED002")
        
        // Give receiver some initial points
        transactionRepository.createTransaction(
            senderId = "deduct-sender-1",
            receiverId = "deduct-receiver-1",
            points = 10,
            message = "Initial points",
            connectionId = "deduct-connection-1"
        )
        
        // Verify initial points
        val initialDoc = firestore.collection("users").document("deduct-receiver-1").get().await()
        val initialPoints = initialDoc.getLong("totalPointsReceived") ?: 0L
        assertEquals("Initial points should be 10", 10L, initialPoints)
        
        // Deduct points
        val result = transactionRepository.deductPoints(
            senderId = "deduct-sender-1",
            receiverId = "deduct-receiver-1",
            points = 3,
            reason = "Argument about dishes",
            connectionId = "deduct-connection-1"
        )
        
        assertTrue("Point deduction should succeed", result.isSuccess)
        
        val transaction = result.getOrNull()
        assertNotNull("Deduction transaction should not be null", transaction)
        assertEquals("Sender ID should match", "deduct-sender-1", transaction?.senderId)
        assertEquals("Receiver ID should match", "deduct-receiver-1", transaction?.receiverId)
        assertEquals("Points should be negative", -3, transaction?.points)
        assertEquals("Reason should match", "Argument about dishes", transaction?.message)
        assertEquals("Connection ID should match", "deduct-connection-1", transaction?.connectionId)
        assertEquals("Transaction type should be DEDUCT", com.browniepoints.app.data.model.TransactionType.DEDUCT, transaction?.type)
        
        // Verify receiver's points were deducted
        val updatedDoc = firestore.collection("users").document("deduct-receiver-1").get().await()
        val updatedPoints = updatedDoc.getLong("totalPointsReceived") ?: 0L
        assertEquals("Receiver points should be deducted", 7L, updatedPoints) // 10 - 3 = 7
    }
    
    @Test
    fun testDeductPointsAllowsNegativeBalance() = runTest {
        // Create test users
        createTestUser("deduct-sender-2", "Deduct Sender 2", "deductsender2@example.com", "DED003")
        createTestUser("deduct-receiver-2", "Deduct Receiver 2", "deductreceiver2@example.com", "DED004")
        
        // Give receiver only 2 points initially
        transactionRepository.createTransaction(
            senderId = "deduct-sender-2",
            receiverId = "deduct-receiver-2",
            points = 2,
            message = "Small initial points",
            connectionId = "deduct-connection-2"
        )
        
        // Deduct more points than available (should allow negative balance)
        val result = transactionRepository.deductPoints(
            senderId = "deduct-sender-2",
            receiverId = "deduct-receiver-2",
            points = 5,
            reason = "Major disagreement",
            connectionId = "deduct-connection-2"
        )
        
        assertTrue("Point deduction should succeed even with negative balance", result.isSuccess)
        
        val transaction = result.getOrNull()
        assertNotNull("Deduction transaction should not be null", transaction)
        assertEquals("Points should be negative", -5, transaction?.points)
        
        // Verify receiver's balance is now negative
        val updatedDoc = firestore.collection("users").document("deduct-receiver-2").get().await()
        val updatedPoints = updatedDoc.getLong("totalPointsReceived") ?: 0L
        assertEquals("Receiver points should be negative", -3L, updatedPoints) // 2 - 5 = -3
    }
    
    @Test
    fun testGivePointsMethod() = runTest {
        // Create test users
        createTestUser("give-sender-1", "Give Sender 1", "givesender1@example.com", "GIV001")
        createTestUser("give-receiver-1", "Give Receiver 1", "givereceiver1@example.com", "GIV002")
        
        // Use givePoints method
        val result = transactionRepository.givePoints(
            senderId = "give-sender-1",
            receiverId = "give-receiver-1",
            points = 4,
            message = "Thanks for helping!",
            connectionId = "give-connection-1"
        )
        
        assertTrue("Give points should succeed", result.isSuccess)
        
        val transaction = result.getOrNull()
        assertNotNull("Give transaction should not be null", transaction)
        assertEquals("Points should be positive", 4, transaction?.points)
        assertEquals("Message should match", "Thanks for helping!", transaction?.message)
        assertEquals("Transaction type should be GIVE", com.browniepoints.app.data.model.TransactionType.GIVE, transaction?.type)
        
        // Verify receiver's points were added
        val updatedDoc = firestore.collection("users").document("give-receiver-1").get().await()
        val updatedPoints = updatedDoc.getLong("totalPointsReceived") ?: 0L
        assertEquals("Receiver points should be added", 4L, updatedPoints)
    }
    
    @Test
    fun testMixedTransactionHistory() = runTest {
        // Create test users
        createTestUser("mixed-user-1", "Mixed User 1", "mixed1@example.com", "MIX001")
        createTestUser("mixed-user-2", "Mixed User 2", "mixed2@example.com", "MIX002")
        
        // Create mixed transactions (give and deduct)
        transactionRepository.givePoints(
            senderId = "mixed-user-1",
            receiverId = "mixed-user-2",
            points = 5,
            message = "Good job!",
            connectionId = "mixed-connection-1"
        )
        
        transactionRepository.deductPoints(
            senderId = "mixed-user-1",
            receiverId = "mixed-user-2",
            points = 2,
            reason = "Small disagreement",
            connectionId = "mixed-connection-1"
        )
        
        transactionRepository.givePoints(
            senderId = "mixed-user-2",
            receiverId = "mixed-user-1",
            points = 3,
            message = "Thanks for understanding",
            connectionId = "mixed-connection-1"
        )
        
        // Get transaction history for mixed-user-1
        val result = transactionRepository.getTransactionHistory("mixed-user-1")
        assertTrue("Getting mixed transaction history should succeed", result.isSuccess)
        
        val transactions = result.getOrNull()
        assertNotNull("Transactions should not be null", transactions)
        assertEquals("Should have 3 transactions", 3, transactions?.size)
        
        // Verify transaction types
        val giveTransactions = transactions?.filter { it.type == com.browniepoints.app.data.model.TransactionType.GIVE }
        val deductTransactions = transactions?.filter { it.type == com.browniepoints.app.data.model.TransactionType.DEDUCT }
        
        assertEquals("Should have 2 give transactions", 2, giveTransactions?.size)
        assertEquals("Should have 1 deduct transaction", 1, deductTransactions?.size)
        
        // Verify points are correct
        val sentGive = giveTransactions?.find { it.senderId == "mixed-user-1" }
        val receivedGive = giveTransactions?.find { it.receiverId == "mixed-user-1" }
        val sentDeduct = deductTransactions?.find { it.senderId == "mixed-user-1" }
        
        assertNotNull("Should have sent give transaction", sentGive)
        assertNotNull("Should have received give transaction", receivedGive)
        assertNotNull("Should have sent deduct transaction", sentDeduct)
        
        assertEquals("Sent give points should be positive", 5, sentGive?.points)
        assertEquals("Received give points should be positive", 3, receivedGive?.points)
        assertEquals("Sent deduct points should be negative", -2, sentDeduct?.points)
        
        // Verify final balance for mixed-user-2
        val finalDoc = firestore.collection("users").document("mixed-user-2").get().await()
        val finalPoints = finalDoc.getLong("totalPointsReceived") ?: 0L
        assertEquals("Final points should be 3 (5 - 2)", 3L, finalPoints)
    }
    
    @Test
    fun testObserveTransactionsWithDeductions() = runTest {
        val userId = "observe-user-1"
        val partnerId = "observe-user-2"
        
        // Create test users
        createTestUser(userId, "Observe User 1", "observe1@example.com", "OBS001")
        createTestUser(partnerId, "Observe User 2", "observe2@example.com", "OBS002")
        
        // Start observing transactions
        transactionRepository.observeTransactions(userId).test {
            // Initially should be empty
            val initialTransactions = awaitItem()
            assertTrue("Initial transactions should be empty", initialTransactions.isEmpty())
            
            // Create a give transaction
            transactionRepository.givePoints(
                senderId = userId,
                receiverId = partnerId,
                points = 4,
                message = "Good work!",
                connectionId = "observe-connection-1"
            )
            
            // Should receive the give transaction
            val afterGive = awaitItem()
            assertEquals("Should have 1 transaction", 1, afterGive.size)
            assertEquals("Transaction should be give type", com.browniepoints.app.data.model.TransactionType.GIVE, afterGive[0].type)
            assertEquals("Points should be positive", 4, afterGive[0].points)
            
            // Create a deduct transaction
            transactionRepository.deductPoints(
                senderId = partnerId,
                receiverId = userId,
                points = 2,
                reason = "Minor issue",
                connectionId = "observe-connection-1"
            )
            
            // Should receive both transactions
            val afterDeduct = awaitItem()
            assertEquals("Should have 2 transactions", 2, afterDeduct.size)
            
            // Find the deduct transaction
            val deductTransaction = afterDeduct.find { it.type == com.browniepoints.app.data.model.TransactionType.DEDUCT }
            assertNotNull("Should have deduct transaction", deductTransaction)
            assertEquals("Deduct points should be negative", -2, deductTransaction?.points)
            assertEquals("Deduct reason should match", "Minor issue", deductTransaction?.message)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun testDeductPointsAtomicity() = runTest {
        // This test verifies that deduction transactions are atomic
        
        // Create test users
        createTestUser("atomic-sender", "Atomic Sender", "atomicsender@example.com", "ATO001")
        createTestUser("atomic-receiver", "Atomic Receiver", "atomicreceiver@example.com", "ATO002")
        
        // Give initial points
        transactionRepository.givePoints(
            senderId = "atomic-sender",
            receiverId = "atomic-receiver",
            points = 8,
            message = "Initial points",
            connectionId = "atomic-connection"
        )
        
        // Get initial receiver points
        val initialDoc = firestore.collection("users").document("atomic-receiver").get().await()
        val initialPoints = initialDoc.getLong("totalPointsReceived") ?: 0L
        assertEquals("Initial points should be 8", 8L, initialPoints)
        
        // Deduct points
        val result = transactionRepository.deductPoints(
            senderId = "atomic-sender",
            receiverId = "atomic-receiver",
            points = 3,
            reason = "Atomicity test deduction",
            connectionId = "atomic-connection"
        )
        
        if (result.isSuccess) {
            // If deduction succeeded, points should be updated
            val updatedDoc = firestore.collection("users").document("atomic-receiver").get().await()
            val updatedPoints = updatedDoc.getLong("totalPointsReceived") ?: 0L
            assertEquals("Points should be deducted atomically", initialPoints - 3, updatedPoints)
            
            // Deduction transaction should exist in Firestore
            val transactionId = result.getOrNull()?.id
            assertNotNull("Transaction ID should not be null", transactionId)
            
            val transactionDoc = firestore.collection("transactions").document(transactionId!!).get().await()
            assertTrue("Deduction transaction document should exist", transactionDoc.exists())
            assertEquals("Transaction points should be negative", -3L, transactionDoc.getLong("points"))
            assertEquals("Transaction type should be DEDUCT", "DEDUCT", transactionDoc.getString("type"))
        }
    }
}