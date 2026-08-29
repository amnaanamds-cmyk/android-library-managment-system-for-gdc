package com.college.library.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.library.data.DatabaseHelper
import com.college.library.data.SyncDatabaseHelper
import com.college.library.data.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.OptIn

@Composable
fun ReportingScreen() {
    val database = remember { DatabaseHelper.getDatabase() }
    val syncService = remember { SyncManager.getSyncService(database) }
    val scope = rememberCoroutineScope()
    
    var selectedReport by remember { mutableStateOf("Circulation") }
    var isSyncing by remember { mutableStateOf(false) }
    
    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        // Report Sidebar
        Column(
            modifier = Modifier.width(280.dp).fillMaxHeight().background(MaterialTheme.colors.surface).padding(24.dp)
        ) {
            Text("Reports Center", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
            Spacer(modifier = Modifier.height(32.dp))
            
            ReportNavItem("Circulation Report", Icons.Default.DateRange, selectedReport == "Circulation") { selectedReport = "Circulation" }
            ReportNavItem("Member Heatmap", Icons.Default.List, selectedReport == "Heatmap") { selectedReport = "Heatmap" }
            ReportNavItem("Collection Health", Icons.Default.Info, selectedReport == "Health") { selectedReport = "Health" }
            ReportNavItem("Compliance (HEC)", Icons.Default.Info, selectedReport == "Compliance") { selectedReport = "Compliance" }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { 
                    scope.launch {
                        isSyncing = true
                        try {
                            syncService.startFullSync()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isSyncing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(backgroundColor = if (isSyncing) Color.Gray else MaterialTheme.colors.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isSyncing) "Syncing..." else "Sync Cloud", color = Color.White)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { /* Export Logic */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export PDF", color = Color.White)
            }
        }
        
        // Report Content
        Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            when (selectedReport) {
                "Circulation" -> CirculationReportView()
                "Heatmap" -> MemberHeatmapView()
                "Health" -> CollectionHealthView()
                "Compliance" -> ComplianceReportView()
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ReportNavItem(title: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val tint = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
    val bg = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent
    
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, color = tint, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
fun CirculationReportView() {
    Column {
        Text("Monthly Circulation Overview", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
        Spacer(modifier = Modifier.height(24.dp))
        
        // Placeholder for Chart
        Card(modifier = Modifier.fillMaxWidth().height(300.dp), shape = RoundedCornerShape(12.dp), backgroundColor = MaterialTheme.colors.surface) {
            Box(contentAlignment = Alignment.Center) {
                Text("Circulation Trend Chart (KMP Chart Library integration here)", color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Recent Transactions", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Table of data
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), backgroundColor = MaterialTheme.colors.surface) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text("Date", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                    Text("Issues", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                    Text("Returns", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                }
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                listOf("2026-05-01", "2026-05-02", "2026-05-03").forEach { date ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(date, modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface)
                        Text("12", modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface)
                        Text("8", modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface)
                    }
                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.05f))
                }
            }
        }
    }
}

@Composable
fun MemberHeatmapView() {
    Text("Member Activity Heatmap", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    // Visualization of active members
}

@Composable
fun CollectionHealthView() {
    Text("Collection Health Analysis", fontSize = 24.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun ComplianceReportView() {
    Text("HEC NDLP Compliance Report", fontSize = 24.sp, fontWeight = FontWeight.Bold)
}
