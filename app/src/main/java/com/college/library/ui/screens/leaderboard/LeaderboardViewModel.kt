package com.college.library.ui.screens.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Member
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val memberDao: MemberDao
) : ViewModel() {
    private val _topMembers = MutableStateFlow<List<Member>>(emptyList())
    val topMembers: StateFlow<List<Member>> = _topMembers.asStateFlow()

    init {
        loadTopMembers()
    }

    private fun loadTopMembers() {
        viewModelScope.launch {
            memberDao.getTopMembers(10).collect { members ->
                _topMembers.value = members
            }
        }
    }
}
