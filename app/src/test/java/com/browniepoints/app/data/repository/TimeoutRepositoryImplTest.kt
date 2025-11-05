package com.browniepoints.app.data.repository

import com.browniepoints.app.data.model.Timeout
import com.browniepoints.app.data.service.TimeoutNotificationService
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
import java.text.SimpleDateFormat
import java.util.*

class TimeoutRepositoryImplTest {

    @Mock
    private lateinit var firestore: FirebaseFirestore

    @Mock
    private lateinit var timeoutNotificationService: TimeoutNotificationService

    @Mock
    private lateinit var timeoutCollection: CollectionReference

    @Mock
    private lateinit var timeoutDocument: DocumentReference

    @Mock
    private lateinit var documentSnapshot: DocumentSnapshot

    @Mock
    private lateinit var querySnapshot: QuerySnapshot

    @Mock
    private lateinit var query: Query

    private lateinit var timeoutRepository: TimeoutRepositoryImpl

    private val testUserId = "test_user_id"
    private val testConnectionId = "test_connection_id"
    private val testTimeoutId = "test_timeout_id"

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        timeoutRepository = TimeoutRepositoryImpl(firestore, timeoutNotificationService)
    }

    @Test
    fun `createTimeout should return success when timeout is created successfully`() = runTest {
        // Given
        val currentDate = getCurrentDateString()
        
        whenever(firestore.collection("timeouts")).thenReturn(timeoutCollection)
        whenever(timeoutCollection.document()).thenReturn(timeoutDocument)
        whenever(timeoutDocument.id).thenReturn(testTimeoutId)
        
        // Mock canRequestTimeout to return true
        whenever(timeoutCollection.whereEqualTo("userId", testUserId)).thenReturn(query)
        whenever(query.whereEqualTo("createdDate", currentDate)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.size()).thenReturn(0) // No timeouts today
        
        whenever(timeoutDocument.set(any<Timeout>())).thenReturn(Tasks.forResult(null))
        whenever(timeoutNotificationService.sendTimeoutRequestNotification(any())).thenReturn(Result.success(Unit))

        // When
        val result = timeoutRepository.createTimeout(testUserId, testConnectionId)

        // Then
        assertTrue(result.isSuccess)
        val timeout = result.getOrNull()
        assertNotNull(timeout)
        assertEquals(testTimeoutId, timeout?.id)
        assertEquals(testUserId, timeout?.userId)
        assertEquals(testConnectionId, timeout?.connectionId)
        assertTrue(timeout?.isActive == true)
        assertEquals(Timeout.DEFAULT_DURATION_MS, timeout?.duration)
        
        verify(timeoutDocument).set(any<Timeout>())
        verify(timeoutNotificationService).sendTimeoutRequestNotification(any())
    }

    @Test
    fun `createTimeout should return failure when user has already requested timeout today`() = runTest {
        // Given
        val currentDate = getCurrentDateString()
        
        whenever(firestore.collection("timeouts")).thenReturn(timeoutCollection)
        whenever(timeoutCollection.whereEqualTo("userId", testUserId)).thenReturn(query)
        whenever(query.whereEqualTo("createdDate", currentDate)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.size()).thenReturn(1) // Already has timeout today

        // When
        val result = timeoutRepository.createTimeout(testUserId, testConnectionId)

        // Then
        assertTrue(result.isFailure)
        assertEquals("User has already requested timeout today", result.exceptionOrNull()?.message)
        
        verify(timeoutDocument, never()).set(any<Timeout>())
        verify(timeoutNotificationService, never()).sendTimeoutRequestNotification(any())
    }

    @Test
    fun `createTimeout should return failure when Firestore operation fails`() = runTest {
        // Given
        val currentDate = getCurrentDateString()
        val exception = FirebaseFirestoreException("Firestore error", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        
        whenever(firestore.collection("timeouts")).thenReturn(timeoutCollection)
        whenever(timeoutCollection.whereEqualTo("userId", testUserId)).thenReturn(query)
        whenever(query.whereEqualTo("createdDate", currentDate)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forException(exception))

        // When
        val result = timeoutRepository.createTimeout(testUserId, testConnectionId)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `canRequestTimeout should return true when user has not requested timeout today`() = runTest {
        // Given
        val currentDate = getCurrentDateString()
        
        whenever(firestore.collection("timeouts")).thenReturn(timeoutCollection)
        whenever(timeoutCollection.whereEqualTo("userId", testUserId)).thenReturn(query)
        whenever(query.whereEqualTo("createdDate", currentDate)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.size()).thenReturn(0)

        // When
        val result = timeoutRepository.canRequestTimeout(testUserId)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `canRequestTimeout should return false when user has already requested timeout today`() = runTest {
        // Given
        val currentDate = getCurrentDateString()
        
        whenever(firestore.collection("timeouts")).thenReturn(timeoutCollection)
        whenever(timeoutCollection.whereEqualTo("userId", testUserId)).thenReturn(query)
        whenever(query.whereEqualTo("createdDate", currentDate)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.size()).thenReturn(1)

        // When
        val result = timeoutRepository.canRequestTimeout(testUserId)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == false)
    }

    @Test
    fun `getActiveTimeout should return active timeout when one exists`() = runTest {
        // Given
        val activeTimeout = Timeout(
            id = testTimeoutId,
            userId = testUserId,
            connectionId = testConnectionId,
            startTime = Timestamp.now(),
            isActive = true
        )
        
        whenever(firestore.collection("timeouts")).thenReturn(timeoutCollection)
        whenever(timeoutCollection.whereEqualTo("connectionId", testConnectionId)).thenReturn(query)
        whenever(query.whereEqualTo("isActive", true)).thenReturn(query)
        whenever(query.orderBy("startTime", Query.Direction.DESCENDING)).thenReturn(query)
        whenever(query.limit(1)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.documents).thenReturn(listOf(documentSnapshot))
        whenever(documentSnapshot.toObject(Timeout::class.java)).thenReturn(activeTimeout)

        // When
        val result = timeoutRepository.getActiveTimeout(testConnectionId)

        // Then
        assertTrue(result.isSuccess)
        val timeout = result.getOrNull()
        assertNotNull(timeout)
        assertEquals(testTimeoutId, timeout?.id)
        assertEquals(testConnectionId, timeout?.connectionId)
        assertTrue(timeout?.isActive == true)
    }

    @Test
    fun `getActiveTimeout should return null when no active timeout exists`() = runTest {
        // Given
        whenever(firestore.collection("timeouts")).thenReturn(timeoutCollection)
        whenever(timeoutCollection.whereEqualTo("connectionId", testConnectionId)).thenReturn(query)
        whenever(query.whereEqualTo("isActive", true)).thenReturn(query)
        whenever(query.orderBy("startTime", Query.Direction.DESCENDING)).thenReturn(query)
        whenever(query.limit(1)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.documents).thenReturn(emptyList())

        // When
        val result = timeoutRepository.getActiveTimeout(testConnectionId)

        // Then
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `getActiveTimeout should expire timeout and return null when timeout has expired`() = runTest {
        // Given
        val expiredTimeout = Timeout(
            id = testTimeoutId,
            userId = testUserId,
            connectionId = testConnectionId,
            startTime = Timestamp(Date(System.currentTimeMillis() - (31 * 60 * 1000))), // 31 minutes ago
            isActive = true
        )
        
        whenever(firestore.collection("timeouts")).thenReturn(timeoutCollection)
        whenever(timeoutCollection.whereEqualTo("connectionId", testConnectionId)).thenReturn(query)
        whenever(query.whereEqualTo("isActive", true)).thenReturn(query)
        whenever(query.orderBy("startTime", Query.Direction.DESCENDING)).thenReturn(query)
        whenever(query.limit(1)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.documents).thenReturn(listOf(documentSnapshot))
        whenever(documentSnapshot.toObject(Timeout::class.java)).thenReturn(expiredTimeout)
        
        whenever(timeoutCollection.document(testTimeoutId)).thenReturn(timeoutDocument)
        whenever(timeoutDocument.update("isActive", false)).thenReturn(Tasks.forResult(null))

        // When
        val result = timeoutRepository.getActiveTimeout(testConnectionId)

        // Then
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        verify(timeoutDocument).update("isActive", false)
    }

    @Test
    fun `expireTimeout should mark timeout as inactive`() = runTest {
        // Given
        whenever(firestore.collection("timeouts")).thenReturn(timeoutCollection)
        whenever(timeoutCollection.document(testTimeoutId)).thenReturn(timeoutDocument)
        whenever(timeoutDocument.update("isActive", false)).thenReturn(Tasks.forResult(null))

        // When
        val result = timeoutRepository.expireTimeout(testTimeoutId)

        // Then
        assertTrue(result.isSuccess)
        verify(timeoutDocument).update("isActive", false)
    }

    @Test
    fun `expireTimeout should return failure when Firestore operation fails`() = runTest {
        // Given
        val exception = FirebaseFirestoreException("Update failed", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        
        whenever(firestore.collection("timeouts")).thenReturn(timeoutCollection)
        whenever(timeoutCollection.document(testTimeoutId)).thenReturn(timeoutDocument)
        whenever(timeoutDocument.update("isActive", false)).thenReturn(Tasks.forException(exception))

        // When
        val result = timeoutRepository.expireTimeout(testTimeoutId)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `getTimeoutHistory should return list of timeouts for user`() = runTest {
        // Given
        val timeout1 = Timeout(id = "timeout1", userId = testUserId, connectionId = testConnectionId)
        val timeout2 = Timeout(id = "timeout2", userId = testUserId, connectionId = testConnectionId)
        val timeouts = listOf(timeout1, timeout2)
        
        whenever(firestore.collection("timeouts")).thenReturn(timeoutCollection)
        whenever(timeoutCollection.whereEqualTo("userId", testUserId)).thenReturn(query)
        whenever(query.orderBy("startTime", Query.Direction.DESCENDING)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.documents).thenReturn(listOf(documentSnapshot, documentSnapshot))
        whenever(documentSnapshot.toObject(Timeout::class.java)).thenReturn(timeout1, timeout2)

        // When
        val result = timeoutRepository.getTimeoutHistory(testUserId)

        // Then
        assertTrue(result.isSuccess)
        val history = result.getOrNull()
        assertNotNull(history)
        assertEquals(2, history?.size)
        assertTrue(history?.contains(timeout1) == true)
        assertTrue(history?.contains(timeout2) == true)
    }

    @Test
    fun `getTodayTimeoutCount should return correct count for today`() = runTest {
        // Given
        val currentDate = getCurrentDateString()
        
        whenever(firestore.collection("timeouts")).thenReturn(timeoutCollection)
        whenever(timeoutCollection.whereEqualTo("userId", testUserId)).thenReturn(query)
        whenever(query.whereEqualTo("createdDate", currentDate)).thenReturn(query)
        whenever(query.get()).thenReturn(Tasks.forResult(querySnapshot))
        whenever(querySnapshot.size()).thenReturn(1)

        // When
        val result = timeoutRepository.getTodayTimeoutCount(testUserId)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
    }

    private fun getCurrentDateString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}