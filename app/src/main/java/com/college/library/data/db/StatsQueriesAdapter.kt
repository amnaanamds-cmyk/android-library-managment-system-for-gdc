package com.college.library.data.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.college.library.data.db.LibraryDatabase as SQLDelightDb

// Drop-in replacement for the Room StatsQueries @Dao interface.
class StatsQueriesAdapter(private val db: SQLDelightDb) : StatsQueries {

    private val queries get() = db.appStatsQueriesQueries

    override fun getBookCountByCategory(): Flow<List<CategoryCount>> =
        queries.getBookCountByCategory().asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map { CategoryCount(it.category, it.count.toInt()) } }

    override fun getTotalCollectionValue(): Flow<Double> =
        queries.getTotalCollectionValue().asFlow().mapToOneOrNull(Dispatchers.IO)
            .map { it ?: 0.0 }

    override suspend fun getMostPopularAuthorRaw(): CategoryCount? = withContext(Dispatchers.IO) {
        queries.getMostPopularAuthorRaw().executeAsOneOrNull()
            ?.let { CategoryCount(it.category, it.count.toInt()) }
    }

    override suspend fun getMonthlyIssueCounts(): List<MonthlyIssueCount> = withContext(Dispatchers.IO) {
        queries.getMonthlyIssueCounts().executeAsList()
            .map { MonthlyIssueCount(it.month, it.count.toInt()) }
    }

    override suspend fun getAverageBorrowDuration(): Double? = withContext(Dispatchers.IO) {
        queries.getAverageBorrowDuration().executeAsOneOrNull()?.averageDuration
    }

    override suspend fun getTopBorrowers(): List<BorrowerActivity> = withContext(Dispatchers.IO) {
        queries.getTopBorrowers().executeAsList()
            .map { BorrowerActivity(it.memberName, it.memberId, it.issueCount.toInt()) }
    }

    override suspend fun getDigitalBookCount(): Int = withContext(Dispatchers.IO) {
        queries.getDigitalBookCount().executeAsOne().toInt()
    }

    override suspend fun getPhysicalBookCount(): Int = withContext(Dispatchers.IO) {
        queries.getPhysicalBookCount().executeAsOne().toInt()
    }

    override suspend fun getTotalFinesCollected(): Double = withContext(Dispatchers.IO) {
        queries.getTotalFinesCollected().executeAsOne()
    }

    override suspend fun getMonthlyReturnCounts(): List<MonthlyIssueCount> = withContext(Dispatchers.IO) {
        queries.getMonthlyReturnCounts().executeAsList()
            .map { MonthlyIssueCount(it.month ?: "", it.count.toInt()) }
    }
}
