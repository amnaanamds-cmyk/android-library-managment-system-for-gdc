package com.college.library.backup

import android.app.Application
import android.os.Environment
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.college.library.data.db.LibraryDatabase
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class BackupInfo(
    val file: File,
    val name: String,
    val dateMillis: Long,
    val sizeBytes: Long
)

@Singleton
class BackupManager @Inject constructor(
    private val application: Application,
    private val database: LibraryDatabase
) {

    companion object {
        private const val BACKUP_DIR_NAME = "LibraryBackups"
        private const val DB_NAME = "library_db"
        private const val MAX_BACKUPS = 10
        private const val AUTO_BACKUP_WORK_NAME = "library_auto_backup_daily"
        private const val PREFS_NAME = "library_backup_prefs"
        private const val KEY_AUTO_BACKUP = "auto_backup_enabled"
    }

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private fun getBackupDir(): File {
        val dir = File(
            application.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            BACKUP_DIR_NAME
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getDbFile(): File = application.getDatabasePath(DB_NAME)

    /**
     * Checkpoint WAL so all pending writes are flushed into the main database file.
     */
    private fun checkpointWal() {
        val db = database.openHelper.writableDatabase
        db.query("PRAGMA wal_checkpoint(TRUNCATE)")?.close()
    }

    /**
     * Creates a timestamped backup of the current database.
     * Returns the created backup file, or null on failure.
     */
    fun createBackup(): File? {
        return try {
            checkpointWal()
            val dbFile = getDbFile()
            if (!dbFile.exists()) return null

            val timestamp = dateFormat.format(Date())
            val backupFile = File(getBackupDir(), "library_backup_$timestamp.sqlite")

            FileInputStream(dbFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Also copy WAL and SHM if they exist (they should be empty after checkpoint, but just in case)
            val walFile = File(dbFile.parent, "$DB_NAME-wal")
            val shmFile = File(dbFile.parent, "$DB_NAME-shm")
            if (walFile.exists()) {
                FileInputStream(walFile).use { input ->
                    FileOutputStream(File(getBackupDir(), "library_backup_$timestamp.sqlite-wal")).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (shmFile.exists()) {
                FileInputStream(shmFile).use { input ->
                    FileOutputStream(File(getBackupDir(), "library_backup_$timestamp.sqlite-shm")).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            pruneOldBackups()
            backupFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Lists all backup files sorted newest-first.
     */
    fun listBackups(): List<BackupInfo> {
        val dir = getBackupDir()
        if (!dir.exists()) return emptyList()

        return dir.listFiles { file ->
            file.isFile && file.name.endsWith(".sqlite") && file.name.startsWith("library_backup_")
        }?.map { file ->
            BackupInfo(
                file = file,
                name = file.name,
                dateMillis = file.lastModified(),
                sizeBytes = file.length()
            )
        }?.sortedByDescending { it.dateMillis } ?: emptyList()
    }

    /**
     * Restores the database from a given backup file.
     * The caller must close and re-open the database / restart the app after this.
     */
    fun restoreFromBackup(backupFile: File): Boolean {
        return try {
            if (!backupFile.exists()) return false

            // Close the database so we can overwrite the file
            database.close()

            val dbFile = getDbFile()

            // Delete WAL and SHM files before restoring
            File(dbFile.parent, "$DB_NAME-wal").delete()
            File(dbFile.parent, "$DB_NAME-shm").delete()

            // Copy backup over the database file
            FileInputStream(backupFile).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Also restore WAL/SHM companions if they exist alongside the backup
            val backupWal = File(backupFile.parent, backupFile.name + "-wal")
            val backupShm = File(backupFile.parent, backupFile.name + "-shm")
            if (backupWal.exists()) {
                FileInputStream(backupWal).use { input ->
                    FileOutputStream(File(dbFile.parent, "$DB_NAME-wal")).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (backupShm.exists()) {
                FileInputStream(backupShm).use { input ->
                    FileOutputStream(File(dbFile.parent, "$DB_NAME-shm")).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Deletes a specific backup file (and its WAL/SHM companions).
     */
    fun deleteBackup(file: File): Boolean {
        return try {
            val walFile = File(file.parent, file.name + "-wal")
            val shmFile = File(file.parent, file.name + "-shm")
            walFile.delete()
            shmFile.delete()
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Keeps only the most recent [MAX_BACKUPS] backups, deleting the rest.
     */
    private fun pruneOldBackups() {
        val backups = listBackups()
        if (backups.size > MAX_BACKUPS) {
            backups.drop(MAX_BACKUPS).forEach { backup ->
                deleteBackup(backup.file)
            }
        }
    }

    /**
     * Schedules (or cancels) the daily automatic backup via WorkManager.
     */
    fun scheduleAutoBackup(enabled: Boolean) {
        val prefs = application.getSharedPreferences(PREFS_NAME, 0)
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()

        val workManager = WorkManager.getInstance(application)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                AUTO_BACKUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } else {
            workManager.cancelUniqueWork(AUTO_BACKUP_WORK_NAME)
        }
    }

    /**
     * Returns whether auto-backup is currently enabled.
     */
    fun isAutoBackupEnabled(): Boolean {
        val prefs = application.getSharedPreferences(PREFS_NAME, 0)
        return prefs.getBoolean(KEY_AUTO_BACKUP, false)
    }

    /**
     * Exports all database tables as a portable JSON file.
     * Returns the JSON file, or null on failure.
     */
    suspend fun exportToJson(): File? {
        return try {
            val bookDao = database.bookDao()
            val memberDao = database.memberDao()
            val issuedBookDao = database.issuedBookDao()

            val books = bookDao.getAllBooksStatic()
            val members = memberDao.getAllMembers().first()
            val issuedBooks = issuedBookDao.getAllTransactions().first()

            val root = JSONObject()
            root.put("exportDate", displayDateFormat.format(Date()))
            root.put("appVersion", "1.0")

            // Books
            val booksArray = JSONArray()
            for (book in books) {
                booksArray.put(JSONObject().apply {
                    put("id", book.id)
                    put("isbn", book.isbn)
                    put("accNo", book.accNo)
                    put("title", book.title)
                    put("author", book.author)
                    put("publisher", book.publisher)
                    put("publisherPlace", book.publisherPlace)
                    put("publishDate", book.publishDate)
                    put("edition", book.edition)
                    put("pages", book.pages)
                    put("procurement", book.procurement)
                    put("volume", book.volume)
                    put("price", book.price)
                    put("status", book.status)
                    put("isDigital", book.isDigital)
                    put("digitalUrl", book.digitalUrl ?: JSONObject.NULL)
                    put("category", book.category)
                })
            }
            root.put("books", booksArray)

            // Members
            val membersArray = JSONArray()
            for (member in members) {
                membersArray.put(JSONObject().apply {
                    put("id", member.id)
                    put("memberId", member.memberId)
                    put("name", member.name)
                    put("email", member.email)
                    put("phone", member.phone)
                    put("department", member.department)
                    put("memberType", member.memberType)
                    put("joinDate", member.joinDate)
                    put("expiryDate", member.expiryDate)
                    put("booksIssued", member.booksIssued)
                    put("fatherName", member.fatherName)
                    put("className", member.className)
                    put("classNo", member.classNo)
                    put("address", member.address)
                    put("photoUri", member.photoUri ?: JSONObject.NULL)
                    put("designation", member.designation)
                    put("bps", member.bps)
                    put("pin", member.pin)
                })
            }
            root.put("members", membersArray)

            // Issued Books
            val issuedArray = JSONArray()
            for (issued in issuedBooks) {
                issuedArray.put(JSONObject().apply {
                    put("id", issued.id)
                    put("bookId", issued.bookId)
                    put("bookTitle", issued.bookTitle)
                    put("bookIsbn", issued.bookIsbn)
                    put("memberId", issued.memberId)
                    put("memberName", issued.memberName)
                    put("memberMemberId", issued.memberMemberId)
                    put("issueDate", issued.issueDate)
                    put("dueDate", issued.dueDate)
                    put("returnDate", issued.returnDate ?: JSONObject.NULL)
                    put("fine", issued.fine)
                    put("status", issued.status)
                })
            }
            root.put("issuedBooks", issuedArray)

            val timestamp = dateFormat.format(Date())
            val jsonFile = File(getBackupDir(), "library_export_$timestamp.json")
            jsonFile.writeText(root.toString(2))
            jsonFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
