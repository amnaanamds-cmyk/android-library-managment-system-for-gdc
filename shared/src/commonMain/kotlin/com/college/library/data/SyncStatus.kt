package com.college.library.data

/**
 * Runtime state of the real-time sync engine.
 * The UI maps each state to a colored indicator:
 *  - Synced    -> green
 *  - Syncing   -> amber
 *  - Offline   -> red
 *  - NotLinked -> red (distinct from Offline: see below)
 *  - Error     -> red (with a message)
 */
sealed interface SyncStatus {
    data object Synced : SyncStatus
    data object Syncing : SyncStatus
    data object Offline : SyncStatus

    /**
     * No institution is linked on this device, so there is nothing to sync
     * with. Kept separate from [Offline] because the two need opposite
     * responses — Offline resolves itself when the network returns, this one
     * never does until the user signs in again — and reporting it as Offline
     * sent people hunting for a network fault that did not exist.
     */
    data object NotLinked : SyncStatus

    data class Error(val message: String) : SyncStatus
}
