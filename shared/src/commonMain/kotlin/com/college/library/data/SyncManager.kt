package com.college.library.data

import com.college.library.data.db.LibraryDatabase
import com.college.library.database.DatabaseServiceImpl

/**
 * A singleton or managed instance to provide the SyncService to the UI.
 *
 * Note: Synchronization is handled by the platform-specific callers or
 * by accepting that multiple instances might be created during race 
 * conditions on startup, which is safe for this stateless service.
 */
object SyncManager {
    private var syncService: SyncService? = null
    
    fun getSyncService(database: LibraryDatabase): SyncService {
        if (syncService == null) {
            val firestoreService = FirestoreService()
            val databaseService = DatabaseServiceImpl(database)
            val conflictResolver = ConflictResolver()
            
            syncService = SyncServiceImpl(
                firestoreService,
                databaseService,
                conflictResolver
            )
        }
        return syncService!!
    }
}
