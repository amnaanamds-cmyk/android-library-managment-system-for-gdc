package com.college.library.data.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.college.library.data.model.BookRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.college.library.data.db.LibraryDatabase as SQLDelightDb

class BookRequestDaoAdapter(db: SQLDelightDb) : BookRequestDao {
    private val queries = db.bookRequestQueriesQueries

    override fun getAllBookRequests(): Flow<List<BookRequest>> {
        return queries.getAllBookRequests().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map {
                BookRequest(
                    id = it.id,
                    syncId = it.syncId,
                    title = it.title,
                    author = it.author,
                    memberId = it.memberId,
                    memberName = it.memberName,
                    requestDate = it.requestDate,
                    status = it.status,
                    lastUpdated = it.lastUpdated,
                    deleted = it.deleted
                )
            }
        }
    }

    override suspend fun insertBookRequest(request: BookRequest) {
        queries.insertBookRequest(
            syncId = request.syncId,
            title = request.title,
            author = request.author,
            memberId = request.memberId,
            memberName = request.memberName,
            requestDate = request.requestDate,
            status = request.status,
            lastUpdated = request.lastUpdated,
            deleted = request.deleted
        )
    }

    override suspend fun updateBookRequestStatus(id: Long, status: String) {
        queries.updateBookRequestStatus(status, System.currentTimeMillis(), id)
    }

    override suspend fun deleteBookRequest(id: Long) {
        queries.deleteBookRequest(System.currentTimeMillis(), id)
    }
}
