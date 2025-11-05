package com.browniepoints.app.data.repository

import com.browniepoints.app.data.error.AppError
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

class UserRepositoryImplTest {

    @Mock
    private lateinit var firestore: FirebaseFirestore

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

    private lateinit var userRepository: UserRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        userRepository = UserRepositoryImpl(firestore)
    }

    @Test
    fun `createUser should return success when user is created successfully`() = runTest {
        // Given
        val user = User(
            uid = "test_user_id",
            displayName = "Test User",
            email = "test@example.com",
            matchingCode = "ABC123",
            createdAt = Timestamp.now()
        )

        whenever(firestore.collection("users")).thenReturn(collectionReference)
        whenever(collectionReference.document(user.uid)).thenReturn(documentReference)
        whenever(documentReference.set(user)).thenReturn(Tasks.forResult(null))

        // When
        val result = userRepository.createUser(user)

        // Then
        assertTrue(result.isSuccess)
        verify(documentReference).set(user)
    }

    @Test
    fun `createUser should return failure when creation fails`() = runTest {
        // Given
        val user = User(uid = "test_user_id")
        val exception = Exception("Creation failed")

        whenever(firestore.collection("users")).thenReturn(collectionReference)
        whenever(collectionReference.document(user.uid)).thenReturn(documentReference)
        whenever(documentReference.set(user)).thenReturn(Tasks.forException(exception))

        // When
        val result = userRepository.createUser(user)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `getUser should return user when user exists`() = runTest {
        // Given
        val userId = "test_user_id"
        val user = User(
            uid = userId,
            displayName = "Test User",
            email = "test@example.com",
            matchingCode = "ABC123"
        )

        whenever(firestore.collection("users")).thenReturn(collectionReference)
        whenever(collectionReference.document(userId)).thenReturn(documentReference)
        whenever(documentReference.get()).thenReturn(Tasks.forResult(documentSnapshot))
        whenever(documentSnapshot.exists()).thenReturn(true)
        whenever(documentSnapshot.toObject(User::class.java)).thenReturn(user)

        // When
        val result = userRepository.getUser(userId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(user, result.getOrNull())
    }

    @Test
    fun `getUser should return null when user doesn't exist`() = runTest {
        // Given
        val userId = "nonexistent_user_id"

        whenever(firestore.collection("users")).thenReturn(collectionReference)
        whenever(collectionReference.document(userId)).thenReturn(documentReference)
        whenever(documentReference.get()).thenReturn(Tasks.forResult(documentSnapshot))
        whenever(documentSnapshot.exists()).thenReturn(false)

        // When
        val result = userRepository.getUser(userId)

        // Then
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `updateUser should return success when user is updated successfully`() = runTest {
        // Given
        val user = User(
            uid = "test_user_id",
            displayName = "Updated User",
            email = "updated@example.com",
            matchingCode = "XYZ789"
        )

        whenever(firestore.collection("users")).thenReturn(collectionReference)
        whenever(collectionReference.document(user.uid)).thenReturn(documentReference)
        whenever(documentReference.set(user)).thenReturn(Tasks.forResult(null))

        // When
        val result = userRepository.updateUser(user)

        // Then
        assertTrue(result.isSuccess)
        verify(documentReference).set(user)
    }

    @Test
    fun `generateMatchingCode should return unique 6-character code`() = runTest {
        // Given
        whenever(firestore.collection("users")).thenReturn(collectionReference)
        whenever(collectionReference.whereEqualTo(eq("matchingCode"), any())).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.isEmpty).thenReturn(true)

        // When
        val result = userRepository.generateMatchingCode()

        // Then
        assertEquals(6, result.length)
        assertTrue(result.all { it.isLetterOrDigit() })
    }

    @Test
    fun `findUserByMatchingCode should return user when valid code exists`() = runTest {
        // Given
        val matchingCode = "ABC123"
        val user = User(
            uid = "test_user_id",
            displayName = "Test User",
            matchingCode = matchingCode
        )

        whenever(firestore.collection("users")).thenReturn(collectionReference)
        whenever(collectionReference.whereEqualTo("matchingCode", matchingCode)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.isEmpty).thenReturn(false)
        whenever(querySnapshot.documents).thenReturn(listOf(documentSnapshot))
        whenever(documentSnapshot.toObject(User::class.java)).thenReturn(user)

        // When
        val result = userRepository.findUserByMatchingCode(matchingCode)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(user, result.getOrNull())
    }

    @Test
    fun `findUserByMatchingCode should return null when code doesn't exist`() = runTest {
        // Given
        val matchingCode = "NONEXISTENT"

        whenever(firestore.collection("users")).thenReturn(collectionReference)
        whenever(collectionReference.whereEqualTo("matchingCode", matchingCode)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.isEmpty).thenReturn(true)

        // When
        val result = userRepository.findUserByMatchingCode(matchingCode)

        // Then
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `findUserByMatchingCode should return failure for invalid code format`() = runTest {
        // Given
        val invalidCode = "ABC" // Too short

        // When
        val result = userRepository.findUserByMatchingCode(invalidCode)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.InvalidMatchingCodeError)
    }

    @Test
    fun `findUserByMatchingCode should return failure for non-alphanumeric code`() = runTest {
        // Given
        val invalidCode = "ABC@#$" // Contains special characters

        // When
        val result = userRepository.findUserByMatchingCode(invalidCode)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.InvalidMatchingCodeError)
    }
}