package com.college.library.data.db

import com.college.library.data.model.BookRequest
import kotlinx.coroutines.flow.Flow

interface BookRequestDao {
    fun getAllBookRequests(): Flow<List<BookRequest>>
    suspend fun insertBookRequest(request: BookRequest)
    suspend fun updateBookRequestStatus(id: Long, status: String)
    suspend fun deleteBookRequest(id: Long)
}
