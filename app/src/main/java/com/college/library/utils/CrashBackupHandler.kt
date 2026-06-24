package com.college.library.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashBackupHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    
    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            Log.e("CrashBackupHandler", "App crashed. Taking database backup...", e)
            val dbFile = context.getDatabasePath("library_db")
            if (dbFile.exists()) {
                val backupDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "LibraryBackups")
                if (!backupDir.exists()) backupDir.mkdirs()
                
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFile = File(backupDir, "library_db_crash_backup_$timestamp.sqlite")
                
                FileInputStream(dbFile).use { input ->
                    FileOutputStream(backupFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Save path for next startup so we can prompt user to upload to Google Drive
                context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("pending_backup_file", backupFile.absolutePath)
                    .commit()
            }
        } catch (ex: Exception) {
            Log.e("CrashBackupHandler", "Failed to backup database", ex)
        } finally {
            defaultHandler?.uncaughtException(t, e)
        }
    }
    
    companion object {
        fun init(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (defaultHandler !is CrashBackupHandler) {
                Thread.setDefaultUncaughtExceptionHandler(CrashBackupHandler(context.applicationContext, defaultHandler))
            }
        }
    }
}
