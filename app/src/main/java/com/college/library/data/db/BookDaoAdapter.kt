package com.college.library.data.db

import com.college.library.data.db.adapters.toModel
import com.college.library.data.model.Book
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.college.library.data.db.LibraryDatabase as SQLDelightDb
import java.util.UUID

// Drop-in replacement for the Room BookDao interface.
// All ViewModels continue to inject BookDao — zero changes needed in UI layer.
class BookDaoAdapter(private val db: SQLDelightDb) : BookDao {

    private val queries get() = db.bookQueriesQueries

    override fun getAllBooks(): Flow<List<Book>> =
        queries.getAllBooks().asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override suspend fun getAllBooksStatic(): List<Book> = withContext(Dispatchers.IO) {
        queries.getAllBooksStatic().executeAsList().map { it.toModel() }
    }

    override fun getAvailableBooks(): Flow<List<Book>> =
        queries.getAvailableBooks().asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override fun searchBooks(query: String): Flow<List<Book>> =
        queries.searchBooks(query).asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override suspend fun getBookById(id: Long): Book? = withContext(Dispatchers.IO) {
        queries.getBookById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun insertBook(book: Book) {
        withContext(Dispatchers.IO) {
            val syncId = if (book.syncId.isBlank()) UUID.randomUUID().toString() else book.syncId
            queries.insertBook(
                syncId = syncId,
                isbn = book.isbn, accNo = book.accNo, title = book.title,
                author = book.author, publisher = book.publisher,
                publisherPlace = book.publisherPlace, publishDate = book.publishDate,
                edition = book.edition, pages = book.pages.toLong(),
                procurement = book.procurement, volume = book.volume,
                price = book.price, status = book.status,
                isDigital = book.isDigital, digitalUrl = book.digitalUrl,
                category = book.category,
                marcData = book.marcData,
                lastUpdated = System.currentTimeMillis(),
                deleted = false
            )
            runCatching { com.college.library.data.SyncManager.getSyncService(db).pushChanges() }
        }
    }

    override suspend fun updateBook(book: Book) {
        withContext(Dispatchers.IO) {
            queries.updateBook(
                syncId = book.syncId, isbn = book.isbn, accNo = book.accNo,
                title = book.title, author = book.author, publisher = book.publisher,
                publisherPlace = book.publisherPlace, publishDate = book.publishDate,
                edition = book.edition, pages = book.pages.toLong(),
                procurement = book.procurement, volume = book.volume,
                price = book.price, status = book.status,
                isDigital = book.isDigital, digitalUrl = book.digitalUrl,
                category = book.category,
                marcData = book.marcData,
                lastUpdated = System.currentTimeMillis(),
                deleted = book.deleted,
                id = book.id
            )
            runCatching { com.college.library.data.SyncManager.getSyncService(db).pushChanges() }
        }
    }

    override suspend fun deleteBook(book: Book) {
        withContext(Dispatchers.IO) {
            queries.deleteBook(book.id)
            runCatching { com.college.library.data.SyncManager.getSyncService(db).pushChanges() }
        }
    }

    override suspend fun updateBookStatus(id: Long, status: String) {
        withContext(Dispatchers.IO) {
            queries.updateBookStatus(status = status, id = id, lastUpdated = System.currentTimeMillis())
            runCatching { com.college.library.data.SyncManager.getSyncService(db).pushChanges() }
        }
    }

    override fun getTotalCount(): Flow<Int> =
        queries.getTotalCount().asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.toInt() ?: 0 }

    override fun getAvailableCount(): Flow<Int> =
        queries.getAvailableCount().asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.toInt() ?: 0 }

    override fun getCategories(): Flow<List<String>> =
        queries.getCategories().asFlow().mapToList(Dispatchers.IO)

    override fun getBooksByCategory(category: String): Flow<List<Book>> =
        queries.getBooksByCategory(category).asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }
}
