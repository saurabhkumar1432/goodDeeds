package com.browniepoints.app

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BrowniePointsApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Configure Firestore for optimal offline persistence
        configureFirestore()
    }
    
    private fun configureFirestore() {
        val firestore = FirebaseFirestore.getInstance()
        
        // Enhanced Firestore settings for better offline experience
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        
        firestore.firestoreSettings = settings
    }
}