package com.college.library.data.db

import kotlinx.coroutines.flow.Flow

// Projection data classes (unchanged — StatsViewModel still imports these)
data class CategoryCount(val category: String, val count: Int)
data class MonthlyIssueCount(val month: String, val count: Int)
data class BorrowerActivity(val memberName: String, val memberId: String, val issueCount: Int)

// Plain interface — backed by StatsQueriesAdapter at runtime via DI.
interface StatsQueries {
    fun getBookCountByCategory(): Flow<List<CategoryCount>>
    fun getTotalCollectionValue(): Flow<Double>
    suspend fun getMostPopularAuthorRaw(): CategoryCount?
    suspend fun getMonthlyIssueCounts(): List<MonthlyIssueCount>
    suspend fun getAverageBorrowDuration(): Double?
    suspend fun getTopBorrowers(): List<BorrowerActivity>
    suspend fun getDigitalBookCount(): Int
    suspend fun getPhysicalBookCount(): Int
    suspend fun getTotalFinesCollected(): Double
    suspend fun getMonthlyReturnCounts(): List<MonthlyIssueCount>
}
