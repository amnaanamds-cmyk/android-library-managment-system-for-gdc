package com.college.library.ui.screens.ai

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.CardOrange
import com.college.library.ui.theme.Gold
import com.college.library.ui.theme.NavyBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHubScreen(
    onNavigateBack: () -> Unit,
    viewModel: AiHubViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library AI & Leaderboard", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = NavyBlue,
                contentColor = Gold,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Gold
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Libby AI", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI") },
                    selectedContentColor = Gold,
                    unselectedContentColor = Color.White.copy(alpha = 0.6f)
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Top Readers", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.EmojiEvents, contentDescription = "Leaderboard") },
                    selectedContentColor = Gold,
                    unselectedContentColor = Color.White.copy(alpha = 0.6f)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> AiChatTab(viewModel)
                    1 -> LeaderboardTab(viewModel)
                }
            }
        }
    }
}

@Composable
fun AiChatTab(viewModel: AiHubViewModel) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isTyping by viewModel.isTyping

    // Autoscroll chat on new messages
    LaunchedEffect(viewModel.chatMessages.size, isTyping) {
        if (viewModel.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.chatMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
    ) {
        // Chat messages log
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(viewModel.chatMessages, key = { it.id }) { message ->
                ChatBubble(message)
            }
            if (isTyping) {
                item { TypingIndicatorBubble() }
            }
        }

        // Suggestions chips
        val suggestions = listOf(
            "Recommend Books 📚",
            "Overdue Alerts ⚠️",
            "Fine Policy 📋",
            "Search Java 🔍"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { text ->
                SuggestionChip(
                    onClick = {
                        val query = when (text) {
                            "Recommend Books 📚" -> "Recommend books"
                            "Overdue Alerts ⚠️" -> "Show overdue books warning"
                            "Fine Policy 📋" -> "What is the fine policy?"
                            "Search Java 🔍" -> "Is Java book available?"
                            else -> text
                        }
                        viewModel.sendMessage(query)
                    },
                    label = { Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        // Send text bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask Libby anything...") },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp)),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NavyBlue,
                    unfocusedBorderColor = Color.LightGray
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        brush = Brush.radialGradient(colors = listOf(NavyBlue, Color(0xFF0F1E3D))),
                        shape = CircleShape
                    ),
                enabled = inputText.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Gold)
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == SenderType.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            // Libby avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Gold.copy(alpha = 0.2f), shape = CircleShape)
                    .border(1.dp, Gold, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Libby AI Logo", tint = NavyBlue, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) NavyBlue else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    color = if (isUser) Color.White else Color.Black,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Gold.copy(alpha = 0.2f), shape = CircleShape)
                .border(1.dp, Gold, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = "Libby AI", tint = NavyBlue, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))

        Card(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.width(80.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsing dot animations
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulse1 by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse),
                    label = "p1"
                )
                val pulse2 by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(animation = tween(600, delayMillis = 200), repeatMode = RepeatMode.Reverse),
                    label = "p2"
                )
                val pulse3 by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(animation = tween(600, delayMillis = 400), repeatMode = RepeatMode.Reverse),
                    label = "p3"
                )

                Box(modifier = Modifier.size(6.dp).scale(pulse1).background(NavyBlue, CircleShape))
                Box(modifier = Modifier.size(6.dp).scale(pulse2).background(NavyBlue, CircleShape))
                Box(modifier = Modifier.size(6.dp).scale(pulse3).background(NavyBlue, CircleShape))
            }
        }
    }
}

@Composable
fun LeaderboardTab(viewModel: AiHubViewModel) {
    val entries by viewModel.leaderboardState.collectAsState()

    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.EmojiEvents, contentDescription = "Trophy", tint = Color.LightGray, modifier = Modifier.size(80.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No borrows registered yet!", fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("Issue books to members to see who ranks top!", fontSize = 13.sp, color = Color.Gray)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Visual Podium
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                // 2nd Place
                if (entries.size > 1) {
                    PodiumColumn(
                        entry = entries[1],
                        rank = 2,
                        height = 110,
                        accentColor = Color(0xFFC0C0C0), // Silver
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // 1st Place
                if (entries.isNotEmpty()) {
                    PodiumColumn(
                        entry = entries[0],
                        rank = 1,
                        height = 145,
                        accentColor = Gold,
                        modifier = Modifier.weight(1.2f)
                    )
                }

                // 3rd Place
                if (entries.size > 2) {
                    PodiumColumn(
                        entry = entries[2],
                        rank = 3,
                        height = 85,
                        accentColor = Color(0xFFCD7F32), // Bronze
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("All Competitors", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NavyBlue)
            Spacer(modifier = Modifier.height(8.dp))

            // Other entries list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(entries.drop(3)) { entry ->
                    val index = entries.indexOf(entry)
                    LeaderboardRow(entry = entry, rank = index + 1)
                }
            }
        }
    }
}

@Composable
fun PodiumColumn(
    entry: LeaderboardEntry,
    rank: Int,
    height: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Badge Icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(accentColor.copy(alpha = 0.2f), shape = CircleShape)
                .border(2.dp, accentColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                fontWeight = FontWeight.Bold,
                color = if (rank == 1) NavyBlue else accentColor,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = entry.name,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = "${entry.borrowCount} books",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Podium Pillar Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(NavyBlue.copy(alpha = 0.9f), NavyBlue.copy(alpha = 0.6f))
                    ),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                )
                .border(
                    BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (rank) {
                    1 -> Icons.Default.EmojiEvents
                    2 -> Icons.Default.Stars
                    else -> Icons.Default.WorkspacePremium
                },
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun LeaderboardRow(
    entry: LeaderboardEntry,
    rank: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Number
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.LightGray.copy(alpha = 0.3f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Member Info
            Column(modifier = Modifier.weight(1f)) {
                Text(text = entry.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = "${entry.memberType} • ${entry.department}", fontSize = 11.sp, color = Color.Gray)
            }

            // Borrow Count
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.borrowCount.toString(),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = NavyBlue
                )
                Text(text = "borrows", fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}
