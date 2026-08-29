package com.college.library.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.college.library.data.db.LibraryDatabase
import java.io.File

object DatabaseHelper {
    @Volatile
    private var database: LibraryDatabase? = null

    fun getDatabase(): LibraryDatabase {
        return database ?: synchronized(this) {
            database ?: initDatabase().also { database = it }
        }
    }

    private fun initDatabase(): LibraryDatabase {
        val userHome = System.getProperty("user.home")
        val dbDir = File(userHome, "GDC_Library50")
        if (!dbDir.exists()) dbDir.mkdirs()
        
        // Use library_v6.db to match Android and ensure a fresh schema
        val dbFile = File(dbDir, "library_v6.db")
        
        // Use forward slashes for JDBC URL to avoid Windows path issues
        val path = dbFile.absolutePath.replace("\\", "/")
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        
        try {
            // Check if schema exists by querying a known table
            driver.execute(null, "SELECT COUNT(*) FROM books", 0)
        } catch (e: Exception) {
            // Table doesn't exist or DB is new, create schema
            try {
                LibraryDatabase.Schema.create(driver)
            } catch (createEx: Exception) {
                // If creation fails, we might be in a bad state. 
                // In a dev build, we can try deleting the file and starting over.
            }
        }
        
        return LibraryDatabase(driver)
    }
}
