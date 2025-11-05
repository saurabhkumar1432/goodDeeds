package com.browniepoints.app.domain.usecase

import com.browniepoints.app.data.model.User
import com.browniepoints.app.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AuthUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    val currentUser: Flow<FirebaseUser?> = authRepository.currentUser
    val isSignedIn: Flow<Boolean> = authRepository.isSignedIn
    
    suspend fun signInWithGoogle(idToken: String): Result<User> {
        return authRepository.signInWithGoogle(idToken)
    }
    
    suspend fun signOut(): Result<Unit> {
        return authRepository.signOut()
    }
    
    fun getCurrentUser(): FirebaseUser? {
        return authRepository.getCurrentUser()
    }
}