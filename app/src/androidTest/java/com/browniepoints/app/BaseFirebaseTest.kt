package com.browniepoints.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Base class for Firebase integration tests that provides common setup and teardown
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
abstract class BaseFirebaseTest {
    
    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var firebaseAuth: FirebaseAuth
    
    @Inject
    lateinit var firestore: FirebaseFirestore
    
    @Before
    open fun setUp() {
        hiltRule.inject()
        // Clear any existing auth state
        firebaseAuth.signOut()
    }
    
    @After
    open fun tearDown() {
        // Clean up auth state
        firebaseAuth.signOut()
        
        // Clear Firestore data - Note: This requires admin SDK in real emulator setup
        // For now, we'll clean up specific collections used in tests
        clearFirestoreCollections()
    }
    
    private fun clearFirestoreCollections() {
        // This is a simplified cleanup - in a real setup you'd use admin SDK
        // or Firebase emulator REST API to clear data
        try {
            // Clear users collection
            firestore.collection("users").get().addOnSuccessListener { snapshot ->
                for (document in snapshot.documents) {
                    document.reference.delete()
                }
            }
            
            // Clear transactions collection
            firestore.collection("transactions").get().addOnSuccessListener { snapshot ->
                for (document in snapshot.documents) {
                    document.reference.delete()
                }
            }
            
            // Clear connections collection
            firestore.collection("connections").get().addOnSuccessListener { snapshot ->
                for (document in snapshot.documents) {
                    document.reference.delete()
                }
            }
            
            // Clear timeouts collection (for couple features)
            firestore.collection("timeouts").get().addOnSuccessListener { snapshot ->
                for (document in snapshot.documents) {
                    document.reference.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors in tests
        }
    }
    
    /**
     * Helper method to create a test user in Firestore
     */
    protected suspend fun createTestUser(
        uid: String,
        displayName: String = "Test User",
        email: String = "test@example.com",
        matchingCode: String = "TEST01"
    ) {
        val user = mapOf(
            "uid" to uid,
            "displayName" to displayName,
            "email" to email,
            "matchingCode" to matchingCode,
            "totalPointsReceived" to 0,
            "createdAt" to com.google.firebase.Timestamp.now()
        )
        
        firestore.collection("users")
            .document(uid)
            .set(user)
            .await()
    }
}