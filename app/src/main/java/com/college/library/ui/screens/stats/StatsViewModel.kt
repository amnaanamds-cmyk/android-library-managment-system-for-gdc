package com.college.library.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BorrowerActivity
import com.college.library.data.db.CategoryCount
import com.college.library.data.db.MemberDao
import com.college.library.data.db.MonthlyIssueCount
import com.college.library.data.db.StatsQueries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryStatsState(
    val isLoading: Boolean = true,
    val totalCollectionValue: Double = 0.0,
    val booksByCategory: List<CategoryCount> = emptyList(),
    val topBorrowers: List<BorrowerActivity> = emptyList(),
    val monthlyIssues: List<MonthlyIssueCount> = emptyList(),
    val monthlyReturns: List<MonthlyIssueCount> = emptyList(),
    val averageBorrowDuration: Double = 0.0,
    val totalFinesCollected: Double = 0.0,
    val totalBooks: Int = 0,
    val totalMembers: Int = 0,
    val digitalBookCount: Int = 0,
    val physicalBookCount: Int = 0,
    val mostPopularAuthor: String = "N/A"
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsQueries: StatsQueries,
    private val memberDao: MemberDao
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryStatsState())
    val state: StateFlow<LibraryStatsState> = _state.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            try {
                val collectionValue = statsQueries.getTotalCollectionValue().first()
                val categories = statsQueries.getBookCountByCategory().first()
                val topBorrowers = statsQueries.getTopBorrowers()
                val monthlyIssues = statsQueries.getMonthlyIssueCounts().reversed()
                val monthlyReturns = statsQueries.getMonthlyReturnCounts().reversed()
                val avgDuration = statsQueries.getAverageBorrowDuration() ?: 0.0
                val fines = statsQueries.getTotalFinesCollected()
                val digital = statsQueries.getDigitalBookCount()
                val physical = statsQueries.getPhysicalBookCount()
                val popularAuthor = statsQueries.getMostPopularAuthorRaw()?.category ?: "N/A"
                val totalMembers = memberDao.getTotalCount().first()

                _state.value = LibraryStatsState(
                    isLoading = false,
                    totalCollectionValue = collectionValue,
                    booksByCategory = categories,
                    topBorrowers = topBorrowers,
                    monthlyIssues = monthlyIssues,
                    monthlyReturns = monthlyReturns,
                    averageBorrowDuration = avgDuration,
                    totalFinesCollected = fines,
                    totalBooks = digital + physical,
                    totalMembers = totalMembers,
                    digitalBookCount = digital,
                    physicalBookCount = physical,
                    mostPopularAuthor = popularAuthor
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(isLoading = true)
        loadStats()
    }
}
