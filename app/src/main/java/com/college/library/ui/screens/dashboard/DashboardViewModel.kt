package com.college.library.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.MemberDao
import com.college.library.data.model.IssuedBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DashboardState(
    val totalBooks: Int = 0,
    val availableBooks: Int = 0,
    val issuedBooks: Int = 0,
    val totalMembers: Int = 0,
    val overdueBooks: List<IssuedBook> = emptyList(),
    val topPublishers: Map<String, Int> = emptyMap(),
    val issueTrends: Map<String, Int> = emptyMap(),
    val totalFineCollected: Double = 0.0
)

data class OverdueItem(
    val issuedBook: IssuedBook,
    val memberPhone: String
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    bookDao: BookDao,
    private val memberDao: MemberDao,
    issuedBookDao: IssuedBookDao
) : ViewModel() {

    private val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    val state: StateFlow<DashboardState> = combine(
        bookDao.getTotalCount(),
        bookDao.getAvailableCount(),
        issuedBookDao.getIssuedCount(),
        memberDao.getTotalCount(),
        issuedBookDao.getOverdueBooks(today),
        bookDao.getAllBooks(),
        issuedBookDao.getAllTransactions(),
        issuedBookDao.getTotalFineCollected(),
        memberDao.getAllMembers()
    ) { flows ->
        val totalB = flows[0] as Int
        val availB = flows[1] as Int
        val issuedB = flows[2] as Int
        val totalM = flows[3] as Int
        @Suppress("UNCHECKED_CAST")
        val overdue = flows[4] as List<IssuedBook>
        @Suppress("UNCHECKED_CAST")
        val allBooks = flows[5] as List<com.college.library.data.model.Book>
        @Suppress("UNCHECKED_CAST")
        val allTransactions = flows[6] as List<IssuedBook>
        val totalFine = flows[7] as Double
        @Suppress("UNCHECKED_CAST")
        val allMembers = flows[8] as List<com.college.library.data.model.Member>

        val publishers = allBooks
            .filter { it.publisher.isNotBlank() }
            .groupingBy { it.publisher }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .associate { it.key to it.value }

        // Calculate trends for the last 7 days
        val sevenDaysAgo = LocalDate.now().minusDays(6)
        val trendMap = mutableMapOf<String, Int>()
        for (i in 0..6) {
            val dateStr = sevenDaysAgo.plusDays(i.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            trendMap[dateStr] = 0
        }
        
        allTransactions.forEach { tx ->
            if (trendMap.containsKey(tx.issueDate)) {
                trendMap[tx.issueDate] = trendMap[tx.issueDate]!! + 1
            }
        }

        DashboardState(
            totalBooks = totalB,
            availableBooks = availB,
            issuedBooks = issuedB,
            totalMembers = totalM,
            overdueBooks = overdue,
            topPublishers = publishers,
            issueTrends = trendMap,
            totalFineCollected = totalFine
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    fun getMemberPhone(memberId: Long, members: List<com.college.library.data.model.Member>): String {
        return members.find { it.id == memberId }?.phone ?: ""
    }

    suspend fun getMemberPhoneAsync(memberId: Long): String {
        return memberDao.getMemberById(memberId)?.phone ?: ""
    }
}
