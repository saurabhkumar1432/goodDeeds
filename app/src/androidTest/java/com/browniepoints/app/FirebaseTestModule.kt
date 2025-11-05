package com.browniepoints.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import com.browniepoints.app.di.AppModule
import javax.inject.Singleton

/**
 * Test module that provides Firebase instances configured for emulator use
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class]
)
object FirebaseTestModule {
    
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        val auth = FirebaseAuth.getInstance()
        // Connect to Firebase Auth Emulator
        auth.useEmulator("10.0.2.2", 9099)
        return auth
    }
    
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        // Connect to Firestore Emulator
        firestore.useEmulator("10.0.2.2", 8080)
        
        // Configure settings for testing
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(false) // Disable persistence for tests
            .build()
        firestore.firestoreSettings = settings
        
        return firestore
    }
}