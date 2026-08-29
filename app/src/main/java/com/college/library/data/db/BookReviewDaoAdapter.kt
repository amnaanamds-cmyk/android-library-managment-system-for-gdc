package com.college.library.data.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.college.library.data.model.BookReview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.college.library.data.db.LibraryDatabase as SQLDelightDb

class BookReviewDaoAdapter(db: SQLDelightDb) : BookReviewDao {
    private val queries = db.bookReviewQueriesQueries

    override fun getReviewsForBook(bookId: Long): Flow<List<BookReview>> {
        return queries.getReviewsForBook(bookId).asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map {
                BookReview(
                    id = it.id,
                    syncId = it.syncId,
                    bookId = it.bookId,
                    memberId = it.memberId,
                    memberName = it.memberName,
                    rating = it.rating.toInt(),
                    reviewText = it.reviewText,
                    reviewDate = it.reviewDate,
                    lastUpdated = it.lastUpdated,
                    deleted = it.deleted
                )
            }
        }
    }

    override suspend fun insertBookReview(review: BookReview) {
        queries.insertBookReview(
            syncId = review.syncId,
            bookId = review.bookId,
            memberId = review.memberId,
            memberName = review.memberName,
            rating = review.rating.toLong(),
            reviewText = review.reviewText,
            reviewDate = review.reviewDate,
            lastUpdated = review.lastUpdated,
            deleted = review.deleted
        )
    }

    override suspend fun deleteBookReview(id: Long) {
        queries.deleteBookReview(System.currentTimeMillis(), id)
    }
}
