package com.college.library.ui.screens.leaderboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.college.library.data.model.Member
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.DangerRed

// ── Badge definitions ─────────────────────────────────────────────────────────
data class Badge(val label: String, val icon: ImageVector, val color: Color, val minBooks: Int)

val BADGES = listOf(
    Badge("Bookworm",     Icons.Default.AutoStories, Color(0xFF8BC34A), 1),
    Badge("Reader",       Icons.Default.MenuBook,    Color(0xFF2196F3), 5),
    Badge("Scholar",     Icons.Default.School,      Color(0xFF9C27B0), 10),
    Badge("Champion",     Icons.Default.EmojiEvents, Color(0xFFFFC107), 20),
    Badge("Legend",       Icons.Default.Star,        Color(0xFFFF5722), 50),
)

fun badgesForMember(booksIssued: Int): List<Badge> =
    BADGES.filter { booksIssued >= it.minBooks }

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: LeaderboardViewModel = hiltViewModel()
) {
    val topMembers by viewModel.topMembers.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏆  Leaderboard", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        if (topMembers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EmojiEvents, null,
                        modifier = Modifier.size(80.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No data yet. Issue some books to start the leaderboard!",
                        color = Color.Gray, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Podium for top‑3
                if (topMembers.size >= 3) {
                    item {
                        PodiumRow(
                            first  = topMembers[0],
                            second = topMembers[1],
                            third  = topMembers[2]
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Remaining ranks
                itemsIndexed(topMembers.drop(if (topMembers.size >= 3) 3 else 0)) { idx, member ->
                    RankCard(rank = idx + 4, member = member)
                }
            }
        }
    }
}

// ── Podium row ────────────────────────────────────────────────────────────────
@Composable
fun PodiumRow(first: Member, second: Member, third: Member) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        PodiumItem(rank = 2, member = second, height = 100.dp,
            bg = Color(0xFFB0BEC5), emoji = "🥈")
        PodiumItem(rank = 1, member = first,  height = 140.dp,
            bg = Color(0xFFFFD700), emoji = "🥇")
        PodiumItem(rank = 3, member = third,  height = 80.dp,
            bg = Color(0xFFCD7F32), emoji = "🥉")
    }
}

@Composable
fun PodiumItem(rank: Int, member: Member, height: androidx.compose.ui.unit.Dp, bg: Color, emoji: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Text(emoji, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            member.name.split(" ").first(),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
        Text("${member.booksIssued} books", fontSize = 10.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        // Badge strip for top member
        val badges = badgesForMember(member.booksIssued)
        if (badges.isNotEmpty()) {
            Icon(badges.last().icon, null,
                tint = badges.last().color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(bg),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                "#$rank",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ── Rank card for positions 4‑10 ─────────────────────────────────────────────
@Composable
fun RankCard(rank: Int, member: Member) {
    val badges = badgesForMember(member.booksIssued)
    val rankColor = when (rank) {
        4 -> Color(0xFFFFC107)
        5 -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(rankColor),
                contentAlignment = Alignment.Center
            ) {
                Text("#$rank", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            // Member info
            Column(modifier = Modifier.weight(1f)) {
                Text(member.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${member.memberId} • ${member.department}", fontSize = 12.sp, color = Color.Gray)
                if (badges.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        badges.takeLast(3).forEach { badge ->
                            AssistChip(
                                onClick = {},
                                label = { Text(badge.label, fontSize = 10.sp) },
                                leadingIcon = {
                                    Icon(badge.icon, null,
                                        tint = badge.color, modifier = Modifier.size(14.dp))
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                }
            }

            // Books count
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${member.booksIssued}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = CardGreen
                )
                Text("books", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}
