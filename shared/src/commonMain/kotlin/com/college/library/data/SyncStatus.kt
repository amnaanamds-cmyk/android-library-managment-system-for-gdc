package com.college.library.data

/**
 * Runtime state of the real-time sync engine.
 * The UI maps each state to a colored indicator:
 *  - Synced  -> green
 *  - Syncing -> amber
 *  - Offline -> red
 *  - Error   -> red (with a message)
 */
sealed interface SyncStatus {
    data object Synced : SyncStatus
    data object Syncing : SyncStatus
    data object Offline : SyncStatus
    data class Error(val message: String) : SyncStatus
}
