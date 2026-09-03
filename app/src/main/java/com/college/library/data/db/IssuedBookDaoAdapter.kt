package com.college.library.data.db

import com.college.library.data.db.adapters.toModel
import com.college.library.data.model.IssuedBook
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.college.library.data.db.LibraryDatabase as SQLDelightDb
import java.util.UUID

// Drop-in replacement for the Room IssuedBookDao interface.
class IssuedBookDaoAdapter(private val db: SQLDelightDb) : IssuedBookDao {

    private val queries get() = db.issuedBookQueriesQueries

    override fun getCurrentlyIssuedBooks(): Flow<List<IssuedBook>> =
        queries.getCurrentlyIssuedBooks().asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override fun getOverdueBooks(today: String): Flow<List<IssuedBook>> =
        queries.getOverdueBooks(today).asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override fun getAllTransactions(): Flow<List<IssuedBook>> =
        queries.getAllTransactions().asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override suspend fun getIssuedBookById(id: Long): IssuedBook? = withContext(Dispatchers.IO) {
        queries.getIssuedBookById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun insertIssuedBook(issuedBook: IssuedBook): Unit = withContext(Dispatchers.IO) {
        val syncId = if (issuedBook.syncId.isBlank()) UUID.randomUUID().toString() else issuedBook.syncId
        queries.insertIssuedBook(
            syncId = syncId,
            bookId = issuedBook.bookId, bookTitle = issuedBook.bookTitle,
            bookIsbn = issuedBook.bookIsbn, memberId = issuedBook.memberId,
            memberName = issuedBook.memberName, memberMemberId = issuedBook.memberMemberId,
            issueDate = issuedBook.issueDate, dueDate = issuedBook.dueDate,
            returnDate = issuedBook.returnDate, fine = issuedBook.fine,
            status = issuedBook.status,
            lastUpdated = System.currentTimeMillis(), deleted = false,
            syncStatus = "pending", // Mark pending so the push engine will upload this issue record
            collegeId = issuedBook.collegeId
        )
        runCatching { com.college.library.data.SyncManager.getSyncService(db).pushChanges() }
        Unit
    }

    override suspend fun returnBook(id: Long, returnDate: String, fine: Double): Unit = withContext(Dispatchers.IO) {
        // returnBook SQL only updates returnDate/fine/status, not syncStatus. We mark the record pending
        // by calling the full insertIssuedBook (INSERT OR REPLACE) instead so syncStatus gets written.
        val existing = queries.getIssuedBookById(id).executeAsOneOrNull()
        if (existing != null) {
            queries.insertIssuedBook(
                syncId = existing.syncId,
                bookId = existing.bookId, bookTitle = existing.bookTitle,
                bookIsbn = existing.bookIsbn, memberId = existing.memberId,
                memberName = existing.memberName, memberMemberId = existing.memberMemberId,
                issueDate = existing.issueDate, dueDate = existing.dueDate,
                returnDate = returnDate, fine = fine,
                status = "Returned",
                lastUpdated = System.currentTimeMillis(), deleted = existing.deleted,
                syncStatus = "pending",
                collegeId = existing.collegeId
            )
        } else {
            queries.returnBook(returnDate = returnDate, fine = fine, id = id, lastUpdated = System.currentTimeMillis())
        }
        runCatching { com.college.library.data.SyncManager.getSyncService(db).pushChanges() }
        Unit
    }

    override fun getIssuedCount(): Flow<Int> =
        queries.getIssuedCount().asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.toInt() ?: 0 }

    override fun getTotalFineCollected(): Flow<Double> =
        queries.getTotalFineCollected().asFlow().mapToOneOrNull(Dispatchers.IO).map { it ?: 0.0 }

    override fun getCurrentlyIssuedBooksByMember(memberId: Long): Flow<List<IssuedBook>> =
        queries.getCurrentlyIssuedBooksByMember(memberId).asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override fun getReturnedBooksByMember(memberId: Long): Flow<List<IssuedBook>> =
        queries.getReturnedBooksByMember(memberId).asFlow().mapToList(Dispatchers.IO).map { it.map { row -> row.toModel() } }

    override fun getTotalFineByMember(memberId: Long): Flow<Double> =
        queries.getTotalFineByMember(memberId).asFlow().mapToOneOrNull(Dispatchers.IO).map { it ?: 0.0 }
}
