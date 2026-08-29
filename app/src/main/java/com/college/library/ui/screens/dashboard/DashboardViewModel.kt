package com.college.library.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.MemberDao
import com.college.library.data.db.LibraryDatabase
import com.college.library.data.model.IssuedBook
import com.college.library.profile.CollegeProfile
import com.college.library.profile.CollegeProfileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
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
    val totalFineCollected: Double = 0.0,
    val collegeProfile: CollegeProfile = CollegeProfile()
)

data class OverdueItem(
    val issuedBook: IssuedBook,
    val memberPhone: String
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val memberDao: MemberDao,
    private val issuedBookDao: IssuedBookDao,
    val database: LibraryDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val profileManager = CollegeProfileManager.getInstance(context)
    private val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private val _topPublishers = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _issueTrends = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _collegeProfile = MutableStateFlow(profileManager.getProfile())

    init {
        refreshProfile()
        // Periodic sync is good for keeping data fresh while the dashboard is open.
        startPeriodicSync()
        
        // Removed redundancy: startRealtimeSync is already called in MainActivity's 
        // LibraryApp component whenever isAuthenticated is true.

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

    fun refreshProfile() {
        _collegeProfile.value = profileManager.getProfile()
    }

    private fun startPeriodicSync() {
        val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val instId = authPrefs.getString("institution_id", "gdc11") ?: "gdc11"
        
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val syncService = com.college.library.data.SyncManager.getSyncService(database)
                    syncService.currentInstitutionId = instId
                    syncService.startFullSync()
                } catch (e: Exception) {
                    // Fail silently in background
                }
                kotlinx.coroutines.delay(120000) // Every 2 minutes
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    val state: StateFlow<DashboardState> = combine(
        bookDao.getTotalCount().distinctUntilChanged(),
        bookDao.getAvailableCount().distinctUntilChanged(),
        issuedBookDao.getIssuedCount().distinctUntilChanged(),
        memberDao.getTotalCount().distinctUntilChanged(),
        issuedBookDao.getOverdueBooks(today).distinctUntilChanged(),
        issuedBookDao.getTotalFineCollected().distinctUntilChanged(),
        _topPublishers,
        _issueTrends,
        _collegeProfile
    ) { flows ->
        DashboardState(
            totalBooks = flows[0] as Int,
            availableBooks = flows[1] as Int,
            issuedBooks = flows[2] as Int,
            totalMembers = flows[3] as Int,
            overdueBooks = flows[4] as List<IssuedBook>,
            totalFineCollected = flows[5] as Double,
            topPublishers = flows[6] as Map<String, Int>,
            issueTrends = flows[7] as Map<String, Int>,
            collegeProfile = flows[8] as CollegeProfile
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
