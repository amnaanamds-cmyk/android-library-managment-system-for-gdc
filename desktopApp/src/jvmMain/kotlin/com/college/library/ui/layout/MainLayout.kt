package com.college.library.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.library.domain.session.SessionManager
import com.college.library.data.SyncManager
import com.college.library.data.DatabaseHelper
import com.college.library.ui.components.SyncStatusBadge
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState

enum class Screen {
    DASHBOARD, CATALOG, MEMBERS, CIRCULATION, RESERVATIONS, REPORTS, SETTINGS, DIRECTOR
}

@Composable
fun MainLayout(
    sessionManager: SessionManager,
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onLogout: () -> Unit,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    val syncService = remember { SyncManager.getSyncService(DatabaseHelper.getDatabase()) }
    val syncStatus by syncService.status.collectAsState()

    // Start the real-time sync engine and run a periodic full sync.
    LaunchedEffect(Unit) {
        syncService.currentInstitutionId = sessionManager.institutionId ?: "gdc11"
        syncService.startRealtimeSync(this)
        while(true) {
            try {
                syncService.startFullSync()
            } catch(e: Exception) {}
            delay(120000)
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        // Sidebar Navigation
        Column(
            modifier = Modifier.width(260.dp).fillMaxHeight().background(MaterialTheme.colors.primaryVariant).padding(vertical = 24.dp)
        ) {
            Text(
                text = "GDC Library50",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            Divider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 16.dp))

            SidebarItem("Dashboard", Icons.Default.Home, currentScreen == Screen.DASHBOARD) { onNavigate(Screen.DASHBOARD) }
            SidebarItem("Book Catalog", Icons.Default.List, currentScreen == Screen.CATALOG) { onNavigate(Screen.CATALOG) }
            SidebarItem("Members", Icons.Default.Person, currentScreen == Screen.MEMBERS) { onNavigate(Screen.MEMBERS) }
            SidebarItem("Circulation", Icons.Default.Send, currentScreen == Screen.CIRCULATION) { onNavigate(Screen.CIRCULATION) }
            SidebarItem("Reservations", Icons.Default.Notifications, currentScreen == Screen.RESERVATIONS) { onNavigate(Screen.RESERVATIONS) }
            
            if (sessionManager.role == "Director") {
                SidebarItem("Director Dashboard", Icons.Default.Star, currentScreen == Screen.DIRECTOR) { onNavigate(Screen.DIRECTOR) }
            }
            if (sessionManager.role == "Director" || sessionManager.role == "Librarian") {
                SidebarItem("Reports", Icons.Default.Info, currentScreen == Screen.REPORTS) { onNavigate(Screen.REPORTS) }
            }
            SidebarItem("Settings", Icons.Default.Settings, currentScreen == Screen.SETTINGS) { onNavigate(Screen.SETTINGS) }

            Spacer(modifier = Modifier.weight(1f))
            
            Divider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 16.dp))
            SidebarItem("Logout", Icons.Default.Close, false) { onLogout() }
        }

        // Main Content Area
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).background(MaterialTheme.colors.surface).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currentScreen.name.lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onToggleDarkMode(!isDarkMode) }) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colors.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = {
                            scope.launch {
                                isSyncing = true
                                try {
                                    syncService.currentInstitutionId = sessionManager.institutionId ?: "gdc11"
                                    syncService.startFullSync()
                                } catch(e: Exception) {}
                                isSyncing = false
                            }
                        },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF10B981))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync Now", tint = Color(0xFF10B981))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SyncStatusBadge(status = syncStatus)
                    Spacer(modifier = Modifier.width(32.dp))
                    Text(
                        text = "${sessionManager.librarianName} (${sessionManager.institutionId})",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface
                    )
                }
            }
            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))

            // Screen Content
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SidebarItem(title: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent
    val contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(bgColor)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, color = contentColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
