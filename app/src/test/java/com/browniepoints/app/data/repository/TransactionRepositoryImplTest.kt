package com.browniepoints.app.data.repository

import com.browniepoints.app.data.model.Transaction
import com.browniepoints.app.data.model.TransactionType
import com.browniepoints.app.data.service.TransactionNotificationService
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class TransactionRepositoryImplTest {

    @Mock
    private lateinit var firestore: FirebaseFirestore

    @Mock
    private lateinit var transactionNotificationService: TransactionNotificationService

    @Mock
    private lateinit var transactionCollection: CollectionReference

    @Mock
    private lateinit var usersCollection: CollectionReference

    @Mock
    private lateinit var transactionDocument: DocumentReference

    @Mock
    private lateinit var userDocument: DocumentReference

    @Mock
    private lateinit var documentSnapshot: DocumentSnapshot

    @Mock
    private lateinit var querySnapshot: QuerySnapshot

    @Mock
    private lateinit var query: Query

    @Mock
    private lateinit var firestoreTransaction: com.google.firebase.firestore.Transaction

    private lateinit var transactionRepository: TransactionRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        transactionRepository = TransactionRepositoryImpl(firestore, transactionNotificationService)
    }

    @Test
    fun `createTransaction should return success when transaction is created successfully`() = runTest {
        // Given
        val senderId = "sender_id"
        val receiverId = "receiver_id"
        val points = 5
        val message = "Great job!"
        val connectionId = "connection_id"

        val expectedTransaction = Transaction(
            id = "transaction_id",
            senderId = senderId,
            receiverId = receiverId,
            points = points,
            message = message,
            connectionId = connectionId,
            timestamp = Timestamp.now()
        )

        whenever(firestore.collection("transactions")).thenReturn(transactionCollection)
        whenever(transactionCollection.document()).thenReturn(transactionDocument)
        whenever(transactionDocument.id).thenReturn("transaction_id")

        whenever(firestore.runTransaction<Transaction>(any())).thenAnswer { invocation ->
            val transactionFunction = invocation.getArgument<com.google.firebase.firestore.Transaction.Function<Transaction>>(0)
            Tasks.forResult(transactionFunction.apply(firestoreTransaction))
        }

        whenever(firestore.collection("users")).thenReturn(usersCollection)
        whenever(usersCollection.document(receiverId)).thenReturn(userDocument)
        whenever(firestoreTransaction.get(userDocument)).thenReturn(documentSnapshot)
        whenever(documentSnapshot.getLong("totalPointsReceived")).thenReturn(10L)
        whenever(firestoreTransaction.set(eq(transactionDocument), any<Transaction>())).thenReturn(firestoreTransaction)
        whenever(firestoreTransaction.update(userDocument, "totalPointsReceived", 15L)).thenReturn(firestoreTransaction)

        // When
        val result = transactionRepository.createTransaction(senderId, receiverId, points, message, connectionId)

        // Then
        assertTrue(result.isSuccess)
        val transaction = result.getOrNull()
        assertNotNull(transaction)
        assertEquals(senderId, transaction?.senderId)
        assertEquals(receiverId, transaction?.receiverId)
        assertEquals(points, transaction?.points)
        assertEquals(message, transaction?.message)
        assertEquals(connectionId, transaction?.connectionId)
    }

    @Test
    fun `createTransaction should return failure when Firestore transaction fails`() = runTest {
        // Given
        val senderId = "sender_id"
        val receiverId = "receiver_id"
        val points = 5
        val message = "Great job!"
        val connectionId = "connection_id"
        val exception = FirebaseFirestoreException("Transaction failed", FirebaseFirestoreException.Code.ABORTED)

        whenever(firestore.runTransaction<Transaction>(any())).thenReturn(Tasks.forException(exception))

        // When
        val result = transactionRepository.createTransaction(senderId, receiverId, points, message, connectionId)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `getTransactionHistory should return combined sent and received transactions`() = runTest {
        // Given
        val userId = "test_user_id"
        val sentTransaction = Transaction(
            id = "sent_1",
            senderId = userId,
            receiverId = "other_user",
            points = 3,
            timestamp = Timestamp.now()
        )
        val receivedTransaction = Transaction(
            id = "received_1",
            senderId = "other_user",
            receiverId = userId,
            points = 5,
            timestamp = Timestamp.now()
        )

        whenever(firestore.collection("transactions")).thenReturn(transactionCollection)
        
        // Mock sent transactions query
        whenever(transactionCollection.whereEqualTo("senderId", userId)).thenReturn(query)
        whenever(query.orderBy("timestamp", Query.Direction.DESCENDING)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.toObjects(Transaction::class.java)).thenReturn(listOf(sentTransaction))

        // Mock received transactions query
        whenever(transactionCollection.whereEqualTo("receiverId", userId)).thenReturn(query)

        // When
        val result = transactionRepository.getTransactionHistory(userId)

        // Then
        assertTrue(result.isSuccess)
        val transactions = result.getOrNull()
        assertNotNull(transactions)
        assertTrue(transactions!!.contains(sentTransaction))
    }

    @Test
    fun `getTransactionHistory should return failure when Firestore query fails`() = runTest {
        // Given
        val userId = "test_user_id"
        val exception = FirebaseFirestoreException("Query failed", FirebaseFirestoreException.Code.PERMISSION_DENIED)

        whenever(firestore.collection("transactions")).thenReturn(transactionCollection)
        whenever(transactionCollection.whereEqualTo("senderId", userId)).thenReturn(query)
        whenever(query.orderBy("timestamp", Query.Direction.DESCENDING)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forException(exception))

        // When
        val result = transactionRepository.getTransactionHistory(userId)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // Tests for new couple features - Point deduction functionality

    @Test
    fun `deductPoints should create negative transaction successfully`() = runTest {
        // Given
        val senderId = "sender_id"
        val receiverId = "receiver_id"
        val points = 3 // Will be converted to -3
        val reason = "Argument about dishes"
        val connectionId = "connection_id"

        whenever(firestore.collection("transactions")).thenReturn(transactionCollection)
        whenever(transactionCollection.document()).thenReturn(transactionDocument)
        whenever(transactionDocument.id).thenReturn("transaction_id")

        whenever(firestore.runTransaction<Transaction>(any())).thenAnswer { invocation ->
            val transactionFunction = invocation.getArgument<com.google.firebase.firestore.Transaction.Function<Transaction>>(0)
            Tasks.forResult(transactionFunction.apply(firestoreTransaction))
        }

        whenever(firestore.collection("users")).thenReturn(usersCollection)
        whenever(usersCollection.document(receiverId)).thenReturn(userDocument)
        whenever(firestoreTransaction.get(userDocument)).thenReturn(documentSnapshot)
        whenever(documentSnapshot.getLong("totalPointsReceived")).thenReturn(10L)
        whenever(firestoreTransaction.set(eq(transactionDocument), any<Transaction>())).thenReturn(firestoreTransaction)
        whenever(firestoreTransaction.update(userDocument, "totalPointsReceived", 7L)).thenReturn(firestoreTransaction) // 10 - 3 = 7

        // When
        val result = transactionRepository.deductPoints(senderId, receiverId, points, reason, connectionId)

        // Then
        assertTrue(result.isSuccess)
        val transaction = result.getOrNull()
        assertNotNull(transaction)
        assertEquals(senderId, transaction?.senderId)
        assertEquals(receiverId, transaction?.receiverId)
        assertEquals(-points, transaction?.points) // Should be negative
        assertEquals(reason, transaction?.message)
        assertEquals(connectionId, transaction?.connectionId)
        assertEquals(TransactionType.DEDUCT, transaction?.type)
    }

    @Test
    fun `deductPoints should allow negative balance`() = runTest {
        // Given
        val senderId = "sender_id"
        val receiverId = "receiver_id"
        val points = 5
        val reason = "Major disagreement"
        val connectionId = "connection_id"

        whenever(firestore.collection("transactions")).thenReturn(transactionCollection)
        whenever(transactionCollection.document()).thenReturn(transactionDocument)
        whenever(transactionDocument.id).thenReturn("transaction_id")

        whenever(firestore.runTransaction<Transaction>(any())).thenAnswer { invocation ->
            val transactionFunction = invocation.getArgument<com.google.firebase.firestore.Transaction.Function<Transaction>>(0)
            Tasks.forResult(transactionFunction.apply(firestoreTransaction))
        }

        whenever(firestore.collection("users")).thenReturn(usersCollection)
        whenever(usersCollection.document(receiverId)).thenReturn(userDocument)
        whenever(firestoreTransaction.get(userDocument)).thenReturn(documentSnapshot)
        whenever(documentSnapshot.getLong("totalPointsReceived")).thenReturn(2L) // Low balance
        whenever(firestoreTransaction.set(eq(transactionDocument), any<Transaction>())).thenReturn(firestoreTransaction)
        whenever(firestoreTransaction.update(userDocument, "totalPointsReceived", -3L)).thenReturn(firestoreTransaction) // 2 - 5 = -3

        // When
        val result = transactionRepository.deductPoints(senderId, receiverId, points, reason, connectionId)

        // Then
        assertTrue(result.isSuccess)
        val transaction = result.getOrNull()
        assertNotNull(transaction)
        assertEquals(-points, transaction?.points)
        assertEquals(TransactionType.DEDUCT, transaction?.type)
    }

    @Test
    fun `givePoints should create positive transaction with GIVE type`() = runTest {
        // Given
        val senderId = "sender_id"
        val receiverId = "receiver_id"
        val points = 4
        val message = "Thanks for helping!"
        val connectionId = "connection_id"

        whenever(firestore.collection("transactions")).thenReturn(transactionCollection)
        whenever(transactionCollection.document()).thenReturn(transactionDocument)
        whenever(transactionDocument.id).thenReturn("transaction_id")

        whenever(firestore.runTransaction<Transaction>(any())).thenAnswer { invocation ->
            val transactionFunction = invocation.getArgument<com.google.firebase.firestore.Transaction.Function<Transaction>>(0)
            Tasks.forResult(transactionFunction.apply(firestoreTransaction))
        }

        whenever(firestore.collection("users")).thenReturn(usersCollection)
        whenever(usersCollection.document(receiverId)).thenReturn(userDocument)
        whenever(firestoreTransaction.get(userDocument)).thenReturn(documentSnapshot)
        whenever(documentSnapshot.getLong("totalPointsReceived")).thenReturn(5L)
        whenever(firestoreTransaction.set(eq(transactionDocument), any<Transaction>())).thenReturn(firestoreTransaction)
        whenever(firestoreTransaction.update(userDocument, "totalPointsReceived", 9L)).thenReturn(firestoreTransaction) // 5 + 4 = 9

        // When
        val result = transactionRepository.givePoints(senderId, receiverId, points, message, connectionId)

        // Then
        assertTrue(result.isSuccess)
        val transaction = result.getOrNull()
        assertNotNull(transaction)
        assertEquals(senderId, transaction?.senderId)
        assertEquals(receiverId, transaction?.receiverId)
        assertEquals(points, transaction?.points) // Should be positive
        assertEquals(message, transaction?.message)
        assertEquals(connectionId, transaction?.connectionId)
        assertEquals(TransactionType.GIVE, transaction?.type)
    }

    @Test
    fun `createTransaction should handle both positive and negative points correctly`() = runTest {
        // Test positive transaction
        val senderId = "sender_id"
        val receiverId = "receiver_id"
        val positivePoints = 3
        val connectionId = "connection_id"

        whenever(firestore.collection("transactions")).thenReturn(transactionCollection)
        whenever(transactionCollection.document()).thenReturn(transactionDocument)
        whenever(transactionDocument.id).thenReturn("transaction_id")

        whenever(firestore.runTransaction<Transaction>(any())).thenAnswer { invocation ->
            val transactionFunction = invocation.getArgument<com.google.firebase.firestore.Transaction.Function<Transaction>>(0)
            Tasks.forResult(transactionFunction.apply(firestoreTransaction))
        }

        whenever(firestore.collection("users")).thenReturn(usersCollection)
        whenever(usersCollection.document(receiverId)).thenReturn(userDocument)
        whenever(firestoreTransaction.get(userDocument)).thenReturn(documentSnapshot)
        whenever(documentSnapshot.getLong("totalPointsReceived")).thenReturn(10L)
        whenever(firestoreTransaction.set(eq(transactionDocument), any<Transaction>())).thenReturn(firestoreTransaction)
        whenever(firestoreTransaction.update(userDocument, "totalPointsReceived", 13L)).thenReturn(firestoreTransaction)

        // When - positive transaction
        val positiveResult = transactionRepository.createTransaction(
            senderId, receiverId, positivePoints, "Good job!", connectionId, TransactionType.GIVE
        )

        // Then
        assertTrue(positiveResult.isSuccess)
        val positiveTransaction = positiveResult.getOrNull()
        assertEquals(positivePoints, positiveTransaction?.points)
        assertEquals(TransactionType.GIVE, positiveTransaction?.type)

        // Reset mocks for negative transaction
        whenever(documentSnapshot.getLong("totalPointsReceived")).thenReturn(10L)
        whenever(firestoreTransaction.update(userDocument, "totalPointsReceived", 8L)).thenReturn(firestoreTransaction)

        // When - negative transaction
        val negativeResult = transactionRepository.createTransaction(
            senderId, receiverId, -2, "Disagreement", connectionId, TransactionType.DEDUCT
        )

        // Then
        assertTrue(negativeResult.isSuccess)
        val negativeTransaction = negativeResult.getOrNull()
        assertEquals(-2, negativeTransaction?.points)
        assertEquals(TransactionType.DEDUCT, negativeTransaction?.type)
    }
}