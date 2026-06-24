package com.college.library

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.college.library.ui.screens.ai.AiHubScreen
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

@OptIn(ExperimentalPermissionsApi::class)
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkModeEnabled = settingsViewModel.state.collectAsState().value.darkModeEnabled

            // Request Notification Permission on Android 13+
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

            val context = androidx.compose.ui.platform.LocalContext.current
            var showBackupDialog by remember { mutableStateOf(false) }
            var pendingBackupFile by remember { mutableStateOf<java.io.File?>(null) }

            LaunchedEffect(Unit) {
                val prefs = context.getSharedPreferences("library_prefs", android.content.Context.MODE_PRIVATE)
                val path = prefs.getString("pending_backup_file", null)
                if (path != null) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        pendingBackupFile = file
                        showBackupDialog = true
                    }
                    prefs.edit().remove("pending_backup_file").apply()
                }
            }

            if (showBackupDialog && pendingBackupFile != null) {
                AlertDialog(
                    onDismissRequest = { showBackupDialog = false },
                    title = { Text("App Recovered from Crash") },
                    text = { Text("The app recently crashed. A secure backup of your local database has been saved. Would you like to save it to your Google Drive for extra safety?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showBackupDialog = false
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                pendingBackupFile!!
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/x-sqlite3"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Save Backup to Google Drive"))
                        }) {
                            Text("Save to Drive", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBackupDialog = false }) {
                            Text("Dismiss")
                        }
                    }
                )
            }

            AppTheme(darkModeEnabled = darkModeEnabled) {
                LibraryApp(settingsViewModel = settingsViewModel)
            }
        }
    }
}

