package com.browniepoints.app.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.browniepoints.app.data.model.Notification
import com.browniepoints.app.data.repository.InAppNotificationRepository
import com.browniepoints.app.data.service.InAppNotificationManager
import com.browniepoints.app.domain.usecase.InAppNotificationUseCase
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class NotificationViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var inAppNotificationRepository: InAppNotificationRepository

    @Mock
    private lateinit var inAppNotificationUseCase: InAppNotificationUseCase

    @Mock
    private lateinit var inAppNotificationManager: InAppNotificationManager

    @Mock
    private lateinit var firebaseAuth: FirebaseAuth

    private lateinit var notificationViewModel: NotificationViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        notificationViewModel = NotificationViewModel(inAppNotificationUseCase, inAppNotificationManager, firebaseAuth)
    }

    @Test
    fun `initial state should be correct`() {
        // When
        val state = notificationViewModel.uiState.value

        // Then
        assertTrue(state.notifications.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }
}