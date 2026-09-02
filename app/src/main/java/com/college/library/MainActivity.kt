package com.college.library

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.college.library.ui.screens.books.AddEditBookScreen
import com.college.library.ui.screens.books.BookDetailScreen
import com.college.library.ui.screens.books.BookListScreen
import com.college.library.ui.screens.books.SubjectBrowseScreen
import com.college.library.ui.screens.dashboard.DashboardScreen
import com.college.library.ui.screens.issue.BulkIssueScreen
import com.college.library.ui.screens.issue.IssueBookScreen
import com.college.library.ui.screens.issue.IssueReturnHubScreen
import com.college.library.ui.screens.members.AddEditMemberScreen
import com.college.library.ui.screens.members.MemberDetailScreen
import com.college.library.ui.screens.members.MembersScreen
import com.college.library.ui.screens.reports.ReportsScreen
import com.college.library.ui.screens.return_.ReturnBookScreen
import com.college.library.ui.screens.settings.SettingsScreen
import com.college.library.ui.screens.settings.SettingsViewModel
import com.college.library.ui.screens.settings.AboutScreen
import com.college.library.ui.screens.inventory.InventoryScreen
import com.college.library.ui.screens.ai.AiHubScreen
import com.college.library.ui.screens.wishlist.WishlistScreen
import com.college.library.ui.screens.leaderboard.LeaderboardScreen
import com.college.library.ui.theme.AppTheme
import com.college.library.utils.rememberStrings
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import com.college.library.backup.BackupRestoreScreen
import com.college.library.export.ExportScreen
import com.college.library.license.LicenseScreen
import com.college.library.license.LicenseViewModel
import com.college.library.notifications.NotificationCenterScreen
import com.college.library.ui.screens.auth.AuthViewModel
import com.college.library.ui.screens.auth.LoginScreen
import com.college.library.ui.screens.reservation.ReservationScreen
import com.college.library.ui.screens.stats.LibraryStatsScreen
import com.college.library.profile.CollegeProfileManager
import com.college.library.profile.CollegeProfileScreen
import com.college.library.ui.screens.heatmap.HeatmapScreen
import com.college.library.ui.screens.recommendation.RecommendationScreen
import com.college.library.ui.screens.finewaiver.FineWaiverScreen
import com.college.library.ui.screens.readinggoals.ReadingGoalsScreen
import com.college.library.ui.screens.digitalid.DigitalIdScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.college.library.data.db.LibraryDatabase
import javax.inject.Inject

