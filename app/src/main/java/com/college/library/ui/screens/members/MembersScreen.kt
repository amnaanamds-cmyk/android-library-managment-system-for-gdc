package com.college.library.ui.screens.members

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Member
import com.college.library.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val memberDao: MemberDao
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow("All")
    val filter = _filter.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val members: StateFlow<List<Member>> = combine(_searchQuery, _filter) { query, filter ->
        Pair(query, filter)
    }.flatMapLatest { (query, filter) ->
        val flow = if (query.isBlank()) memberDao.getAllMembers() else memberDao.searchMembers(query)
        flow.map { list ->
            if (filter == "All") list else list.filter { it.memberType == filter }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun updateFilter(filter: String) { _filter.value = filter }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    onNavigateToAddMember: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    viewModel: MembersViewModel = hiltViewModel(),
    authViewModel: com.college.library.ui.screens.auth.AuthViewModel = hiltViewModel()
) {
    val members by viewModel.members.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val canEdit = authViewModel.canEditMembers()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Members", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
            )
        },
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(onClick = onNavigateToAddMember, containerColor = NavyBlue, contentColor = Color.White) {
                    Icon(Icons.Default.Add, contentDescription = "Add Member")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search name, ID, email...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Student", "Faculty", "Staff").forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { viewModel.updateFilter(option) },
                        label = { Text(option) }
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(members, key = { it.id }) { member ->
                    MemberCard(member = member, onClick = { onNavigateToDetail(member.id) })
                }
            }
        }
    }
}

@Composable
fun MemberCard(member: Member, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(member.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Dept: ${member.department}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Surface(color = LightNavy.copy(alpha = 0.1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {
                        Text(member.memberId, modifier = Modifier.padding(4.dp), color = NavyBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = CardPurple.copy(alpha = 0.1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {
                        Text(member.memberType, modifier = Modifier.padding(4.dp), color = CardPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(member.booksIssued.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CardOrange)
                Text("Books", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
