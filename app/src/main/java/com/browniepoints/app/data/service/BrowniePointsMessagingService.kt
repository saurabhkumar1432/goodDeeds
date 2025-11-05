package com.browniepoints.app.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.browniepoints.app.data.repository.NotificationRepository
import com.browniepoints.app.presentation.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BrowniePointsMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationRepository: NotificationRepository
    
    @Inject
    lateinit var firebaseAuth: FirebaseAuth
    
    @Inject
    lateinit var fcmTokenManager: FcmTokenManager
    
    @Inject
    lateinit var notificationIntegrationService: NotificationIntegrationService

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "From: ${remoteMessage.from}")
        
        // Handle FCM messages here
        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            // Handle data payload using integration service
            handleDataMessage(remoteMessage.data)
            // Also process through integration service for overlay notifications
            notificationIntegrationService.processFcmNotification(remoteMessage.data)
        }
        
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            // Handle notification payload - this will be implemented in task 7.3
            handleNotificationMessage(it)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")
        
        // Use the FCM token manager to handle token updates
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            serviceScope.launch {
                val success = fcmTokenManager.updateTokenForUser(currentUser.uid, token)
                if (success) {
                    Log.d(TAG, "FCM token updated successfully via token manager")
                } else {
                    Log.e(TAG, "Failed to update FCM token via token manager")
                }
            }
        } else {
            Log.w(TAG, "No authenticated user found, storing token for later update")
            fcmTokenManager.storePendingToken(token)
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        // Extract data from the message
        val type = data["type"]
        val senderId = data["senderId"]
        val senderName = data["senderName"]
        val points = data["points"]
        val message = data["message"]
        val transactionId = data["transactionId"]
        val connectionId = data["connectionId"]
        val timeoutId = data["timeoutId"]
        
        Log.d(TAG, "Handling data message - Type: $type, Sender: $senderId, Points: $points")
        
        // Validate required fields
        if (type.isNullOrBlank()) {
            Log.w(TAG, "Notification type is missing or empty")
            return
        }
        
        // Create appropriate notification based on type
        when (type.lowercase()) {
            "points_received" -> {
                if (senderName.isNullOrBlank() || points.isNullOrBlank()) {
                    Log.w(TAG, "Missing required fields for points_received notification")
                    return
                }
                
                val pointsInt = points.toIntOrNull() ?: 0
                val title = "Brownie Points Received!"
                val body = if (!message.isNullOrBlank()) {
                    "$senderName gave you $pointsInt brownie point${if (pointsInt != 1) "s" else ""}: \"$message\""
                } else {
                    "$senderName gave you $pointsInt brownie point${if (pointsInt != 1) "s" else ""}!"
                }
                showNotification(title, body, data)
                Log.d(TAG, "Points received notification: $senderName gave $pointsInt points")
            }
            
            "points_deducted" -> {
                if (senderName.isNullOrBlank() || points.isNullOrBlank()) {
                    Log.w(TAG, "Missing required fields for points_deducted notification")
                    return
                }
                
                val pointsInt = kotlin.math.abs(points.toIntOrNull() ?: 0)
                val title = "Brownie Points Deducted"
                val body = if (!message.isNullOrBlank()) {
                    "$senderName deducted $pointsInt brownie point${if (pointsInt != 1) "s" else ""} from you. Reason: \"$message\""
                } else {
                    "$senderName deducted $pointsInt brownie point${if (pointsInt != 1) "s" else ""} from you"
                }
                showNotification(title, body, data)
                Log.d(TAG, "Points deducted notification: $senderName deducted $pointsInt points")
            }
            
            "connection_request" -> {
                if (senderName.isNullOrBlank()) {
                    Log.w(TAG, "Missing sender name for connection_request notification")
                    return
                }
                
                val title = "Connection Request"
                val body = "$senderName wants to connect with you"
                showNotification(title, body, data)
                Log.d(TAG, "Connection request from: $senderName")
            }
            
            "connection_accepted" -> {
                if (senderName.isNullOrBlank()) {
                    Log.w(TAG, "Missing sender name for connection_accepted notification")
                    return
                }
                
                val title = "Connection Accepted"
                val body = "$senderName accepted your connection request"
                showNotification(title, body, data)
                Log.d(TAG, "Connection accepted by: $senderName")
            }
            
            "timeout_requested" -> {
                if (senderName.isNullOrBlank()) {
                    Log.w(TAG, "Missing sender name for timeout_requested notification")
                    return
                }
                
                val title = "Timeout Requested"
                val body = "You have requested a 30-minute timeout. Point exchanges are now disabled."
                showNotification(title, body, data)
                Log.d(TAG, "Timeout requested by: $senderName")
            }
            
            "timeout_partner_request" -> {
                if (senderName.isNullOrBlank()) {
                    Log.w(TAG, "Missing sender name for timeout_partner_request notification")
                    return
                }
                
                val title = "Partner Requested Timeout"
                val body = "$senderName has requested a timeout. Point exchanges are now disabled for 30 minutes."
                showNotification(title, body, data)
                Log.d(TAG, "Partner timeout request from: $senderName")
            }
            
            "timeout_expired" -> {
                val title = "Timeout Expired"
                val body = "Your timeout has expired. You can now exchange points again."
                showNotification(title, body, data)
                Log.d(TAG, "Timeout expired notification")
            }
            
            else -> {
                Log.w(TAG, "Unknown notification type: $type")
            }
        }
    }

    private fun handleNotificationMessage(notification: RemoteMessage.Notification) {
        val title = notification.title ?: "Brownie Points"
        val body = notification.body ?: "You have a new notification"
        
        Log.d(TAG, "Handling notification message - Title: $title, Body: $body")
        
        // Create and show notification
        showNotification(title, body, emptyMap(), notification.imageUrl?.toString())
    }

    /**
     * Creates and displays a notification
     */
    private fun showNotification(title: String, body: String, data: Map<String, String> = emptyMap(), imageUrl: String? = null) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android O and above
        createNotificationChannel(notificationManager)
        
        // Create intent to open the app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Add notification data as extras for proper navigation
            data.forEach { (key, value) ->
                putExtra(key, value)
            }
            putExtra("notification_clicked", true)
        }
        
        // Generate unique request code based on notification type and timestamp
        val requestCode = (data["type"]?.hashCode() ?: 0) + System.currentTimeMillis().toInt()
        
        val pendingIntent = PendingIntent.getActivity(
            this, 
            requestCode, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build the notification with enhanced features
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Using system icon for now
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)) // Allow long text
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
        
        // Add notification category based on type
        val notificationType = data["type"]
        when (notificationType?.lowercase()) {
            "points_received", "points_deducted" -> {
                notificationBuilder.setCategory(NotificationCompat.CATEGORY_SOCIAL)
            }
            "connection_request", "connection_accepted" -> {
                notificationBuilder.setCategory(NotificationCompat.CATEGORY_SOCIAL)
            }
            "timeout_requested", "timeout_partner_request", "timeout_expired" -> {
                notificationBuilder.setCategory(NotificationCompat.CATEGORY_STATUS)
            }
        }
        
        // Add action buttons for certain notification types
        when (notificationType?.lowercase()) {
            "connection_request" -> {
                // Add Accept/Decline actions for connection requests
                val acceptIntent = Intent(this, MainActivity::class.java).apply {
                    putExtra("action", "accept_connection")
                    putExtra("senderId", data["senderId"])
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val acceptPendingIntent = PendingIntent.getActivity(
                    this, 
                    requestCode + 1, 
                    acceptIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                notificationBuilder.addAction(
                    android.R.drawable.ic_menu_add, 
                    "Accept", 
                    acceptPendingIntent
                )
            }
        }
        
        // Generate unique notification ID to allow multiple notifications
        val notificationId = requestCode
        
        // Show the notification
        notificationManager.notify(notificationId, notificationBuilder.build())
        
        Log.d(TAG, "Notification displayed: $title - $body (ID: $notificationId)")
    }
    
    /**
     * Creates notification channel for Android O and above
     */
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "BrowniePointsFCM"
        private const val CHANNEL_ID = "brownie_points_notifications"
        private const val CHANNEL_NAME = "Brownie Points Notifications"
        private const val CHANNEL_DESCRIPTION = "Notifications for brownie points and connections"
        private const val NOTIFICATION_ID = 1001
    }
}