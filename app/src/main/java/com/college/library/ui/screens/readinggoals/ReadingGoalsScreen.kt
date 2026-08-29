package com.college.library.ui.screens.readinggoals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Member
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReadingGoalEntry(
    val member: Member,
    val targetBooks: Int,
    val currentBooks: Int,
    val streakDays: Int,
    val badge: String
)

@HiltViewModel
class ReadingGoalsViewModel @Inject constructor(
    private val memberDao: MemberDao,
    private val issuedBookDao: IssuedBookDao
) : ViewModel() {

    private val _goals = MutableStateFlow<List<ReadingGoalEntry>>(emptyList())
    val goals = _goals.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // In-memory goal targets (member id -> target)
    private val goalTargets = mutableMapOf<Long, Int>()

    init { loadGoals() }

    fun setGoal(memberId: Long, target: Int) {
        goalTargets[memberId] = target
        loadGoals()
    }

    fun loadGoals() {
        viewModelScope.launch {
            _isLoading.value = true
            val currentYear = LocalDate.now().year
            val yearStart = "${currentYear}-01-01"
            val allMembers = memberDao.getAllMembers().first()
            val allIssues = issuedBookDao.getAllTransactions().first()

            val result = allMembers.map { member ->
                val memberIssues = allIssues.filter { it.memberId == member.id }
                val thisYearIssues = memberIssues.filter { it.issueDate >= yearStart }
                val target = goalTargets[member.id] ?: 10 // default 10 books/year

                // Calculate streak (consecutive days with a book issued)
                val issueDates = memberIssues.mapNotNull {
                    try { LocalDate.parse(it.issueDate) } catch (_: Exception) { null }
                }.distinct().sortedDescending()

                var streak = 0
                var checkDate = LocalDate.now()
                for (d in issueDates) {
                    if (d == checkDate || d == checkDate.minusDays(1)) {
                        streak++
                        checkDate = d
                    } else break
                }

                val progress = thisYearIssues.size
                val percent = progress.toFloat() / target
                val badge = when {
                    percent >= 1.0f -> "🏆 Platinum"
                    percent >= 0.75f -> "🥇 Gold"
                    percent >= 0.5f -> "🥈 Silver"
                    percent >= 0.25f -> "🥉 Bronze"
                    else -> "📖 Starter"
                }

                ReadingGoalEntry(
                    member = member,
                    targetBooks = target,
                    currentBooks = progress,
                    streakDays = streak,
                    badge = badge
                )
            }.sortedByDescending { it.currentBooks }

            _goals.value = result
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingGoalsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReadingGoalsViewModel = hiltViewModel()
) {
    val goals by viewModel.goals.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showSetGoalDialog by remember { mutableStateOf(false) }
    var selectedMember by remember { mutableStateOf<ReadingGoalEntry?>(null) }
    var goalInput by remember { mutableStateOf("10") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎯 Reading Goals & Streaks", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadGoals() }) {
                        Icon(Icons.Default.EmojiEvents, "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSetGoalDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Set Goal", tint = Color.White)
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    // Leaderboard header
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Annual Reading Challenge ${LocalDate.now().year}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Track member reading goals and streaks", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }

                items(goals.withIndex().toList()) { (index, goal) ->
                    ReadingGoalCard(
                        rank = index + 1,
                        entry = goal,
                        onSetGoal = { selectedMember = goal; goalInput = goal.targetBooks.toString(); showSetGoalDialog = true }
                    )
                }
            }
        }
    }

    if (showSetGoalDialog && selectedMember != null) {
        AlertDialog(
            onDismissRequest = { showSetGoalDialog = false },
            icon = { Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFF59E0B)) },
            title = { Text("Set Reading Goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("For: ${selectedMember!!.member.name}", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { if (it.all { c -> c.isDigit() }) goalInput = it },
                        label = { Text("Annual target (books)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = goalInput.toIntOrNull() ?: 10
                    viewModel.setGoal(selectedMember!!.member.id, target)
                    showSetGoalDialog = false
                }) { Text("Save Goal") }
            },
            dismissButton = {
                TextButton(onClick = { showSetGoalDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ReadingGoalCard(rank: Int, entry: ReadingGoalEntry, onSetGoal: () -> Unit) {
    val progress = (entry.currentBooks.toFloat() / entry.targetBooks).coerceIn(0f, 1f)
    val progressColor = when {
        progress >= 1f -> Color(0xFF10B981)
        progress >= 0.5f -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Rank badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            when (rank) {
                                1 -> Color(0xFFF59E0B)
                                2 -> Color(0xFF9CA3AF)
                                3 -> Color(0xFFCD7F32)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (rank <= 3) listOf("🥇", "🥈", "🥉")[rank - 1] else "#$rank",
                        fontSize = if (rank <= 3) 18.sp else 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.member.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${entry.member.department} • ${entry.member.memberType}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(entry.badge, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Progress: ${entry.currentBooks}/${entry.targetBooks} books", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${(progress * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = progressColor)
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // Streak
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${entry.streakDays} day streak", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444))
                }
                OutlinedButton(onClick = onSetGoal, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("Set Goal", fontSize = 11.sp)
                }
            }
        }
    }
}
