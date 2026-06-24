package com.college.library.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager Worker that creates a daily automatic backup of the library database.
 * Scheduled by [BackupManager.scheduleAutoBackup].
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupManager: BackupManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "BackupWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting automatic daily backup...")
        val backupFile = backupManager.createBackup()
        return if (backupFile != null) {
            Log.d(TAG, "Auto-backup created successfully: ${backupFile.name}")
            Result.success()
        } else {
            Log.e(TAG, "Auto-backup failed")
            Result.retry()
        }
    }
}
