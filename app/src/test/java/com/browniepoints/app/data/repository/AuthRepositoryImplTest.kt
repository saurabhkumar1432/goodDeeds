package com.browniepoints.app.data.repository

import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.service.GoogleSignInService
import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class AuthRepositoryImplTest {

    @Mock
    private lateinit var firebaseAuth: FirebaseAuth

    @Mock
    private lateinit var firestore: FirebaseFirestore

    @Mock
    private lateinit var googleSignInService: GoogleSignInService

    @Mock
    private lateinit var firebaseUser: FirebaseUser

    @Mock
    private lateinit var authResult: AuthResult

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

    private lateinit var authRepository: AuthRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        authRepository = AuthRepositoryImpl(firebaseAuth, firestore, googleSignInService)
    }

    @Test
    fun `signInWithGoogle should return success when authentication succeeds for existing user`() = runTest {
        // Given
        val idToken = "test_id_token"
        val userId = "test_user_id"
        val existingUser = User(
            uid = userId,
            displayName = "Test User",
            email = "test@example.com",
            matchingCode = "ABC123",
            createdAt = Timestamp.now()
        )

        // Mock Firebase Auth
        whenever(firebaseUser.uid).thenReturn(userId)
        whenever(authResult.user).thenReturn(firebaseUser)
        whenever(firebaseAuth.signInWithCredential(any())).thenReturn(Tasks.forResult(authResult))

        // Mock Firestore - user exists
        whenever(firestore.collection("users")).thenReturn(collectionReference)
        whenever(collectionReference.document(userId)).thenReturn(documentReference)
        whenever(documentReference.get()).thenReturn(Tasks.forResult(documentSnapshot))
        whenever(documentSnapshot.exists()).thenReturn(true)
        whenever(documentSnapshot.toObject(User::class.java)).thenReturn(existingUser)

        // When
        val result = authRepository.signInWithGoogle(idToken)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(existingUser, result.getOrNull())
        verify(firebaseAuth).signInWithCredential(any())
        verify(documentReference).get()
        verify(documentSnapshot).toObject(User::class.java)
    }

    @Test
    fun `signInWithGoogle should create new user when user doesn't exist`() = runTest {
        // Given
        val idToken = "test_id_token"
        val userId = "test_user_id"
        val displayName = "Test User"
        val email = "test@example.com"

        // Mock Firebase Auth
        whenever(firebaseUser.uid).thenReturn(userId)
        whenever(firebaseUser.displayName).thenReturn(displayName)
        whenever(firebaseUser.email).thenReturn(email)
        whenever(firebaseUser.photoUrl).thenReturn(null)
        whenever(authResult.user).thenReturn(firebaseUser)
        whenever(firebaseAuth.signInWithCredential(any())).thenReturn(Tasks.forResult(authResult))

        // Mock Firestore - user doesn't exist
        whenever(firestore.collection("users")).thenReturn(collectionReference)
        whenever(collectionReference.document(userId)).thenReturn(documentReference)
        whenever(documentReference.get()).thenReturn(Tasks.forResult(documentSnapshot))
        whenever(documentSnapshot.exists()).thenReturn(false)

        // Mock matching code generation
        whenever(collectionReference.whereEqualTo(eq("matchingCode"), any())).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.isEmpty).thenReturn(true)

        // Mock user creation
        whenever(documentReference.set(any<User>())).thenReturn(Tasks.forResult(null))

        // When
        val result = authRepository.signInWithGoogle(idToken)

        // Then
        assertTrue(result.isSuccess)
        val createdUser = result.getOrNull()
        assertNotNull(createdUser)
        assertEquals(userId, createdUser?.uid)
        assertEquals(displayName, createdUser?.displayName)
        assertEquals(email, createdUser?.email)
        assertEquals(6, createdUser?.matchingCode?.length)
        verify(documentReference).set(any<User>())
    }

    @Test
    fun `signInWithGoogle should return failure when authentication fails`() = runTest {
        // Given
        val idToken = "invalid_token"
        val exception = Exception("Authentication failed")
        whenever(firebaseAuth.signInWithCredential(any())).thenReturn(Tasks.forException(exception))

        // When
        val result = authRepository.signInWithGoogle(idToken)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `signOut should return success when sign out succeeds`() = runTest {
        // Given
        doNothing().whenever(firebaseAuth).signOut()

        // When
        val result = authRepository.signOut()

        // Then
        assertTrue(result.isSuccess)
        verify(firebaseAuth).signOut()
    }

    @Test
    fun `getCurrentUser should return current Firebase user`() {
        // Given
        whenever(firebaseAuth.currentUser).thenReturn(firebaseUser)

        // When
        val result = authRepository.getCurrentUser()

        // Then
        assertEquals(firebaseUser, result)
    }

    @Test
    fun `getCurrentUser should return null when no user is signed in`() {
        // Given
        whenever(firebaseAuth.currentUser).thenReturn(null)

        // When
        val result = authRepository.getCurrentUser()

        // Then
        assertNull(result)
    }
}