package com.college.library.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.college.library.data.db.BorrowerActivity
import com.college.library.data.db.CategoryCount
import com.college.library.data.db.MonthlyIssueCount
import com.college.library.ui.theme.CardBlue
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.CardOrange
import com.college.library.ui.theme.CardPurple
import com.college.library.ui.theme.Gold
import com.college.library.ui.theme.LightNavy
import com.college.library.ui.theme.NavyBlue
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryStatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library Analytics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NavyBlue)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                // Hero: Total Collection Value
                item { CollectionValueHero(value = state.totalCollectionValue) }

                // Quick Stats Row
                item { QuickStatsRow(state) }

                // Books by Category
                item {
                    SectionTitle(icon = Icons.Default.BarChart, title = "Books by Category")
                }
                item { CategoryBarChart(categories = state.booksByCategory) }

                // Most Active Borrowers
                item {
                    SectionTitle(icon = Icons.Default.Groups, title = "Most Active Borrowers")
                }
                item { TopBorrowersSection(borrowers = state.topBorrowers) }

                // Monthly Issue/Return Trends
                item {
                    SectionTitle(icon = Icons.Default.BarChart, title = "Monthly Trends (Last 6 Months)")
                }
                item {
                    MonthlyTrendsChart(
                        issues = state.monthlyIssues,
                        returns = state.monthlyReturns
                    )
                }

                // Additional Stats Cards
                item {
                    SectionTitle(icon = Icons.Default.Star, title = "Key Insights")
                }
                item { InsightsSection(state) }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun CollectionValueHero(value: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyBlue)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AutoStories,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Total Collection Value",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatCurrency(value),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Gold
            )
        }
    }
}

@Composable
private fun QuickStatsRow(state: LibraryStatsState) {
    val ratio = if (state.totalMembers > 0) {
        String.format(Locale.getDefault(), "%.1f", state.totalBooks.toDouble() / state.totalMembers)
    } else "0"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.MenuBook,
            label = "Books/Member",
            value = ratio,
            color = CardBlue
        )
        QuickStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Devices,
            label = "Digital",
            value = "${state.digitalBookCount}",
            color = CardPurple
        )
        QuickStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Schedule,
            label = "Avg Days",
            value = String.format(Locale.getDefault(), "%.0f", state.averageBorrowDuration),
            color = CardOrange
        )
    }
}

@Composable
private fun QuickStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NavyBlue,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = NavyBlue
        )
    }
}

@Composable
private fun CategoryBarChart(categories: List<CategoryCount>) {
    if (categories.isEmpty()) {
        EmptyDataCard("No category data available")
        return
    }
    val maxCount = categories.maxOfOrNull { it.count } ?: 1
    val barColors = listOf(CardBlue, CardGreen, CardOrange, CardPurple, LightNavy, Gold)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            categories.forEachIndexed { index, cat ->
                val fraction = cat.count.toFloat() / maxCount
                val color = barColors[index % barColors.size]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cat.category,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(90.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${cat.count}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBorrowersSection(borrowers: List<BorrowerActivity>) {
    if (borrowers.isEmpty()) {
        EmptyDataCard("No borrowing data available")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        borrowers.forEachIndexed { index, borrower ->
            BorrowerCard(rank = index + 1, borrower = borrower)
        }
    }
}

@Composable
private fun BorrowerCard(rank: Int, borrower: BorrowerActivity) {
    val rankColor = when (rank) {
        1 -> Gold
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> LightNavy
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(rankColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = rankColor
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = borrower.memberName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "ID: ${borrower.memberId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${borrower.issueCount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NavyBlue
                )
                Text(
                    text = "books",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun MonthlyTrendsChart(
    issues: List<MonthlyIssueCount>,
    returns: List<MonthlyIssueCount>
) {
    if (issues.isEmpty() && returns.isEmpty()) {
        EmptyDataCard("No monthly trend data available")
        return
    }

    val allMonths = (issues.map { it.month } + returns.map { it.month }).distinct().sorted()
    val issueMap = issues.associate { it.month to it.count }
    val returnMap = returns.associate { it.month to it.count }
    val maxVal = maxOf(
        issues.maxOfOrNull { it.count } ?: 1,
        returns.maxOfOrNull { it.count } ?: 1
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(CardBlue)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Issued", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(CardGreen)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Returned", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                allMonths.forEach { month ->
                    val issueCount = issueMap[month] ?: 0
                    val returnCount = returnMap[month] ?: 0
                    val issueFraction = issueCount.toFloat() / maxVal
                    val returnFraction = returnCount.toFloat() / maxVal

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Issue bar
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .fillMaxHeight(fraction = maxOf(issueFraction, 0.02f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(CardBlue)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            // Return bar
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .fillMaxHeight(fraction = maxOf(returnFraction, 0.02f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(CardGreen)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatMonthLabel(month),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightsSection(state: LibraryStatsState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InsightCard(
            icon = Icons.Default.Star,
            label = "Most Popular Author",
            value = state.mostPopularAuthor,
            color = Gold
        )
        InsightCard(
            icon = Icons.Default.AttachMoney,
            label = "Total Fines Collected",
            value = formatCurrency(state.totalFinesCollected),
            color = CardGreen
        )
        InsightCard(
            icon = Icons.Default.Person,
            label = "Total Members",
            value = "${state.totalMembers}",
            color = CardBlue
        )
        InsightCard(
            icon = Icons.Default.Devices,
            label = "Digital vs Physical",
            value = "${state.digitalBookCount} digital / ${state.physicalBookCount} physical",
            color = CardPurple
        )
        InsightCard(
            icon = Icons.Default.Schedule,
            label = "Avg. Borrowing Duration",
            value = String.format(Locale.getDefault(), "%.1f days", state.averageBorrowDuration),
            color = CardOrange
        )
    }
}

@Composable
private fun InsightCard(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun EmptyDataCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatCurrency(value: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "PK"))
    return formatter.format(value)
}

private fun formatMonthLabel(yearMonth: String): String {
    // Input format: "2024-03"
    return try {
        val parts = yearMonth.split("-")
        val monthNames = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        val monthIndex = parts[1].toInt() - 1
        monthNames[monthIndex]
    } catch (e: Exception) {
        yearMonth
    }
}
