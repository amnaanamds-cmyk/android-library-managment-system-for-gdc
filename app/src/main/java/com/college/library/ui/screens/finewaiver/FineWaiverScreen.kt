package com.college.library.ui.screens.finewaiver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gavel
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
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import com.college.library.ui.theme.*

enum class WaiverVerdict(val emoji: String, val label: String, val color: Long) {
    FULL_WAIVE("✅", "WAIVE 100%", 0xFF10B981),
    HALF_WAIVE("⚡", "WAIVE 50%", 0xFFF59E0B),
    NO_WAIVER("❌", "NO WAIVER", 0xFFEF4444)
}

data class FineWaiverEntry(
    val member: Member,
    val totalFine: Double,
    val totalBorrowed: Int,
    val returnRate: Float,
    val daysSinceLastOverdue: Long,
    val verdict: WaiverVerdict,
    val score: Int
)

@HiltViewModel
class FineWaiverViewModel @Inject constructor(
    private val memberDao: MemberDao,
    private val issuedBookDao: IssuedBookDao
) : ViewModel() {

    private val _entries = MutableStateFlow<List<FineWaiverEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _totalFines = MutableStateFlow(0.0)
    val totalFines = _totalFines.asStateFlow()

    private val _estimatedWaiver = MutableStateFlow(0.0)
    val estimatedWaiver = _estimatedWaiver.asStateFlow()

    private val _waiverEligible = MutableStateFlow(0)
    val waiverEligible = _waiverEligible.asStateFlow()

    init { analyze() }

    fun analyze() {
        viewModelScope.launch {
            _isLoading.value = true
            val today = LocalDate.now()
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE
            val allMembers = memberDao.getAllMembers().first()
            val allIssues = issuedBookDao.getAllTransactions().first()

            val result = mutableListOf<FineWaiverEntry>()

            for (member in allMembers) {
                val memberIssues = allIssues.filter { it.memberId == member.id }
                val totalFine = memberIssues.sumOf { it.fine }
                if (totalFine <= 0) continue

                val total = memberIssues.size
                val returned = memberIssues.count { it.status == "Returned" }
                val returnRate = if (total > 0) returned.toFloat() / total else 0f

                // Find days since last overdue
                val lastOverdueDate = memberIssues
                    .filter { it.status == "Issued" }
                    .mapNotNull {
                        try { LocalDate.parse(it.dueDate, fmt) } catch (_: Exception) { null }
                    }
                    .filter { it.isBefore(today) }
                    .maxOrNull()

                val daysSince = if (lastOverdueDate != null)
                    ChronoUnit.DAYS.between(lastOverdueDate, today)
                else 999L

                // Score
                var score = 0
                if (returnRate > 0.9f) score += 40
                if (total > 20) score += 20
                if (totalFine < 50) score += 20
                if (daysSince > 30) score += 20

                val verdict = when {
                    score >= 70 -> WaiverVerdict.FULL_WAIVE
                    score >= 40 -> WaiverVerdict.HALF_WAIVE
                    else -> WaiverVerdict.NO_WAIVER
                }

                result.add(
                    FineWaiverEntry(
                        member = member,
                        totalFine = totalFine,
                        totalBorrowed = total,
                        returnRate = returnRate,
                        daysSinceLastOverdue = daysSince,
                        verdict = verdict,
                        score = score
                    )
                )
            }

            result.sortByDescending { it.score }
            _entries.value = result
            _totalFines.value = result.sumOf { it.totalFine }
            _estimatedWaiver.value = result.sumOf { entry ->
                when (entry.verdict) {
                    WaiverVerdict.FULL_WAIVE -> entry.totalFine
                    WaiverVerdict.HALF_WAIVE -> entry.totalFine / 2
                    WaiverVerdict.NO_WAIVER -> 0.0
                }
            }
            _waiverEligible.value = result.count { it.verdict != WaiverVerdict.NO_WAIVER }
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FineWaiverScreen(
    onNavigateBack: () -> Unit,
    viewModel: FineWaiverViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val totalFines by viewModel.totalFines.collectAsState()
    val estimatedWaiver by viewModel.estimatedWaiver.collectAsState()
    val waiverEligible by viewModel.waiverEligible.collectAsState()
    var selectedEntry by remember { mutableStateOf<FineWaiverEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚖️ AI Fine Waiver Judge", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.analyze() }) {
                        Icon(Icons.Default.Gavel, "Re-analyze", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Summary cards
            if (!isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WaiverSummaryCard("💰 Total Fines", "Rs. ${String.format("%.0f", totalFines)}", Danger, Modifier.weight(1f))
                    WaiverSummaryCard("💚 Can Waive", "Rs. ${String.format("%.0f", estimatedWaiver)}", Positive, Modifier.weight(1f))
                    WaiverSummaryCard("✅ Eligible", "$waiverEligible members", Color(0xFF3B82F6), Modifier.weight(1f))
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("AI Judge is analyzing member profiles...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("🎉 No pending fines found!", color = Positive, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(entries) { entry ->
                        FineWaiverCard(
                            entry = entry,
                            isSelected = selectedEntry == entry,
                            onClick = { selectedEntry = if (selectedEntry == entry) null else entry }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FineWaiverCard(entry: FineWaiverEntry, isSelected: Boolean, onClick: () -> Unit) {
    val verdictColor = Color(entry.verdict.color)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, verdictColor) else null,
        elevation = CardDefaults.cardElevation(if (isSelected) 6.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.member.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("ID: ${entry.member.memberId} • ${entry.member.memberType}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = verdictColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        "${entry.verdict.emoji} ${entry.verdict.label}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = verdictColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // Stats row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                FineStatChip("💰 Fine", "Rs. ${String.format("%.0f", entry.totalFine)}")
                FineStatChip("📚 Borrowed", "${entry.totalBorrowed}")
                FineStatChip("↩️ Return Rate", "${(entry.returnRate * 100).toInt()}%")
                FineStatChip("🏅 Score", "${entry.score}/100")
            }

            // Expanded explanation
            if (isSelected) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = buildString {
                        append("🤖 AI Verdict Explanation:\n")
                        append("Score ${entry.score}/100. ")
                        if (entry.returnRate > 0.9f) append("Excellent return rate. ")
                        if (entry.totalBorrowed > 20) append("Highly active reader. ")
                        if (entry.totalFine < 50) append("Small fine — likely accidental. ")
                        if (entry.daysSinceLastOverdue > 30) append("No recent overdue. ")
                        when (entry.verdict) {
                            WaiverVerdict.FULL_WAIVE -> append("\n✅ Recommendation: Full waiver granted — model member with excellent history.")
                            WaiverVerdict.HALF_WAIVE -> append("\n⚡ Recommendation: 50% waiver — good standing but some concerns remain.")
                            WaiverVerdict.NO_WAIVER -> append("\n❌ Recommendation: No waiver — history does not meet eligibility criteria.")
                        }
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FineStatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun WaiverSummaryCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Column(modifier = Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
