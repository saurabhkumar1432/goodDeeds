package com.browniepoints.app.data.repository

import com.browniepoints.app.data.model.Connection
import com.browniepoints.app.data.model.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class ConnectionRepositoryImplTest {

    @Mock
    private lateinit var firestore: FirebaseFirestore

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var collectionReference: CollectionReference

    @Mock
    private lateinit var documentReference: DocumentReference

    @Mock
    private lateinit var documentSnapshot: DocumentSnapshot

    @Mock
    private lateinit var querySnapshot: QuerySnapshot

    @Mock
    private lateinit var query: Query

    private lateinit var connectionRepository: ConnectionRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        connectionRepository = ConnectionRepositoryImpl(firestore)
    }

    @Test
    fun `validateMatchingCode should return user ID when valid code exists`() = runTest {
        // Given
        val matchingCode = "ABC123"
        val userId = "test_user_id"
        val user = User(uid = userId, matchingCode = matchingCode)

        whenever(userRepository.findUserByMatchingCode(matchingCode))
            .thenReturn(Result.success(user))

        // When
        val result = connectionRepository.validateMatchingCode(matchingCode)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(userId, result.getOrNull())
        verify(userRepository).findUserByMatchingCode(matchingCode)
    }

    @Test
    fun `validateMatchingCode should return null when code doesn't exist`() = runTest {
        // Given
        val matchingCode = "INVALID"

        whenever(userRepository.findUserByMatchingCode(matchingCode))
            .thenReturn(Result.success(null))

        // When
        val result = connectionRepository.validateMatchingCode(matchingCode)

        // Then
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `validateMatchingCode should return failure when repository fails`() = runTest {
        // Given
        val matchingCode = "ABC123"
        val exception = Exception("Repository error")

        whenever(userRepository.findUserByMatchingCode(matchingCode))
            .thenReturn(Result.failure(exception))

        // When
        val result = connectionRepository.validateMatchingCode(matchingCode)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `createConnection should create connection and update users successfully`() = runTest {
        // Given
        val user1Id = "user1_id"
        val user2Id = "user2_id"
        val connectionId = "${minOf(user1Id, user2Id)}_${maxOf(user1Id, user2Id)}"
        val connection = Connection(
            id = connectionId,
            user1Id = user1Id,
            user2Id = user2Id,
            createdAt = Timestamp.now()
        )

        val user1 = User(uid = user1Id, displayName = "User 1")
        val user2 = User(uid = user2Id, displayName = "User 2")

        whenever(firestore.collection("connections")).thenReturn(collectionReference)
        whenever(collectionReference.document(connectionId)).thenReturn(documentReference)
        whenever(documentReference.set(any<Connection>())).thenReturn(Tasks.forResult(null))

        whenever(userRepository.getUser(user1Id)).thenReturn(Result.success(user1))
        whenever(userRepository.getUser(user2Id)).thenReturn(Result.success(user2))
        whenever(userRepository.updateUser(any())).thenReturn(Result.success(Unit))

        // When
        val result = connectionRepository.createConnection(user1Id, user2Id)

        // Then
        assertTrue(result.isSuccess)
        val createdConnection = result.getOrNull()
        assertNotNull(createdConnection)
        assertEquals(connectionId, createdConnection?.id)
        assertEquals(user1Id, createdConnection?.user1Id)
        assertEquals(user2Id, createdConnection?.user2Id)

        verify(documentReference).set(any<Connection>())
        verify(userRepository, times(2)).updateUser(any())
    }

    @Test
    fun `createConnection should return failure when Firestore operation fails`() = runTest {
        // Given
        val user1Id = "user1_id"
        val user2Id = "user2_id"
        val connectionId = "${minOf(user1Id, user2Id)}_${maxOf(user1Id, user2Id)}"
        val exception = Exception("Firestore error")

        whenever(firestore.collection("connections")).thenReturn(collectionReference)
        whenever(collectionReference.document(connectionId)).thenReturn(documentReference)
        whenever(documentReference.set(any<Connection>())).thenReturn(Tasks.forException(exception))

        // When
        val result = connectionRepository.createConnection(user1Id, user2Id)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `createConnection should return failure when user retrieval fails`() = runTest {
        // Given
        val user1Id = "user1_id"
        val user2Id = "user2_id"
        val connectionId = "${minOf(user1Id, user2Id)}_${maxOf(user1Id, user2Id)}"
        val exception = Exception("User not found")

        whenever(firestore.collection("connections")).thenReturn(collectionReference)
        whenever(collectionReference.document(connectionId)).thenReturn(documentReference)
        whenever(documentReference.set(any<Connection>())).thenReturn(Tasks.forResult(null))

        whenever(userRepository.getUser(user1Id)).thenReturn(Result.failure(exception))

        // When
        val result = connectionRepository.createConnection(user1Id, user2Id)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}