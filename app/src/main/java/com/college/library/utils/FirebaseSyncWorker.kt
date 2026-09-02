package com.college.library.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await

/**
 * FirebaseSyncWorker — Bi-directional Firestore sync for the active institution.
 *
 * Architecture (SYNC_ARCHITECTURE.md):
 *   Firestore path: /institutions/{institutionId}/books|members|issues|reservations
 *   Conflict resolution: lastUpdated timestamp wins (last-write-wins).
 *
 * This worker is enqueued by WorkManager as a PeriodicWorkRequest (e.g. every 15 min).
 * It can also be triggered immediately after a local write via enqueueSyncNow().
 */
@HiltWorker
class FirebaseSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val database: com.college.library.data.db.LibraryDatabase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs: SharedPreferences =
            context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val institutionId = prefs.getString("institution_id", "") ?: ""
        // No hardcoded fallback: a background worker that runs before login
        // would otherwise sync this device against a shared "gdc11" tenant.
        // Nothing to do yet — succeed so WorkManager keeps the periodic job.
        if (institutionId.isEmpty()) return Result.success()

        return try {
            val db = FirebaseFirestore.getInstance()
            val instRef = db.collection("institutions").document(institutionId)

            // Trigger full KMP database sync (pull then push)
            val syncService = com.college.library.data.SyncManager.getSyncService(database)
            syncService.currentInstitutionId = institutionId
            syncService.startFullSync()

            val pingRef = instRef.collection("_sync").document("heartbeat")
            pingRef.set(
                mapOf("lastSyncAt" to System.currentTimeMillis()),
                SetOptions.merge()
            ).await()

            Result.success()
        } catch (e: Exception) {
            // Retry on next work request
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "FirebaseSyncWorker"
    }
}