@OptIn(ExperimentalPermissionsApi::class)
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var database: LibraryDatabase
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkModeEnabled = settingsViewModel.state.collectAsState().value.darkModeEnabled
            val context = androidx.compose.ui.platform.LocalContext.current

            var isProfileSetup by remember { mutableStateOf<Boolean?>(null) }

            LaunchedEffect(Unit) {
                isProfileSetup = withContext(Dispatchers.IO) {
                    CollegeProfileManager.getInstance(context).isSetupComplete()
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val permissionState = com.google.accompanist.permissions.rememberPermissionState(
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (!permissionState.status.isGranted) {
                        permissionState.launchPermissionRequest()
                    }
                }
            }

            AppTheme(darkModeEnabled = darkModeEnabled) {
                if (isProfileSetup == null) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    LibraryApp(
                        settingsViewModel = settingsViewModel,
                        initialProfileSetup = isProfileSetup!!,
                        onProfileSetupComplete = { isProfileSetup = true },
                        database = database
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryApp(
    settingsViewModel: SettingsViewModel,
    initialProfileSetup: Boolean,
    onProfileSetupComplete: () -> Unit,
    database: LibraryDatabase,
    authViewModel: AuthViewModel = hiltViewModel(),
    licenseViewModel: LicenseViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val authState by authViewModel.authState.collectAsState()
    val isAuthenticated = authState is com.college.library.ui.screens.auth.AuthState.Authenticated
    val isLicensed by licenseViewModel.isLicensed.collectAsState()
    var showOpac by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Initialize Realtime Sync engine
    //
    // FIX: previously this fell back to institutionId = "gdc11" whenever the
    // Authenticated state's institutionId couldn't be read. That silent
    // fallback is what caused every device to converge on a shared "gdc11"
    // institution document instead of each college's own data. Now, if we
    // don't have a real institutionId yet, we simply don't start sync — the
    // LaunchedEffect will re-run and start it correctly once authState
    // actually carries a valid institutionId (right after login/onboarding).
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(isAuthenticated, (authState as? com.college.library.ui.screens.auth.AuthState.Authenticated)?.institutionId) {
        val institutionId = (authState as? com.college.library.ui.screens.auth.AuthState.Authenticated)?.institutionId
        if (isAuthenticated && !institutionId.isNullOrBlank()) {
            com.college.library.data.sync.RealtimeSyncManager.initialize(context, database, institutionId, coroutineScope)
        } else {
            com.college.library.data.sync.RealtimeSyncManager.disconnect(database)
        }
    }

    if (!isLicensed) {
        LicenseScreen(onLicenseActivated = { }, viewModel = licenseViewModel)
        return
    }

    if (!initialProfileSetup) {
        CollegeProfileScreen(onNavigateBack = { }, onSetupComplete = onProfileSetupComplete, isOnboarding = true)
        return
    }

    if (showOpac) {
        val opacNavController = rememberNavController()
        NavHost(navController = opacNavController, startDestination = "opac_login") {
            composable("opac_login") {
                com.college.library.ui.screens.opac.OpacLoginScreen(
                    onLoginSuccess = { id -> opacNavController.navigate("opac_home/$id") { popUpTo("opac_login") { inclusive = true } } },
                    onNavigateBack = { showOpac = false }
                )
            }
            composable("opac_home/{studentId}", arguments = listOf(navArgument("studentId") { type = NavType.LongType })) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("studentId") ?: 0L
                com.college.library.ui.screens.opac.OpacHomeScreen(studentId = id, onLogout = { showOpac = false })
            }
        }
        return
    }

    if (authState is com.college.library.ui.screens.auth.AuthState.NeedsOnboarding) {
        com.college.library.ui.screens.auth.OnboardingScreen(viewModel = authViewModel)
        return
    }

    val startDestination = when {
        !isAuthenticated -> "login"
        else -> "dashboard"
    }

    val mainTabs = listOf("dashboard", "books", "members", "hub", "reports", "leaderboard", "ebooks")
    val isMainScreen = currentRoute in mainTabs && isAuthenticated

    Scaffold(
        bottomBar = {
            if (isMainScreen) {
                val strings = rememberStrings()
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text(strings.navHome, fontSize = 10.sp) },
                        selected = currentRoute == "dashboard",
                        onClick = { navController.navigate("dashboard") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                        label = { Text(strings.navBooks, fontSize = 10.sp) },
                        selected = currentRoute == "books",
                        onClick = { navController.navigate("books") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.People, null) },
                        label = { Text(strings.navMembers, fontSize = 10.sp) },
                        selected = currentRoute == "members",
                        onClick = { navController.navigate("members") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.SwapHoriz, null) },
                        label = { Text(strings.navTransact, fontSize = 10.sp) },
                        selected = currentRoute == "hub",
                        onClick = { navController.navigate("hub") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.BarChart, null) },
                        label = { Text(strings.navReports, fontSize = 10.sp) },
                        selected = currentRoute == "reports",
                        onClick = { navController.navigate("reports") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                        label = { Text("E-Books", fontSize = 10.sp) },
                        selected = currentRoute == "ebooks",
                        onClick = { navController.navigate("ebooks") { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) }
        ) {
            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("dashboard") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onNavigateToOpac = { showOpac = true },
                    onNavigateToScanner = { navController.navigate("login_qr_scanner") },
                    viewModel = authViewModel
                )
            }
            composable("login_qr_scanner") {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Scan QR to Link") },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.Default.ArrowBack, "Back")
                                }
                            }
                        )
                    }
                ) { p ->
                    Box(modifier = Modifier.fillMaxSize().padding(p)) {
                        com.college.library.ui.components.CameraXScanner { qrData ->
                            try {
                                val parts = qrData.split("|")
                                if (parts.size >= 3 && parts[0] == "NEXLIB_LINK") {
                                    val instId = parts[1]
                                    val roleStr = parts[2]
                                    val role = when(roleStr.lowercase()) {
                                        "admin" -> com.college.library.ui.screens.auth.UserRole.COLLEGE_ADMIN
                                        "director" -> com.college.library.ui.screens.auth.UserRole.DIRECTOR
                                        else -> com.college.library.ui.screens.auth.UserRole.LIBRARIAN
                                    }
                                    authViewModel.onOnboardingComplete(instId, role)
                                    navController.navigate("dashboard") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Invalid Login QR", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            composable("dashboard") {
                DashboardScreen(
                    onNavigateToOverdue = { navController.navigate("reports") },
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToAiHub = { navController.navigate("ai_hub") },
                    onNavigateToIssue = { navController.navigate("issue_book") },
                    onNavigateToReturn = { navController.navigate("return_book") },
                    onNavigateToAddMember = { navController.navigate("add_edit_member/0") },
                    onNavigateToSubjects = { navController.navigate("browse_subjects") },
                    onNavigateToWishlist = { navController.navigate("wishlist") },
                    onNavigateToSearch = { navController.navigate("global_search") }
                )
            }
            // Global search across books and members, with barcode lookup.
            // The screen existed but had no route, so nothing could reach it.
            composable("global_search") {
                com.college.library.ui.screens.search.GlobalSearchScreen(
                    onNavigateToBookDetail = { navController.navigate("book_detail/$it") },
                    onNavigateToMemberDetail = { navController.navigate("member_detail/$it") }
                )
            }
            composable("books") {
                BookListScreen(
                    onNavigateToAddBook = { navController.navigate("add_edit_book/0") },
                    onNavigateToDetail = { navController.navigate("book_detail/$it") },
                    onNavigateToEdit = { navController.navigate("add_edit_book/$it") },
                    onNavigateToWishlist = { navController.navigate("wishlist") }
                )
            }
            composable("members") {
                MembersScreen(
                    onNavigateToAddMember = { navController.navigate("add_edit_member/0") },
                    onNavigateToDetail = { navController.navigate("member_detail/$it") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAbout = { navController.navigate("about") },
                    onNavigateToBackup = { navController.navigate("backup_restore") },
                    onNavigateToExport = { navController.navigate("export") },
                    onNavigateToStats = { navController.navigate("library_stats") },
                    onNavigateToReservations = { navController.navigate("reservations") },
                    onNavigateToNotifications = { navController.navigate("notifications") },
                    onNavigateToCollegeProfile = { navController.navigate("college_profile") },
                    onNavigateToHeatmap = { navController.navigate("heatmap") },
                    onNavigateToRecommendations = { navController.navigate("recommendations") },
                    onNavigateToFineWaiver = { navController.navigate("fine_waiver") },
                    onNavigateToReadingGoals = { navController.navigate("reading_goals") },
                    onNavigateToClassification = { navController.navigate("classification") },
                    onNavigateToSpineLabels = { navController.navigate("spine_labels") },
                    onNavigateToBiometric = { navController.navigate("biometric") },
                    onNavigateToUnionCatalog = { navController.navigate("union_catalog") },
                    onNavigateToGateLog = { navController.navigate("gate_log") },
                    onNavigateToAcquisitions = { navController.navigate("acquisitions") },
                    onNavigateToBookTransfers = { navController.navigate("book_transfers") },
                    onNavigateToSerials = { navController.navigate("serials") },
                    onNavigateToIll = { navController.navigate("ill_requests") },
                    onNavigateToMarcCatalog = { navController.navigate("marc_catalog") },
                    onNavigateToEnterprise = { navController.navigate("enterprise") },
                    viewModel = settingsViewModel
                )
            }
            composable("wishlist") { WishlistScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("ai_hub") { AiHubScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("browse_subjects") { SubjectBrowseScreen(onNavigateBack = { navController.popBackStack() }, onNavigateToDetail = { navController.navigate("book_detail/$it") }) }
            composable("about") { AboutScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("book_detail/{bookId}", arguments = listOf(navArgument("bookId") { type = NavType.LongType })) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("bookId") ?: 0L
                BookDetailScreen(id, onNavigateBack = { navController.popBackStack() }, onNavigateToEdit = { navController.navigate("add_edit_book/$it") }, onNavigateToCopy = { navController.navigate("add_edit_book/$it?isCopy=true") }, onNavigateToIssue = { navController.navigate("issue_book?isbn=$it") })
            }
            composable("add_edit_book/{bookId}?isCopy={isCopy}", arguments = listOf(navArgument("bookId") { type = NavType.LongType }, navArgument("isCopy") { type = NavType.BoolType; defaultValue = false })) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("bookId") ?: 0L
                val isCopy = backStackEntry.arguments?.getBoolean("isCopy") ?: false
                AddEditBookScreen(id, isCopy, onNavigateBack = { navController.popBackStack() })
            }
            composable("member_detail/{memberId}", arguments = listOf(navArgument("memberId") { type = NavType.LongType })) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("memberId") ?: 0L
                MemberDetailScreen(id, onNavigateBack = { navController.popBackStack() }, onNavigateToEdit = { navController.navigate("add_edit_member/$it") })
            }
            composable("add_edit_member/{memberId}", arguments = listOf(navArgument("memberId") { type = NavType.LongType })) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("memberId") ?: 0L
                AddEditMemberScreen(id, onNavigateBack = { navController.popBackStack() })
            }
            composable("hub") { IssueReturnHubScreen(onNavigateToIssue = { navController.navigate("issue_book") }, onNavigateToReturn = { navController.navigate("return_book") }, onNavigateToBulk = { navController.navigate("bulk_issue") }, onNavigateToInventory = { navController.navigate("inventory") }) }
            composable("issue_book?isbn={isbn}", arguments = listOf(navArgument("isbn") { type = NavType.StringType; defaultValue = "" })) { backStackEntry ->
                IssueBookScreen(backStackEntry.arguments?.getString("isbn") ?: "", onNavigateBack = { navController.popBackStack() })
            }
            composable("return_book") { ReturnBookScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("bulk_issue") { BulkIssueScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("inventory") { InventoryScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("reports") { ReportsScreen() }
            composable("leaderboard") { LeaderboardScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("ebooks") { com.college.library.ui.screens.books.EBooksScreen() }
            composable("backup_restore") { BackupRestoreScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("export") { ExportScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("reservations") { ReservationScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("notifications") { NotificationCenterScreen(onBack = { navController.popBackStack() }) }
            composable("library_stats") { LibraryStatsScreen(onBack = { navController.popBackStack() }) }
            composable("college_profile") { CollegeProfileScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("heatmap") { HeatmapScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("recommendations") { RecommendationScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("fine_waiver") { FineWaiverScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("reading_goals") { ReadingGoalsScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("digital_id/{memberId}", arguments = listOf(navArgument("memberId") { type = NavType.LongType })) { backStackEntry ->
                DigitalIdScreen(backStackEntry.arguments?.getLong("memberId") ?: 0L, onNavigateBack = { navController.popBackStack() })
            }
            composable("classification") { com.college.library.ui.screens.classification.ClassificationScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("spine_labels") { com.college.library.ui.screens.spinelabel.SpineLabelScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("biometric") { com.college.library.ui.screens.biometric.BiometricScreen(onNavigateBack = { navController.popBackStack() }) }
            composable("union_catalog") { com.college.library.ui.screens.unioncatalog.UnionCatalogScreen(onNavigateBack = { navController.popBackStack() }) }
            // ── Operations screens ────────────────────────────────────────
            // Gate log, acquisitions, transfers, serials, ILL, MARC catalogue
            // and enterprise. These existed on the desktop and web apps but
            // had no Android equivalent, so the three platforms were not
            // interchangeable for a librarian.
            composable("gate_log") {
                com.college.library.ui.screens.operations.GateLogScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("acquisitions") {
                com.college.library.ui.screens.operations.AcquisitionsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("book_transfers") {
                com.college.library.ui.screens.operations.BookTransfersScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("serials") {
                com.college.library.ui.screens.operations.SerialsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("ill_requests") {
                com.college.library.ui.screens.operations.IllScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("marc_catalog") {
                com.college.library.ui.screens.operations.MarcCatalogScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("enterprise") {
                com.college.library.ui.screens.operations.EnterpriseScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("login_qr_scanner_placeholder") {} // Removed the old route handler
        }
    }
}