package com.browniepoints.app.data.repository

import android.util.Log
import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.service.GoogleSignInService
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val googleSignInService: GoogleSignInService
) : AuthRepository {

    companion object {
        private const val TAG = "AuthRepositoryImpl"
    }

    override val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            Log.d(TAG, "Auth state changed: ${auth.currentUser?.uid}")
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override val isSignedIn: Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val isSignedIn = auth.currentUser != null
            Log.d(TAG, "Sign-in state changed: $isSignedIn")
            trySend(isSignedIn)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            Log.d(TAG, "Starting Google Sign-In with ID token")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            Log.d(TAG, "Created Firebase credential")
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: throw Exception("Authentication failed")
            Log.d(TAG, "Firebase authentication successful: ${firebaseUser.uid}")
            
            val user = createOrGetUserProfile(firebaseUser)
            Log.d(TAG, "Sign-in completed successfully")
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            Log.d(TAG, "Starting sign out")
            googleSignInService.signOut()
            Log.d(TAG, "Sign out completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed", e)
            Result.failure(e)
        }
    }

    override fun getCurrentUser(): FirebaseUser? {
        return firebaseAuth.currentUser
    }

    /**
     * Creates a new user profile or retrieves existing one from Firestore
     */
    private suspend fun createOrGetUserProfile(firebaseUser: FirebaseUser): User {
        Log.d(TAG, "Checking if user exists in Firestore")
        val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
        
        return if (userDoc.exists()) {
            // User exists, return existing user data
            Log.d(TAG, "User already exists in Firestore")
            userDoc.toObject(User::class.java) ?: throw Exception("Failed to parse user data")
        } else {
            // New user, create profile in Firestore
            Log.d(TAG, "Creating new user profile in Firestore")
            val newUser = User(
                uid = firebaseUser.uid,
                displayName = firebaseUser.displayName ?: "",
                email = firebaseUser.email ?: "",
                photoUrl = firebaseUser.photoUrl?.toString(),
                matchingCode = generateMatchingCode(),
                connectedUserId = null,
                connected = false,
                totalPointsReceived = 0,
                fcmToken = null,
                createdAt = Timestamp.now()
            )
            
            // Save to Firestore
            firestore.collection("users").document(firebaseUser.uid).set(newUser).await()
            Log.d(TAG, "User profile created successfully")
            newUser
        }
    }

    /**
     * Generates a unique 6-character matching code for user connections
     */
    private suspend fun generateMatchingCode(): String {
        var code: String
        var isUnique = false
        
        do {
            // Generate a 6-character alphanumeric code
            code = (1..6)
                .map { Random.nextInt(0, 36) }
                .map { if (it < 10) ('0' + it) else ('A' + it - 10) }
                .joinToString("")
            
            // Check if code is unique in Firestore
            val existingUser = firestore.collection("users")
                .whereEqualTo("matchingCode", code)
                .get()
                .await()
            
            isUnique = existingUser.isEmpty
        } while (!isUnique)
        
        Log.d(TAG, "Generated unique matching code: $code")
        return code
    }
}