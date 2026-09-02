package com.college.library.data.sync

import android.content.Context
import android.util.Log
import com.college.library.data.SyncManager
import com.college.library.data.SyncService
import com.college.library.data.SyncStatus
import com.college.library.data.db.LibraryDatabase
import com.college.library.profile.CollegeProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object RealtimeSyncManager {
    private const val TAG = "RealtimeSyncManager"
    private var activeInstitutionId: String? = null
    private var syncService: SyncService? = null

    // The status the UI observes.
    //
    // This was previously declared as
    //     val syncStatus: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Offline)
    // which discards the mutable reference at construction: nothing could ever
    // emit into it, so the badge showed "Offline" forever no matter what the
    // engine was doing. It is now mirrored from the sync engine's own status.
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Offline)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    fun initialize(
        context: Context,
        database: LibraryDatabase,
        institutionId: String,
        scope: CoroutineScope,
        institutionName: String = "",
    ) {
        if (activeInstitutionId == institutionId) return
        activeInstitutionId = institutionId

        Log.d(TAG, "Initializing RealtimeSyncManager for institution: $institutionId")
        val service = SyncManager.getSyncService(database)
        syncService = service
        service.currentInstitutionId = institutionId
        if (institutionName.isNotEmpty()) {
            service.currentInstitutionName = institutionName
        }
        service.startRealtimeSync(scope)

        // Mirror the engine's status into the flow the UI collects.
        scope.launch {
            service.status.collect { _syncStatus.value = it }
        }

        scope.launch(Dispatchers.IO) {
            try {
                service.startFullSync()
            } catch (e: Exception) {
                Log.e(TAG, "Initial full sync failed", e)
                _syncStatus.value = SyncStatus.Error("Initial sync failed")
            }
            try {
                val profile = CollegeProfileManager.getInstance(context)
                profile.syncFromCloud(institutionId)
                // Name the college in the directorate registry once its profile
                // is known, so the portal shows a real name rather than an ID.
                val name = profile.getProfile().collegeFullName
                if (name.isNotEmpty()) {
                    service.currentInstitutionName = name
                }
            } catch (e: Exception) {
                Log.e(TAG, "College profile cloud sync failed", e)
            }
            try {
                service.publishDirectorateSnapshot(force = true)
            } catch (e: Exception) {
                Log.e(TAG, "Directorate snapshot publish failed", e)
            }
        }
    }

    // No "?: gdc11" fallback. If forceReconnect is called before any institution
    // has been set (the user isn't logged in yet) there is nothing correct to
    // reconnect to, so log and no-op rather than silently attaching this device
    // to the shared "gdc11" institution.
    fun forceReconnect(context: Context, database: LibraryDatabase, scope: CoroutineScope) {
        val instId = activeInstitutionId
        if (instId.isNullOrBlank()) {
            Log.w(TAG, "forceReconnect called with no active institution — ignoring. User is probably not logged in yet.")
            return
        }
        val name = syncService?.currentInstitutionName ?: ""
        activeInstitutionId = null
        initialize(context, database, instId, scope, name)
    }

    fun disconnect(database: LibraryDatabase) {
        activeInstitutionId = null
        val service = syncService ?: SyncManager.getSyncService(database)
        service.stopRealtimeSync()
        syncService = null
        _syncStatus.value = SyncStatus.Offline
    }
}
