package com.college.library.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.college.library.data.model.Book
import com.college.library.data.model.IssuedBook
import com.college.library.data.model.Member
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Provider

// Migration from v1 → v2: adds isDigital and digitalUrl columns to the books table
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE books ADD COLUMN isDigital INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE books ADD COLUMN digitalUrl TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE members ADD COLUMN fatherName TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE members ADD COLUMN className TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE members ADD COLUMN classNo TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE members ADD COLUMN address TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE members ADD COLUMN photoUri TEXT")
        database.execSQL("ALTER TABLE members ADD COLUMN designation TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE members ADD COLUMN bps TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE members ADD COLUMN pin TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE books ADD COLUMN category TEXT NOT NULL DEFAULT 'Uncategorized'")
    }
}

@Database(
    entities = [Book::class, Member::class, IssuedBook::class],
    version = 5,
    exportSchema = true
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun memberDao(): MemberDao
    abstract fun issuedBookDao(): IssuedBookDao

    class Callback(
        private val databaseProvider: Provider<LibraryDatabase>,
        private val applicationScope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            applicationScope.launch(Dispatchers.IO) {
                val database = databaseProvider.get()
                DataSeeder.seedBooks(database)
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            applicationScope.launch(Dispatchers.IO) {
                db.execSQL("UPDATE books SET status = 'Available' WHERE status IS NULL OR TRIM(status) = ''")
            }
        }
    }
}
