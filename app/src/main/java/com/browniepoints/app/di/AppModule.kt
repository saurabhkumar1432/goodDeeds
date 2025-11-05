package com.browniepoints.app.di

import android.content.Context
import com.browniepoints.app.data.repository.AuthRepository
import com.browniepoints.app.data.repository.AuthRepositoryImpl
import com.browniepoints.app.data.repository.ConnectionRepository
import com.browniepoints.app.data.repository.ConnectionRepositoryImpl
import com.browniepoints.app.data.repository.InAppNotificationRepository
import com.browniepoints.app.data.repository.InAppNotificationRepositoryImpl
import com.browniepoints.app.data.repository.NotificationRepository
import com.browniepoints.app.data.repository.NotificationRepositoryImpl
import com.browniepoints.app.data.repository.TransactionRepository
import com.browniepoints.app.data.repository.TransactionRepositoryImpl
import com.browniepoints.app.data.repository.TimeoutRepository
import com.browniepoints.app.data.repository.TimeoutRepositoryImpl
import com.browniepoints.app.data.repository.UserRepository
import com.browniepoints.app.data.repository.UserRepositoryImpl
import com.browniepoints.app.data.service.ErrorHandlerService
import com.browniepoints.app.data.service.ErrorRecoveryService
import com.browniepoints.app.data.service.FcmTokenManager
import com.browniepoints.app.data.service.GoogleSignInService
import com.browniepoints.app.data.service.InAppNotificationManager
import com.browniepoints.app.data.service.NetworkMonitorService
import com.browniepoints.app.data.service.NotificationIntegrationService
import com.browniepoints.app.data.service.OfflineSyncManager
import com.browniepoints.app.data.service.RetryManager
import com.browniepoints.app.data.service.TimeoutManager
import com.browniepoints.app.data.service.TimeoutNotificationService
import com.browniepoints.app.data.service.TransactionNotificationService
import com.browniepoints.app.data.service.UiStateManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        // Enhanced offline persistence configuration
        firestore.firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        return firestore
    }

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
    
    @Provides
    @Singleton
    fun provideGoogleSignInClient(
        @ApplicationContext context: Context
    ): GoogleSignInClient {
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(context.getString(com.browniepoints.app.R.string.default_web_client_id))
            .requestEmail()
            .build()
        
        return com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
    }
    
    @Provides
    @Singleton
    fun provideNetworkMonitorService(
        @ApplicationContext context: Context
    ): NetworkMonitorService = NetworkMonitorService(context)
    
    @Provides
    @Singleton
    fun provideOfflineSyncManager(
        networkMonitorService: NetworkMonitorService,
        firestore: FirebaseFirestore
    ): OfflineSyncManager = OfflineSyncManager(networkMonitorService, firestore)
    
    @Provides
    @Singleton
    fun provideErrorHandlerService(
        networkMonitorService: NetworkMonitorService
    ): ErrorHandlerService = ErrorHandlerService(networkMonitorService)
    
    @Provides
    @Singleton
    fun provideRetryManager(
        networkMonitorService: NetworkMonitorService
    ): RetryManager = RetryManager(networkMonitorService)
    
    @Provides
    @Singleton
    fun provideErrorRecoveryService(
        errorHandlerService: ErrorHandlerService,
        retryManager: RetryManager,
        offlineSyncManager: OfflineSyncManager,
        networkMonitorService: NetworkMonitorService
    ): ErrorRecoveryService = ErrorRecoveryService(
        errorHandlerService,
        retryManager,
        offlineSyncManager,
        networkMonitorService
    )
    
    @Provides
    @Singleton
    fun provideUiStateManager(
        networkMonitorService: NetworkMonitorService,
        offlineSyncManager: OfflineSyncManager
    ): UiStateManager = UiStateManager(
        networkMonitorService,
        offlineSyncManager
    )
    
    @Provides
    @Singleton
    fun provideTimeoutManager(
        timeoutRepository: TimeoutRepository,
        timeoutNotificationService: TimeoutNotificationService
    ): TimeoutManager = TimeoutManager(timeoutRepository, timeoutNotificationService)
    
    @Provides
    @Singleton
    fun provideTimeoutNotificationService(
        inAppNotificationRepository: InAppNotificationRepository,
        notificationRepository: NotificationRepository,
        userRepository: UserRepository,
        connectionRepository: ConnectionRepository
    ): TimeoutNotificationService = TimeoutNotificationService(
        inAppNotificationRepository,
        notificationRepository,
        userRepository,
        connectionRepository
    )
    
    @Provides
    @Singleton
    fun provideTransactionNotificationService(
        inAppNotificationRepository: InAppNotificationRepository,
        notificationRepository: NotificationRepository,
        userRepository: UserRepository
    ): TransactionNotificationService = TransactionNotificationService(
        inAppNotificationRepository,
        notificationRepository,
        userRepository
    )
    
    @Provides
    @Singleton
    fun provideFcmTokenManager(
        @ApplicationContext context: Context,
        firebaseAuth: FirebaseAuth,
        firebaseMessaging: FirebaseMessaging,
        notificationRepository: NotificationRepository
    ): FcmTokenManager = FcmTokenManager(
        context,
        firebaseAuth,
        firebaseMessaging,
        notificationRepository
    )
    
    @Provides
    @Singleton
    fun provideInAppNotificationManager(
        inAppNotificationRepository: InAppNotificationRepository,
        firebaseAuth: FirebaseAuth
    ): InAppNotificationManager = InAppNotificationManager(
        inAppNotificationRepository,
        firebaseAuth
    )
    
    @Provides
    @Singleton
    fun provideNotificationIntegrationService(
        inAppNotificationRepository: InAppNotificationRepository,
        inAppNotificationManager: InAppNotificationManager,
        transactionNotificationService: TransactionNotificationService,
        timeoutNotificationService: TimeoutNotificationService,
        userRepository: UserRepository,
        firebaseAuth: FirebaseAuth
    ): NotificationIntegrationService = NotificationIntegrationService(
        inAppNotificationRepository,
        inAppNotificationManager,
        transactionNotificationService,
        timeoutNotificationService,
        userRepository,
        firebaseAuth
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository
    
    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository
    
    @Binds
    @Singleton
    abstract fun bindConnectionRepository(
        connectionRepositoryImpl: ConnectionRepositoryImpl
    ): ConnectionRepository
    
    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        transactionRepositoryImpl: TransactionRepositoryImpl
    ): TransactionRepository
    
    @Binds
    @Singleton
    abstract fun bindNotificationRepository(
        notificationRepositoryImpl: NotificationRepositoryImpl
    ): NotificationRepository
    
    @Binds
    @Singleton
    abstract fun bindInAppNotificationRepository(
        inAppNotificationRepositoryImpl: InAppNotificationRepositoryImpl
    ): InAppNotificationRepository
    
    @Binds
    @Singleton
    abstract fun bindTimeoutRepository(
        timeoutRepositoryImpl: TimeoutRepositoryImpl
    ): TimeoutRepository
}