package com.college.library.di

import android.app.Application
import androidx.room.Room
import com.college.library.data.db.BookDao
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.LibraryDatabase
import com.college.library.data.db.MIGRATION_1_2
import com.college.library.data.db.MIGRATION_2_3
import com.college.library.data.db.MIGRATION_3_4
import com.college.library.data.db.MIGRATION_4_5
import com.college.library.data.db.MIGRATION_5_6
import com.college.library.data.db.MemberDao
import com.college.library.data.db.ReservationDao
import com.college.library.data.db.StatsQueries
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideApplicationScope() = CoroutineScope(SupervisorJob())

    @Provides
    @Singleton
    fun provideLibraryDatabase(
        app: Application,
        provider: Provider<LibraryDatabase>,
        applicationScope: CoroutineScope
    ): LibraryDatabase {
        return Room.databaseBuilder(
            app,
            LibraryDatabase::class.java,
            "library_db"
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        .fallbackToDestructiveMigration()
        .addCallback(LibraryDatabase.Callback(provider, applicationScope))
        .build()
    }

    @Provides
    @Singleton
    fun provideBookDao(db: LibraryDatabase): BookDao = db.bookDao()

    @Provides
    @Singleton
    fun provideMemberDao(db: LibraryDatabase): MemberDao = db.memberDao()

    @Provides
    @Singleton
    fun provideIssuedBookDao(db: LibraryDatabase): IssuedBookDao = db.issuedBookDao()

    @Provides
    @Singleton
    fun provideStatsQueries(db: LibraryDatabase): StatsQueries = db.statsQueries()

    @Provides
    @Singleton
    fun provideReservationDao(db: LibraryDatabase): ReservationDao = db.reservationDao()
}