@Composable
fun LibraryApp(
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel = hiltViewModel(),
    licenseViewModel: LicenseViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()
    val isLicensed by licenseViewModel.isLicensed.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showOpac by remember { mutableStateOf(false) }
    var isProfileSetup by remember { mutableStateOf(CollegeProfileManager.getInstance(context).isSetupComplete()) }

    val mainTabs = listOf("dashboard", "books", "members", "hub", "reports", "leaderboard", "ebooks")
    val isMainScreen = currentRoute in mainTabs && isAuthenticated

    Scaffold(
        bottomBar = {
            if (isMainScreen) {
                val strings = rememberStrings()
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = strings.navHome) },
                        label = { Text(strings.navHome) },
                        selected = currentRoute == "dashboard",
                        onClick = {
                            navController.navigate("dashboard") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = strings.navBooks) },
                        label = { Text(strings.navBooks) },
                        selected = currentRoute == "books",
                        onClick = {
                            navController.navigate("books") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.People, contentDescription = strings.navMembers) },
                        label = { Text(strings.navMembers) },
                        selected = currentRoute == "members",
                        onClick = {
                            navController.navigate("members") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.SwapHoriz, contentDescription = strings.navTransact) },
                        label = { Text(strings.navTransact) },
                        selected = currentRoute == "hub",
                        onClick = {
                            navController.navigate("hub") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.BarChart, contentDescription = strings.navReports) },
                        label = { Text(strings.navReports) },
                        selected = currentRoute == "reports",
                        onClick = {
                            navController.navigate("reports") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.EmojiEvents, contentDescription = strings.navRanks) },
                        label = { Text(strings.navRanks) },
                        selected = currentRoute == "leaderboard",
                        onClick = {
                            navController.navigate("leaderboard") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "E-Books") },
                        label = { Text("E-Books") },
                        selected = currentRoute == "ebooks",
                        onClick = {
                            navController.navigate("ebooks") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        if (!isLicensed) {
            LicenseScreen(
                onLicenseActivated = { },
                viewModel = licenseViewModel
            )
            return@Scaffold
        }
        if (!isProfileSetup) {
            CollegeProfileScreen(
                onNavigateBack = { },
                onSetupComplete = { isProfileSetup = true },
                isOnboarding = true
            )
            return@Scaffold
        }
        if (showOpac) {
            val opacNavController = rememberNavController()
            NavHost(
                navController = opacNavController,
                startDestination = "opac_login",
                modifier = Modifier.padding(padding)
            ) {
                composable("opac_login") {
                    com.college.library.ui.screens.opac.OpacLoginScreen(
                        onLoginSuccess = { studentId ->
                            opacNavController.navigate("opac_home/$studentId") {
                                popUpTo("opac_login") { inclusive = true }
                            }
                        },
                        onNavigateBack = { showOpac = false }
                    )
                }
                composable(
                    route = "opac_home/{studentId}",
                    arguments = listOf(navArgument("studentId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("studentId") ?: 0L
                    com.college.library.ui.screens.opac.OpacHomeScreen(
                        studentId = id,
                        onLogout = { showOpac = false }
                    )
                }
            }
        } else if (!isAuthenticated) {
            LoginScreen(
                onLoginSuccess = { },
                onNavigateToOpac = { showOpac = true }
            ) // State will update automatically via ViewModel
        } else {
            NavHost(
                navController = navController,
                startDestination = "dashboard",
            modifier = Modifier.padding(padding),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) }
        ) {
            composable("dashboard") { 
                DashboardScreen(
                    onNavigateToOverdue = { navController.navigate("reports") },
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToAiHub = { navController.navigate("ai_hub") },
                    onNavigateToIssue = { navController.navigate("issue_book") },
                    onNavigateToReturn = { navController.navigate("return_book") },
                    onNavigateToAddMember = { navController.navigate("add_edit_member/0") },
                    onNavigateToSubjects = { navController.navigate("browse_subjects") }
                ) 
            }
            
            composable("ai_hub") {
                AiHubScreen(onNavigateBack = { navController.popBackStack() })
            }
            
            composable("browse_subjects") {
                SubjectBrowseScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { id -> navController.navigate("book_detail/$id") }
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
                    viewModel = settingsViewModel
                )
            }
            
            composable("about") {
                AboutScreen(onNavigateBack = { navController.popBackStack() })
            }
            
            composable("books") { 
                BookListScreen(
                    onNavigateToAddBook = { navController.navigate("add_edit_book/0") },
                    onNavigateToDetail = { id -> navController.navigate("book_detail/$id") },
                    onNavigateToEdit = { id -> navController.navigate("add_edit_book/$id") }
                ) 
            }
            
            composable("book_detail/{bookId}", arguments = listOf(navArgument("bookId") { type = NavType.LongType })) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getLong("bookId") ?: 0L
                BookDetailScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id -> navController.navigate("add_edit_book/$id") },
                    onNavigateToIssue = { isbn -> navController.navigate("issue_book?isbn=$isbn") }
                )
            }

            composable("add_edit_book/{bookId}", arguments = listOf(navArgument("bookId") { type = NavType.LongType })) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getLong("bookId") ?: 0L
                AddEditBookScreen(
                    bookId = bookId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("members") { 
                MembersScreen(
                    onNavigateToAddMember = { navController.navigate("add_edit_member/0") },
                    onNavigateToDetail = { id -> navController.navigate("member_detail/$id") }
                ) 
            }

            composable("member_detail/{memberId}", arguments = listOf(navArgument("memberId") { type = NavType.LongType })) { backStackEntry ->
                val memberId = backStackEntry.arguments?.getLong("memberId") ?: 0L
                MemberDetailScreen(
                    memberId = memberId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id -> navController.navigate("add_edit_member/$id") }
                )
            }

            composable("add_edit_member/{memberId}", arguments = listOf(navArgument("memberId") { type = NavType.LongType })) { backStackEntry ->
                val memberId = backStackEntry.arguments?.getLong("memberId") ?: 0L
                AddEditMemberScreen(
                    memberId = memberId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("hub") {
                IssueReturnHubScreen(
                    onNavigateToIssue = { navController.navigate("issue_book") },
                    onNavigateToReturn = { navController.navigate("return_book") },
                    onNavigateToBulk = { navController.navigate("bulk_issue") }
                )
            }

            composable(
                "issue_book?isbn={isbn}",
                arguments = listOf(navArgument("isbn") { type = NavType.StringType; defaultValue = "" })
            ) { backStackEntry ->
                val isbn = backStackEntry.arguments?.getString("isbn") ?: ""
                IssueBookScreen(isbn = isbn, onNavigateBack = { navController.popBackStack() })
            }

            composable("return_book") {
                ReturnBookScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable("bulk_issue") {
                BulkIssueScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable("reports") {
                ReportsScreen()
            }

            composable("leaderboard") {
                LeaderboardScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable("ebooks") {
                com.college.library.ui.screens.books.EBooksScreen()
            }

            composable("backup_restore") {
                BackupRestoreScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable("export") {
                ExportScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable("reservations") {
                ReservationScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable("notifications") {
                NotificationCenterScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable("library_stats") {
                LibraryStatsScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable("college_profile") {
                CollegeProfileScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
        }
    }
}
