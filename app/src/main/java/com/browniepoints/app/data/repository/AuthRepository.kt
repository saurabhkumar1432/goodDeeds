package com.browniepoints.app.data.repository

import com.browniepoints.app.data.model.User
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<FirebaseUser?>
    val isSignedIn: Flow<Boolean>
    
    suspend fun signInWithGoogle(idToken: String): Result<User>
    suspend fun signOut(): Result<Unit>
    fun getCurrentUser(): FirebaseUser?
}