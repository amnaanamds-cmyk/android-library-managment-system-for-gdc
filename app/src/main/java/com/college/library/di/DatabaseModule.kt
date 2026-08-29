package com.college.library.di

import android.app.Application
import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.college.library.data.db.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideApplicationScope() = CoroutineScope(SupervisorJob())

    @Provides
    @Singleton
    fun provideSqlDriver(app: Application): app.cash.sqldelight.db.SqlDriver {
        // Database name v6 to ensure a clean slate and resolve any persistent schema conflicts.
        val databaseName = "library_v6.db"
        return try {
            AndroidSqliteDriver(LibraryDatabase.Schema, app, databaseName)
        } catch (e: Exception) {
            // If initialization fails (e.g. corruption), delete and recreate
            app.deleteDatabase(databaseName)
            AndroidSqliteDriver(LibraryDatabase.Schema, app, databaseName)
        }
    }

    @Provides
    @Singleton
    fun provideLibraryDatabase(
        app: Application,
        driver: app.cash.sqldelight.db.SqlDriver,
        applicationScope: CoroutineScope
    ): LibraryDatabase {
        val db = LibraryDatabase(driver)
        
        // Seed initial data if the database is completely empty and reset was not performed
        applicationScope.launch(Dispatchers.IO) {
            try {
                val prefs = app.getSharedPreferences("library_settings", Context.MODE_PRIVATE)
                val preventAutoSeed = prefs.getBoolean("prevent_autoseed", false)
                if (!preventAutoSeed && db.bookQueriesQueries.getTotalCount().executeAsOneOrNull() == 0L) {
                    DataSeeder.seedBooks(db)
                }
            } catch (e: Exception) {
                // Ignore seeding errors on first startup if schema is malformed
            }
        }
        return db
    }

    @Provides
    @Singleton
    fun provideBookDao(db: LibraryDatabase): BookDao = BookDaoAdapter(db)

    @Provides
    @Singleton
    fun provideMemberDao(db: LibraryDatabase): MemberDao = MemberDaoAdapter(db)

    @Provides
    @Singleton
    fun provideIssuedBookDao(db: LibraryDatabase): IssuedBookDao = IssuedBookDaoAdapter(db)

    @Provides
    @Singleton
    fun provideStatsQueries(db: LibraryDatabase): StatsQueries = StatsQueriesAdapter(db)

    @Provides
    @Singleton
    fun provideReservationDao(db: LibraryDatabase): ReservationDao = ReservationDaoAdapter(db)

    @Provides
    @Singleton
    fun provideBookRequestDao(db: LibraryDatabase): BookRequestDao = BookRequestDaoAdapter(db)

    @Provides
    @Singleton
    fun provideBookReviewDao(db: LibraryDatabase): BookReviewDao = BookReviewDaoAdapter(db)
}
