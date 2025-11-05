package com.browniepoints.app.presentation

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.browniepoints.app.data.service.NotificationPermissionHelper
import com.browniepoints.app.presentation.navigation.BrowniePointsNavigation
import com.browniepoints.app.presentation.ui.theme.BrowniePointsAppTheme
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var googleSignInClient: GoogleSignInClient
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request notification permission for Android 13+
        requestNotificationPermissionIfNeeded()
        
        setContent {
            BrowniePointsAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BrowniePointsNavigation(
                        googleSignInClient = googleSignInClient
                    )
                }
            }
        }
    }
    
    /**
     * Requests notification permission if needed
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
            Log.d("MainActivity", "Requesting notification permission")
            NotificationPermissionHelper.requestNotificationPermission(this)
        } else {
            Log.d("MainActivity", "Notification permission already granted")
        }
    }
    
    /**
     * Handles permission request results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            NotificationPermissionHelper.NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("MainActivity", "Notification permission granted")
                } else {
                    Log.w("MainActivity", "Notification permission denied")
                }
            }
        }
    }
}