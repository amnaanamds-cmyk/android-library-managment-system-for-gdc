package com.college.library.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CategoryCount(
    val category: String,
    val count: Int
)

data class MonthlyIssueCount(
    val month: String,
    val count: Int
)

data class BorrowerActivity(
    val memberName: String,
    val memberId: String,
    val issueCount: Int
)

@Dao
interface StatsQueries {

    @Query("SELECT category, COUNT(*) as count FROM books GROUP BY category ORDER BY count DESC")
    fun getBookCountByCategory(): Flow<List<CategoryCount>>

    @Query("SELECT COALESCE(SUM(price), 0.0) FROM books")
    fun getTotalCollectionValue(): Flow<Double>

    @Query(
        "SELECT author as category, COUNT(*) as count FROM books " +
        "WHERE author != '' GROUP BY author ORDER BY count DESC LIMIT 1"
    )
    suspend fun getMostPopularAuthorRaw(): CategoryCount?

    @Query(
        "SELECT substr(issueDate, 1, 7) as month, COUNT(*) as count " +
        "FROM issued_books " +
        "GROUP BY substr(issueDate, 1, 7) " +
        "ORDER BY month DESC LIMIT 6"
    )
    suspend fun getMonthlyIssueCounts(): List<MonthlyIssueCount>

    @Query(
        "SELECT AVG(julianday(returnDate) - julianday(issueDate)) " +
        "FROM issued_books WHERE returnDate IS NOT NULL AND status = 'Returned'"
    )
    suspend fun getAverageBorrowDuration(): Double?

    @Query(
        "SELECT memberName, memberMemberId as memberId, COUNT(*) as issueCount " +
        "FROM issued_books GROUP BY memberId " +
        "ORDER BY issueCount DESC LIMIT 5"
    )
    suspend fun getTopBorrowers(): List<BorrowerActivity>

    @Query("SELECT COUNT(*) FROM books WHERE isDigital = 1")
    suspend fun getDigitalBookCount(): Int

    @Query("SELECT COUNT(*) FROM books WHERE isDigital = 0")
    suspend fun getPhysicalBookCount(): Int

    @Query("SELECT COALESCE(SUM(fine), 0.0) FROM issued_books WHERE status = 'Returned'")
    suspend fun getTotalFinesCollected(): Double

    @Query(
        "SELECT substr(returnDate, 1, 7) as month, COUNT(*) as count " +
        "FROM issued_books WHERE returnDate IS NOT NULL " +
        "GROUP BY substr(returnDate, 1, 7) " +
        "ORDER BY month DESC LIMIT 6"
    )
    suspend fun getMonthlyReturnCounts(): List<MonthlyIssueCount>
}
