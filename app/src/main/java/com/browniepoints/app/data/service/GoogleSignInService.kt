package com.browniepoints.app.data.service

import android.content.Context
import android.util.Log
import com.browniepoints.app.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSignInService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    
    companion object {
        private const val TAG = "GoogleSignInService"
    }
    
    private val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(getWebClientId())
        .requestEmail()
        .requestProfile()
        .build()
    
    val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)
    
    /**
     * Extracts the ID token from Google Sign-In result
     * Firebase authentication is handled by AuthRepository
     */
    suspend fun getIdTokenFromSignInResult(task: Task<GoogleSignInAccount>): Result<String> {
        return try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            
            if (idToken == null) {
                Log.e(TAG, "Google Sign-In failed: ID token is null")
                return Result.failure(Exception("Failed to get ID token from Google Sign-In"))
            }
            
            Log.d(TAG, "Google Sign-In successful, ID token obtained")
            Result.success(idToken)
        } catch (e: ApiException) {
            Log.e(TAG, "Google Sign-In failed with ApiException", e)
            Result.failure(Exception("Google Sign-In failed: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In processing failed", e)
            Result.failure(Exception("Google Sign-In processing failed: ${e.message}"))
        }
    }
    
    /**
     * Signs out from both Firebase and Google Sign-In
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            firebaseAuth.signOut()
            googleSignInClient.signOut().await()
            Log.d(TAG, "Sign out successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed", e)
            Result.failure(Exception("Sign out failed: ${e.message}"))
        }
    }
    
    /**
     * Revokes access from Google Sign-In
     */
    suspend fun revokeAccess(): Result<Unit> {
        return try {
            googleSignInClient.revokeAccess().await()
            Log.d(TAG, "Access revoked successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Revoke access failed", e)
            Result.failure(Exception("Revoke access failed: ${e.message}"))
        }
    }
    
    /**
     * Gets the web client ID from resources
     */
    private fun getWebClientId(): String {
        return try {
            val webClientId = context.getString(R.string.default_web_client_id)
            if (webClientId.contains("YOUR_WEB_CLIENT_ID")) {
                Log.e(TAG, "Web client ID not properly configured")
                throw IllegalStateException("Web client ID not properly configured in strings.xml")
            }
            webClientId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get web client ID", e)
            throw IllegalStateException("Failed to get web client ID: ${e.message}")
        }
    }
    
    /**
     * Checks if Google Play Services is available
     */
    fun isGooglePlayServicesAvailable(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null ||
                com.google.android.gms.common.GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
    }
}