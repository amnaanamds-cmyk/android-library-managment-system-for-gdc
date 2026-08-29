package com.college.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.college.library.data.sync.FirebaseSyncClientImpl
import com.college.library.data.CollegeService
import com.college.library.domain.session.SessionManager
import com.college.library.ui.layout.MainLayout
import com.college.library.ui.layout.Screen
import com.college.library.ui.screens.login.LoginScreen
import com.college.library.ui.screens.catalog.CatalogScreen
import com.college.library.ui.screens.dashboard.DashboardScreen
import com.college.library.ui.screens.director.DirectorDashboardScreen
import com.college.library.ui.screens.members.MembersScreen
import com.college.library.ui.screens.circulation.CirculationScreen
import com.college.library.ui.screens.reservations.ReservationsScreen
import com.college.library.ui.screens.reports.ReportingScreen
import com.college.library.ui.screens.settings.SettingsScreen
import com.russhwolf.settings.PreferencesSettings
import java.util.prefs.Preferences
import java.awt.Dimension

import com.college.library.ui.theme.DesktopTheme
import com.college.library.data.initializeFirebaseOnJvm

@Composable
fun App(sessionManager: SessionManager) {
    var isAuthenticated by remember { mutableStateOf(sessionManager.isLoggedIn() && sessionManager.isRememberMe) }
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
    var isDarkMode by remember { mutableStateOf(sessionManager.isDarkMode) }
    
    // Enable sync engine on Desktop
    LaunchedEffect(Unit) {
        initializeFirebaseOnJvm()
    }
    
    val syncClient = remember { FirebaseSyncClientImpl() }
    val collegeService = remember { CollegeService() }

    DesktopTheme(darkTheme = isDarkMode) {
        if (!isAuthenticated) {
            LoginScreen(
                syncClient = syncClient,
                sessionManager = sessionManager,
                onLoginSuccess = { isAuthenticated = true }
            )
        } else {
            MainLayout(
                sessionManager = sessionManager,
                currentScreen = currentScreen,
                onNavigate = { currentScreen = it },
                onLogout = {
                    sessionManager.clearSession()
                    isAuthenticated = false
                },
                isDarkMode = isDarkMode,
                onToggleDarkMode = {
                    isDarkMode = it
                    sessionManager.isDarkMode = it
                }
            ) {
                when (currentScreen) {
                    Screen.DASHBOARD -> DashboardScreen(
                        onNavigateToCatalog = { currentScreen = Screen.CATALOG },
                        onNavigateToMembers = { currentScreen = Screen.MEMBERS },
                        onNavigateToCirculation = { currentScreen = Screen.CIRCULATION }
                    )
                    Screen.CATALOG -> CatalogScreen()
                    Screen.MEMBERS -> MembersScreen()
                    Screen.CIRCULATION -> CirculationScreen()
                    Screen.RESERVATIONS -> ReservationsScreen()
                    Screen.REPORTS -> ReportingScreen()
                    Screen.SETTINGS -> SettingsScreen(sessionManager)
                    Screen.DIRECTOR -> DirectorDashboardScreen(
                        sessionManager = sessionManager,
                        collegeService = collegeService
                    )
                }
            }
        }
    }
}

fun main() = application {
    // Initialize KMP Settings using JVM Preferences for desktop
    val preferences = Preferences.userRoot()
    val settings = PreferencesSettings(preferences)
    val sessionManager = SessionManager(settings)

    Window(
        onCloseRequest = ::exitApplication, 
        title = "GDC Library50"
    ) {
        window.minimumSize = Dimension(1024, 768)
        App(sessionManager)
    }
}
