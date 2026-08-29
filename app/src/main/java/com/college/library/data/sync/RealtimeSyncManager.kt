package com.college.library.data.sync

import android.content.Context
import android.util.Log
import com.college.library.data.SyncManager
import com.college.library.data.SyncStatus
import com.college.library.data.db.LibraryDatabase
import com.college.library.profile.CollegeProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object RealtimeSyncManager {
    private const val TAG = "RealtimeSyncManager"
    private var activeInstitutionId: String? = null

    val syncStatus: StateFlow<SyncStatus> = MutableStateFlow<SyncStatus>(SyncStatus.Offline)

    fun initialize(context: Context, database: LibraryDatabase, institutionId: String, scope: CoroutineScope) {
        if (activeInstitutionId == institutionId) return
        activeInstitutionId = institutionId

        Log.d(TAG, "Initializing RealtimeSyncManager for institution: $institutionId")
        val syncService = SyncManager.getSyncService(database)
        syncService.currentInstitutionId = institutionId
        syncService.startRealtimeSync(scope)

        scope.launch(Dispatchers.IO) {
            try {
                syncService.startFullSync()
            } catch (e: Exception) {
                Log.e(TAG, "Initial full sync failed", e)
            }
            try {
                CollegeProfileManager.getInstance(context).syncFromCloud(institutionId)
            } catch (e: Exception) {
                Log.e(TAG, "College profile cloud sync failed", e)
            }
        }
    }

    // FIX: removed the "?: gdc11" fallback. If forceReconnect is called before
    // any institution has actually been set (activeInstitutionId is null —
    // e.g. user isn't logged in yet), there is nothing correct to reconnect
    // to, so we log and no-op instead of silently attaching this device to
    // the shared "gdc11" institution again.
    fun forceReconnect(context: Context, database: LibraryDatabase, scope: CoroutineScope) {
        val instId = activeInstitutionId
        if (instId.isNullOrBlank()) {
            Log.w(TAG, "forceReconnect called with no active institution — ignoring. User is probably not logged in yet.")
            return
        }
        activeInstitutionId = null
        initialize(context, database, instId, scope)
    }

    fun disconnect(database: LibraryDatabase) {
        activeInstitutionId = null
        val syncService = SyncManager.getSyncService(database)
        syncService.stopRealtimeSync()
    }
}