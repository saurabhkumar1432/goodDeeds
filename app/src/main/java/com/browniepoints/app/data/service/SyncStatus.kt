package com.browniepoints.app.data.service

/**
 * Enum representing the synchronization status of data
 */
enum class SyncStatus {
    /**
     * Data is fully synchronized
     */
    SYNCED,
    
    /**
     * Data is currently being synchronized
     */
    SYNCING,
    
    /**
     * No network connection
     */
    OFFLINE,
    
    /**
     * Data is pending synchronization (offline changes waiting to be synced)
     */
    PENDING_SYNC,
    
    /**
     * Data synchronization failed
     */
    ERROR
}