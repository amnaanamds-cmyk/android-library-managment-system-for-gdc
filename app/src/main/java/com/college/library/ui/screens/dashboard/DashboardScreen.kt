package com.college.library.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.college.library.data.SyncManager
import com.college.library.data.SyncStatus
import com.college.library.data.model.IssuedBook
import com.college.library.ui.components.SyncStatusBadge
import com.college.library.ui.theme.*
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.college.library.utils.OverdueNotificationHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DashboardScreen(
    onNavigateToOverdue: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAiHub: () -> Unit,
    onNavigateToIssue: () -> Unit,
    onNavigateToReturn: () -> Unit,
    onNavigateToAddMember: () -> Unit,
    onNavigateToSubjects: () -> Unit,
    onNavigateToWishlist: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
    authViewModel: com.college.library.ui.screens.auth.AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currentRole by authViewModel.currentRole.collectAsState()

    // Currency label as last synced from the institution's LibrarySettings.
    // SettingsViewModel mirrors it into these preferences, so reading it here
    // keeps the dashboard consistent with every other money figure in the app.
    val context = androidx.compose.ui.platform.LocalContext.current
    val currencySymbol = remember {
        context.getSharedPreferences("library_settings", android.content.Context.MODE_PRIVATE)
            .getString("currency_symbol", "Rs") ?: "Rs"
    }
    val canAccessSettings by remember { derivedStateOf { authViewModel.canAccessSettings() } }

    val syncService = remember { SyncManager.getSyncService(viewModel.database) }
    val syncStatus by syncService.status.collectAsState()

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val permissionState = rememberPermissionState(permission = android.Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(Unit) {
            if (!permissionState.status.isGranted) {
                permissionState.launchPermissionRequest()
            }
        }
    }

    val context = LocalContext.current
    
    // Refresh profile every time the screen is displayed
    LaunchedEffect(Unit) {
        viewModel.refreshProfile()
    }
    
    // Also re-fetch on every app resume to ensure profile updates are visible
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                viewModel.refreshProfile()
            }
        })
    }

    LaunchedEffect(state.overdueBooks) {
        if (state.overdueBooks.isNotEmpty()) {
            OverdueNotificationHelper.showNotification(context, state.overdueBooks)
        }
    }

    var showFabMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("College Library", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        SyncStatusBadge(status = syncStatus)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    // Entry point for global search across books and members.
                    // The search screen was previously unreachable.
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Gold)
                    }
                    IconButton(onClick = onNavigateToAiHub) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Hub", tint = Gold)
                    }
                    if (canAccessSettings) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Gold)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (showFabMenu) {
                    ExtendedFloatingActionButton(
                        onClick = { showFabMenu = false; onNavigateToAddMember() },
                        icon = { Icon(Icons.Default.PersonAdd, "Add Member") },
                        text = { Text("Add Member") },
                        modifier = Modifier.padding(bottom = 8.dp),
                        containerColor = CardPurple,
                        contentColor = Color.White
                    )
                    ExtendedFloatingActionButton(
                        onClick = { showFabMenu = false; onNavigateToReturn() },
                        icon = { Icon(Icons.Default.KeyboardReturn, "Return Book") },
                        text = { Text("Return Book") },
                        modifier = Modifier.padding(bottom = 8.dp),
                        containerColor = CardOrange,
                        contentColor = Color.White
                    )
                    ExtendedFloatingActionButton(
                        onClick = { showFabMenu = false; onNavigateToIssue() },
                        icon = { Icon(Icons.Default.MenuBook, "Issue Book") },
                        text = { Text("Issue Book") },
                        modifier = Modifier.padding(bottom = 8.dp),
                        containerColor = CardGreen,
                        contentColor = Color.White
                    )
                }
                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = Gold,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(if (showFabMenu) Icons.Default.Close else Icons.Default.Add, "Quick Actions")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // -- Hero Branding Banner --
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.collegeProfile.collegeFullName.ifBlank { "GDC Library Portal" }.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (state.collegeProfile.tagline.isNotBlank()) {
                            Text(
                                text = state.collegeProfile.tagline,
                                color = Gold,
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(title = "Total Books", value = state.totalBooks, color = CardBlue, modifier = Modifier.weight(1f))
                    StatCard(title = "Available", value = state.availableBooks, color = CardGreen, modifier = Modifier.weight(1f))
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(title = "Issued Books", value = state.issuedBooks, color = CardOrange, modifier = Modifier.weight(1f))
                    StatCard(title = "Total Members", value = state.totalMembers, color = CardPurple, modifier = Modifier.weight(1f))
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DoubleStatCard(
                        title = "Fine Collected",
                        value = state.totalFineCollected,
                        color = DangerRed,
                        modifier = Modifier.fillMaxWidth(),
                        currencySymbol = currencySymbol,
                    )
                }
            }

            if (state.overdueBooks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Overdue Books Warning", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                        TextButton(onClick = onNavigateToOverdue) {
                            Text("View Reports", color = DangerRed)
                        }
                    }
                }
                items(state.overdueBooks) { overdue ->
                    var phone by remember { mutableStateOf("") }
                    LaunchedEffect(overdue.memberId) {
                        phone = viewModel.getMemberPhoneAsync(overdue.memberId)
                    }
                    OverdueBookItem(overdue, phone)
                }
            }

            if (state.topPublishers.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Top 5 Publishers", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            val entries = state.topPublishers.values.map { it.toFloat() }.toTypedArray()
                            val keys = state.topPublishers.keys.toList()
                            if (entries.isNotEmpty()) {
                                Chart(
                                    chart = columnChart(),
                                    model = entryModelOf(*entries),
                                    startAxis = rememberStartAxis(),
                                    bottomAxis = rememberBottomAxis(
                                        valueFormatter = { value, _ ->
                                            val idx = value.toInt()
                                            if (idx in keys.indices) keys[idx].take(10) + ".." else ""
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (state.issueTrends.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Borrowing Trend (Last 7 Days)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .padding(top = 8.dp, bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            val entries = state.issueTrends.values.map { it.toFloat() }.toTypedArray()
                            val keys = state.issueTrends.keys.toList()
                            if (entries.isNotEmpty()) {
                                Chart(
                                    chart = lineChart(),
                                    model = entryModelOf(*entries),
                                    startAxis = rememberStartAxis(
                                        valueFormatter = { value, _ -> value.toInt().toString() }
                                    ),
                                    bottomAxis = rememberBottomAxis(
                                        valueFormatter = { value, _ ->
                                            val idx = value.toInt()
                                            if (idx in keys.indices) keys[idx].takeLast(5) else ""
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    var animationTriggered by remember { mutableStateOf(false) }
    val animatedValue by animateIntAsState(
        targetValue = if (animationTriggered) value else 0,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "countUp"
    )

    LaunchedEffect(Unit) { animationTriggered = true }

    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(animatedValue.toString(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DoubleStatCard(
    title: String,
    value: Double,
    color: Color,
    modifier: Modifier = Modifier,
    /**
     * Currency label from the institution's LibrarySettings. Defaults to the
     * Pakistani rupee — this deployment previously printed the INDIAN rupee
     * sign (U+20B9), which is a different currency.
     */
    currencySymbol: String = "Rs",
) {
    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "$currencySymbol ${String.format("%.2f", value)}",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun OverdueBookItem(book: IssuedBook, phone: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(book.bookTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Issued to: ${book.memberName} (${book.memberMemberId})", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Due: ${book.dueDate}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            }
            if (phone.isNotBlank()) {
                IconButton(onClick = {
                    val message = "Dear ${book.memberName}, your borrowed book '${book.bookTitle}' was due on ${book.dueDate}. Please return it to the library as soon as possible to avoid further fines."
                    val url = "https://api.whatsapp.com/send?phone=$phone&text=${android.net.Uri.encode(message)}"
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse(url)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "WhatsApp not installed.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Default.Message, contentDescription = "Send WhatsApp Reminder", tint = Color(0xFF25D366))
                }
            }
        }
    }
}
