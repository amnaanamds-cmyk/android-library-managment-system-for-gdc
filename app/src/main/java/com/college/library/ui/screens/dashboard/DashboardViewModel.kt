package com.college.library.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.MemberDao
import com.college.library.data.model.IssuedBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    private val _topPublishers = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _issueTrends = MutableStateFlow<Map<String, Int>>(emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.getAllBooks().distinctUntilChanged().collect { allBooks ->
                _topPublishers.value = allBooks
                    .filter { it.publisher.isNotBlank() }
                    .groupingBy { it.publisher }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .associate { it.key to it.value }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            issuedBookDao.getAllTransactions().distinctUntilChanged().collect { allTransactions ->
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
                _issueTrends.value = trendMap
            }
        }
    }

    val state: StateFlow<DashboardState> = combine(
        bookDao.getTotalCount().distinctUntilChanged(),
        bookDao.getAvailableCount().distinctUntilChanged(),
        issuedBookDao.getIssuedCount().distinctUntilChanged(),
        memberDao.getTotalCount().distinctUntilChanged(),
        issuedBookDao.getOverdueBooks(today).distinctUntilChanged(),
        issuedBookDao.getTotalFineCollected().distinctUntilChanged(),
        _topPublishers,
        _issueTrends
    ) { flows ->
        DashboardState(
            totalBooks = flows[0] as Int,
            availableBooks = flows[1] as Int,
            issuedBooks = flows[2] as Int,
            totalMembers = flows[3] as Int,
            @Suppress("UNCHECKED_CAST")
            overdueBooks = flows[4] as List<IssuedBook>,
            totalFineCollected = flows[5] as Double,
            @Suppress("UNCHECKED_CAST")
            topPublishers = flows[6] as Map<String, Int>,
            @Suppress("UNCHECKED_CAST")
            issueTrends = flows[7] as Map<String, Int>
        )
    }
    .flowOn(Dispatchers.IO)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    fun getMemberPhone(memberId: Long, members: List<com.college.library.data.model.Member>): String {
        return members.find { it.id == memberId }?.phone ?: ""
    }

    suspend fun getMemberPhoneAsync(memberId: Long): String {
        return memberDao.getMemberById(memberId)?.phone ?: ""
    }
}
