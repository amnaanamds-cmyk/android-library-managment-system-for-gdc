package com.college.library.data.db

import com.college.library.data.model.BookReview
import kotlinx.coroutines.flow.Flow

interface BookReviewDao {
    fun getReviewsForBook(bookId: Long): Flow<List<BookReview>>
    suspend fun insertBookReview(review: BookReview)
    suspend fun deleteBookReview(id: Long)
}
