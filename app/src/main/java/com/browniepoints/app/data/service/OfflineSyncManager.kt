package com.browniepoints.app.data.service

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineSyncManager @Inject constructor(
    private val networkMonitorService: NetworkMonitorService,
    private val firestore: FirebaseFirestore
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _syncStatus = MutableStateFlow(SyncStatus.SYNCED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()
    
    private val _hasPendingWrites = MutableStateFlow(false)
    val hasPendingWrites: StateFlow<Boolean> = _hasPendingWrites.asStateFlow()
    
    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()
    
    private var syncStatusListener: ListenerRegistration? = null
    
    companion object {
        private const val TAG = "OfflineSyncManager"
        private const val SYNC_TIMEOUT_MS = 10000L // 10 seconds
        private const val RETRY_DELAY_MS = 2000L // 2 seconds
        private const val MAX_RETRY_ATTEMPTS = 3
    }
    
    init {
        monitorNetworkAndSyncStatus()
        monitorFirestoreSyncStatus()
    }
    
    private fun monitorNetworkAndSyncStatus() {
        scope.launch {
            combine(
                networkMonitorService.isOnline,
                syncStatus
            ) { isOnline, currentSyncStatus ->
                _isOffline.value = !isOnline
                
                when {
                    !isOnline -> {
                        Log.d(TAG, "Network offline - switching to offline mode")
                        _syncStatus.value = SyncStatus.OFFLINE
                    }
                    isOnline && currentSyncStatus == SyncStatus.OFFLINE -> {
                        Log.d(TAG, "Network back online - initiating sync")
                        initiateSync()
                    }
                }
            }.collect { }
        }
    }
    
    private fun monitorFirestoreSyncStatus() {
        // Monitor Firestore's internal sync status by listening to metadata changes
        syncStatusListener = firestore.collection("users")
            .limit(1)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Sync status listener error", error)
                    return@addSnapshotListener
                }
                
                snapshot?.let {
                    val hasPendingWrites = it.metadata.hasPendingWrites()
                    val isFromCache = it.metadata.isFromCache()
                    
                    _hasPendingWrites.value = hasPendingWrites
                    
                    // Update sync status based on Firestore metadata
                    when {
                        hasPendingWrites && !networkMonitorService.isCurrentlyOnline() -> {
                            _syncStatus.value = SyncStatus.OFFLINE
                        }
                        hasPendingWrites && networkMonitorService.isCurrentlyOnline() -> {
                            _syncStatus.value = SyncStatus.SYNCING
                        }
                        !hasPendingWrites && !isFromCache -> {
                            _syncStatus.value = SyncStatus.SYNCED
                            _lastSyncTime.value = System.currentTimeMillis()
                        }
                    }
                }
            }
    }
    
    private fun initiateSync() {
        scope.launch {
            _syncStatus.value = SyncStatus.SYNCING
            
            try {
                // Ensure Firestore network is enabled
                firestore.enableNetwork()
                
                // Wait for sync to complete or timeout
                val syncStartTime = System.currentTimeMillis()
                var attempts = 0
                
                while (attempts < MAX_RETRY_ATTEMPTS) {
                    attempts++
                    
                    // Check if sync completed
                    if (!_hasPendingWrites.value && networkMonitorService.isCurrentlyOnline()) {
                        _syncStatus.value = SyncStatus.SYNCED
                        _lastSyncTime.value = System.currentTimeMillis()
                        Log.d(TAG, "Sync completed successfully")
                        return@launch
                    }
                    
                    // Check for timeout
                    if (System.currentTimeMillis() - syncStartTime > SYNC_TIMEOUT_MS) {
                        Log.w(TAG, "Sync timeout after ${SYNC_TIMEOUT_MS}ms")
                        break
                    }
                    
                    delay(RETRY_DELAY_MS)
                }
                
                // If we reach here, sync didn't complete successfully
                if (_hasPendingWrites.value) {
                    _syncStatus.value = SyncStatus.PENDING_SYNC
                    Log.w(TAG, "Sync incomplete - pending writes remain")
                } else {
                    _syncStatus.value = SyncStatus.SYNCED
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                _syncStatus.value = SyncStatus.ERROR
            }
        }
    }
    
    fun forceSyncWhenOnline() {
        scope.launch {
            if (networkMonitorService.isCurrentlyOnline()) {
                Log.d(TAG, "Force sync requested")
                initiateSync()
            } else {
                Log.w(TAG, "Force sync requested but network is offline")
                _syncStatus.value = SyncStatus.OFFLINE
            }
        }
    }
    
    fun waitForSync(): Boolean {
        return try {
            // This is a simplified check - in a real implementation you might want
            // to wait for the sync to complete with a timeout
            !_hasPendingWrites.value && networkMonitorService.isCurrentlyOnline()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking sync status", e)
            false
        }
    }
    
    fun clearOfflineCache() {
        scope.launch {
            try {
                firestore.clearPersistence()
                Log.d(TAG, "Offline cache cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear offline cache", e)
            }
        }
    }
    
    fun disableNetworkForTesting() {
        scope.launch {
            try {
                firestore.disableNetwork()
                _syncStatus.value = SyncStatus.OFFLINE
                Log.d(TAG, "Network disabled for testing")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disable network", e)
            }
        }
    }
    
    fun enableNetworkForTesting() {
        scope.launch {
            try {
                firestore.enableNetwork()
                initiateSync()
                Log.d(TAG, "Network enabled for testing")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable network", e)
                _syncStatus.value = SyncStatus.ERROR
            }
        }
    }
    
    fun onDestroy() {
        syncStatusListener?.remove()
        syncStatusListener = null
    }
}

