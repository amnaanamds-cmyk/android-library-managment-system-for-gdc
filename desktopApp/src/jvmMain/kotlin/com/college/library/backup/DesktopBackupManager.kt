package com.college.library.backup

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object DesktopBackupManager {
    private const val DB_NAME = "library_db"
    private val appDataDir = File(System.getProperty("user.home"), ".LibraryApp")
    private val backupDir = File(appDataDir, "backups")
    private val dbFile = File(appDataDir, DB_NAME)

    init {
        if (!backupDir.exists()) backupDir.mkdirs()
    }

    fun createBackup(): Boolean {
        return try {
            if (!dbFile.exists()) return false
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFile = File(backupDir, "library_backup_$timestamp.sqlite")
            
            FileInputStream(dbFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun restoreBackup(): Boolean {
        return try {
            val latestBackup = backupDir.listFiles { file -> file.name.endsWith(".sqlite") }
                ?.maxByOrNull { it.lastModified() }
            
            if (latestBackup != null && latestBackup.exists()) {
                FileInputStream(latestBackup).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
