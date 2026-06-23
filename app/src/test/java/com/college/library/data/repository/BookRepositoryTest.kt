package com.college.library.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.college.library.data.db.BookDao
import com.college.library.data.db.LibraryDatabase
import com.college.library.data.model.Book
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookRepositoryTest {

    private lateinit var db: LibraryDatabase
    private lateinit var bookDao: BookDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, LibraryDatabase::class.java
        ).allowMainThreadQueries().build()
        bookDao = db.bookDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testAllCrudOperations() = runTest {
        val book = Book(
            title = "Test Book", isbn = "123456", status = "Available",
            accNo = "A1", author = "Author A", publisher = "Pub P",
            publisherPlace = "Place", publishDate = "2026", edition = "1st",
            pages = 200, procurement = "Purchase", volume = "1", price = 50.0
        )
        
        // Create
        bookDao.insertBook(book)
        
        // Read via Flow (wait for at least 1 item since we just inserted)
        val loaded = bookDao.getAllBooks().first().first()
        assertNotNull(loaded)
        assertEquals("Test Book", loaded.title)
        
        val loadedById = bookDao.getBookById(loaded.id)
        assertNotNull(loadedById)
        assertEquals("Test Book", loadedById?.title)
        
        // Update
        bookDao.updateBook(loaded.copy(title = "Updated Title"))
        val updated = bookDao.getBookById(loaded.id)
        assertEquals("Updated Title", updated?.title)

        // Status Update
        bookDao.updateBookStatus(loaded.id, "Issued")
        val issuedBook = bookDao.getBookById(loaded.id)
        assertEquals("Issued", issuedBook?.status)
        
        // Delete
        bookDao.deleteBook(issuedBook!!)
        val deleted = bookDao.getBookById(loaded.id)
        assertNull(deleted)
    }
}
