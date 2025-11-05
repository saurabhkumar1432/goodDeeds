package com.browniepoints.app.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.browniepoints.app.data.model.Timeout
import com.browniepoints.app.data.service.ErrorHandlerService
import com.browniepoints.app.data.service.NetworkMonitorService
import com.browniepoints.app.data.service.OfflineSyncManager
import com.browniepoints.app.data.service.SyncStatus
import com.browniepoints.app.data.service.TimeoutManager
import com.browniepoints.app.domain.usecase.TimeoutException
import com.browniepoints.app.domain.usecase.TimeoutUseCase
import com.browniepoints.app.presentation.ui.common.UiState
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class TimeoutViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var timeoutUseCase: TimeoutUseCase

    @Mock
    private lateinit var timeoutManager: TimeoutManager

    @Mock
    private lateinit var networkMonitorService: NetworkMonitorService

    @Mock
    private lateinit var offlineSyncManager: OfflineSyncManager

    @Mock
    private lateinit var errorHandlerService: ErrorHandlerService

    private lateinit var timeoutViewModel: TimeoutViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val testUserId = "test_user_id"
    private val testConnectionId = "test_connection_id"

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // Setup default mock behaviors
        whenever(networkMonitorService.isOnline).thenReturn(flowOf(true))
        whenever(offlineSyncManager.syncStatus).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(SyncStatus.SYNCED))
        whenever(timeoutManager.observeActiveTimeoutWithMonitoring(any())).thenReturn(flowOf(null))
        whenever(timeoutManager.observeTimeoutStatus(any())).thenReturn(flowOf(false))
        whenever(timeoutManager.observeRemainingTime(any())).thenReturn(flowOf(0L))
        whenever(timeoutManager.observeTransactionsDisabled(any())).thenReturn(flowOf(false))

        timeoutViewModel = TimeoutViewModel(
            timeoutUseCase,
            timeoutManager,
            networkMonitorService,
            offlineSyncManager,
            errorHandlerService
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setConnectionId should update connection ID and check eligibility`() = runTest {
        // Given
        whenever(timeoutUseCase.canRequestTimeout(testUserId)).thenReturn(Result.success(true))

        // When
        timeoutViewModel.setConnectionId(testConnectionId, testUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        timeoutViewModel.currentConnectionId.test {
            assertEquals(testConnectionId, awaitItem())
        }

        timeoutViewModel.canRequestTimeout.test {
            assertTrue(awaitItem())
        }

        verify(timeoutUseCase).canRequestTimeout(testUserId)
    }

    @Test
    fun `showTimeoutRequestDialog should update dialog state to true`() = runTest {
        // When
        timeoutViewModel.showTimeoutRequestDialog()

        // Then
        timeoutViewModel.showTimeoutRequestDialog.test {
            assertTrue(awaitItem())
        }
    }

    @Test
    fun `hideTimeoutRequestDialog should update dialog state to false`() = runTest {
        // Given
        timeoutViewModel.showTimeoutRequestDialog()

        // When
        timeoutViewModel.hideTimeoutRequestDialog()

        // Then
        timeoutViewModel.showTimeoutRequestDialog.test {
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `requestTimeout should return success when timeout is created successfully`() = runTest {
        // Given
        val expectedTimeout = Timeout(
            id = "timeout_id",
            userId = testUserId,
            connectionId = testConnectionId,
            startTime = Timestamp.now(),
            isActive = true
        )

        timeoutViewModel.setConnectionId(testConnectionId, testUserId)
        whenever(timeoutUseCase.requestTimeout(testUserId, testConnectionId)).thenReturn(Result.success(expectedTimeout))
        whenever(timeoutUseCase.canRequestTimeout(testUserId)).thenReturn(Result.success(false)) // After requesting
        whenever(timeoutManager.startTimeoutMonitoring(expectedTimeout)).thenReturn(Unit)

        // When
        timeoutViewModel.requestTimeout(testUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        timeoutViewModel.timeoutRequestState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success)
            assertEquals(expectedTimeout, (state as UiState.Success).data)
        }

        timeoutViewModel.showTimeoutRequestDialog.test {
            assertFalse(awaitItem()) // Dialog should be hidden after success
        }

        timeoutViewModel.canRequestTimeout.test {
            assertFalse(awaitItem()) // Should be false after requesting
        }

        verify(timeoutUseCase).requestTimeout(testUserId, testConnectionId)
        verify(timeoutManager).startTimeoutMonitoring(expectedTimeout)
    }

    @Test
    fun `requestTimeout should return error when daily limit is exceeded`() = runTest {
        // Given
        val dailyLimitException = TimeoutException.DailyLimitExceeded("Daily limit exceeded")
        timeoutViewModel.setConnectionId(testConnectionId, testUserId)
        whenever(timeoutUseCase.requestTimeout(testUserId, testConnectionId)).thenReturn(Result.failure(dailyLimitException))

        // When
        timeoutViewModel.requestTimeout(testUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        timeoutViewModel.timeoutRequestState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Error)
            // Verify it's an error state (specific error content may vary)
        }

        timeoutViewModel.canRequestTimeout.test {
            assertFalse(awaitItem()) // Should be false when daily limit exceeded
        }

        verify(timeoutUseCase).requestTimeout(testUserId, testConnectionId)
        verify(timeoutManager, never()).startTimeoutMonitoring(any())
    }

    @Test
    fun `requestTimeout should return error when timeout is already active`() = runTest {
        // Given
        val alreadyActiveException = TimeoutException.TimeoutAlreadyActive("Timeout already active")
        timeoutViewModel.setConnectionId(testConnectionId, testUserId)
        whenever(timeoutUseCase.requestTimeout(testUserId, testConnectionId)).thenReturn(Result.failure(alreadyActiveException))

        // When
        timeoutViewModel.requestTimeout(testUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        timeoutViewModel.timeoutRequestState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Error)
            // Verify it's an error state (specific error content may vary)
        }

        timeoutViewModel.showTimeoutRequestDialog.test {
            assertFalse(awaitItem()) // Dialog should be hidden when timeout already active
        }

        verify(timeoutUseCase).requestTimeout(testUserId, testConnectionId)
    }

    @Test
    fun `requestTimeout should handle error when no connection ID is set`() = runTest {
        // When
        timeoutViewModel.requestTimeout(testUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        timeoutViewModel.timeoutRequestState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Error)
            // Verify it's an error state (specific error content may vary)
        }

        verify(timeoutUseCase, never()).requestTimeout(any(), any())
    }

    @Test
    fun `expireCurrentTimeout should expire active timeout`() = runTest {
        // Given
        val activeTimeout = Timeout(
            id = "timeout_id",
            userId = testUserId,
            connectionId = testConnectionId,
            startTime = Timestamp.now(),
            isActive = true
        )

        whenever(timeoutManager.observeActiveTimeoutWithMonitoring(testConnectionId)).thenReturn(flowOf(activeTimeout))
        whenever(timeoutManager.expireTimeout("timeout_id", testConnectionId)).thenReturn(Result.success(Unit))

        timeoutViewModel.setConnectionId(testConnectionId, testUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        timeoutViewModel.expireCurrentTimeout()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(timeoutManager).expireTimeout("timeout_id", testConnectionId)
    }

    @Test
    fun `expireCurrentTimeout should do nothing when no active timeout`() = runTest {
        // Given
        timeoutViewModel.setConnectionId(testConnectionId, testUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        timeoutViewModel.expireCurrentTimeout()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(timeoutManager, never()).expireTimeout(any(), any())
    }

    @Test
    fun `getTimeoutHistory should return timeout history for user`() = runTest {
        // Given
        val timeout1 = Timeout(id = "timeout1", userId = testUserId, connectionId = testConnectionId)
        val timeout2 = Timeout(id = "timeout2", userId = testUserId, connectionId = testConnectionId)
        val expectedHistory = listOf(timeout1, timeout2)

        whenever(timeoutUseCase.getTimeoutHistory(testUserId)).thenReturn(Result.success(expectedHistory))

        // When
        timeoutViewModel.getTimeoutHistory(testUserId).test {
            // Then
            assertTrue(awaitItem() is UiState.Loading)
            val successState = awaitItem()
            assertTrue(successState is UiState.Success)
            assertEquals(expectedHistory, (successState as UiState.Success).data)
            awaitComplete()
        }

        verify(timeoutUseCase).getTimeoutHistory(testUserId)
    }

    @Test
    fun `getTimeoutHistory should return error when use case fails`() = runTest {
        // Given
        val historyException = Exception("Failed to get history")
        whenever(timeoutUseCase.getTimeoutHistory(testUserId)).thenReturn(Result.failure(historyException))

        // When
        timeoutViewModel.getTimeoutHistory(testUserId).test {
            // Then
            assertTrue(awaitItem() is UiState.Loading)
            val errorState = awaitItem()
            assertTrue(errorState is UiState.Error)
            // Verify it's an error state (specific error content may vary)
            awaitComplete()
        }
    }

    @Test
    fun `clearTimeoutRequestState should reset state to idle`() = runTest {
        // Given
        timeoutViewModel.setConnectionId(testConnectionId, testUserId)
        val timeout = Timeout(id = "timeout_id", userId = testUserId, connectionId = testConnectionId)
        whenever(timeoutUseCase.requestTimeout(testUserId, testConnectionId)).thenReturn(Result.success(timeout))
        whenever(timeoutManager.startTimeoutMonitoring(timeout)).thenReturn(Unit)

        timeoutViewModel.requestTimeout(testUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        timeoutViewModel.clearTimeoutRequestState()

        // Then
        timeoutViewModel.timeoutRequestState.test {
            assertTrue(awaitItem() is UiState.Idle)
        }
    }

    @Test
    fun `refreshTimeoutStatus should check eligibility again`() = runTest {
        // Given
        timeoutViewModel.setConnectionId(testConnectionId, testUserId)
        whenever(timeoutUseCase.canRequestTimeout(testUserId)).thenReturn(Result.success(true))

        // When
        timeoutViewModel.refreshTimeoutStatus(testUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(timeoutUseCase, times(2)).canRequestTimeout(testUserId) // Once in setConnectionId, once in refresh
    }

    @Test
    fun `getTimeoutErrorMessage should return appropriate messages for different errors`() {
        // Test daily limit exceeded
        val dailyLimitError = TimeoutException.DailyLimitExceeded("Daily limit exceeded")
        assertEquals(
            "You have already used your daily timeout allowance. Try again tomorrow.",
            timeoutViewModel.getTimeoutErrorMessage(dailyLimitError)
        )

        // Test timeout already active
        val alreadyActiveError = TimeoutException.TimeoutAlreadyActive("Already active")
        assertEquals(
            "A timeout is already active for this connection.",
            timeoutViewModel.getTimeoutErrorMessage(alreadyActiveError)
        )

        // Test invalid request
        val invalidRequestError = TimeoutException.InvalidRequest("Invalid request")
        assertEquals(
            "Invalid timeout request: Invalid request",
            timeoutViewModel.getTimeoutErrorMessage(invalidRequestError)
        )

        // Test generic error
        val genericError = Exception("Generic error")
        assertEquals(
            "Failed to request timeout. Please try again.",
            timeoutViewModel.getTimeoutErrorMessage(genericError)
        )
    }

    @Test
    fun `activeTimeout flow should emit timeout from manager`() = runTest {
        // Given
        val activeTimeout = Timeout(
            id = "timeout_id",
            userId = testUserId,
            connectionId = testConnectionId,
            startTime = Timestamp.now(),
            isActive = true
        )

        whenever(timeoutManager.observeActiveTimeoutWithMonitoring(testConnectionId)).thenReturn(flowOf(activeTimeout))

        // When
        timeoutViewModel.setConnectionId(testConnectionId, testUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        timeoutViewModel.activeTimeout.test {
            assertEquals(activeTimeout, awaitItem())
        }
    }

    @Test
    fun `isTimeoutActive flow should emit status from manager`() = runTest {
        // Given
        whenever(timeoutManager.observeTimeoutStatus(testConnectionId)).thenReturn(flowOf(true))

        // When
        timeoutViewModel.setConnectionId(testConnectionId, testUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        timeoutViewModel.isTimeoutActive.test {
            assertTrue(awaitItem())
        }
    }

    @Test
    fun `remainingTimeMs flow should emit remaining time from manager`() = runTest {
        // Given
        val remainingTime = 15 * 60 * 1000L // 15 minutes
        whenever(timeoutManager.observeRemainingTime(testConnectionId)).thenReturn(flowOf(remainingTime))

        // When
        timeoutViewModel.setConnectionId(testConnectionId, testUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        timeoutViewModel.remainingTimeMs.test {
            assertEquals(remainingTime, awaitItem())
        }
    }

    @Test
    fun `transactionsDisabled flow should emit disabled status from manager`() = runTest {
        // Given
        whenever(timeoutManager.observeTransactionsDisabled(testConnectionId)).thenReturn(flowOf(true))

        // When
        timeoutViewModel.setConnectionId(testConnectionId, testUserId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        timeoutViewModel.transactionsDisabled.test {
            assertTrue(awaitItem())
        }
    }
}