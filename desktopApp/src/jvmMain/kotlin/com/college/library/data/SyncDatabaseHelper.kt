package com.college.library.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.college.library.database.AppDatabase
import java.io.File

object SyncDatabaseHelper {
    private var database: AppDatabase? = null

    fun getDatabase(): AppDatabase {
        if (database == null) {
            val dbFile = File(System.getProperty("user.home"), "GDC_Library50/sync_library.db")
            dbFile.parentFile.mkdirs()
            
            val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
            
            if (!dbFile.exists() || dbFile.length() == 0L) {
                AppDatabase.Schema.create(driver)
            }
            database = AppDatabase(driver)
        }
        return database!!
    }
}
