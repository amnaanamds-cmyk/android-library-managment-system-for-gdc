package com.college.library.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.library.domain.session.SessionManager
import com.russhwolf.settings.Settings

@Composable
fun SettingsScreen(sessionManager: SessionManager) {
    var finePerDay by remember { mutableStateOf("10.0") }
    var returnPeriodDays by remember { mutableStateOf("14") }
    var maxBooksPerMember by remember { mutableStateOf("3") }
    var autoBackupEnabled by remember { mutableStateOf(true) }
    
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background).padding(32.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Director Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
        Text("Configure library rules and system behavior", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f), fontSize = 16.sp)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Circulation Rules
        SettingsSection("Circulation Rules") {
            SettingsInputField("Fine per Overdue Day (Rs.)", finePerDay) { finePerDay = it }
            SettingsInputField("Standard Return Period (Days)", returnPeriodDays) { returnPeriodDays = it }
            SettingsInputField("Max Books per Member", maxBooksPerMember) { maxBooksPerMember = it }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // System Settings
        SettingsSection("System & Security") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = autoBackupEnabled, onCheckedChange = { autoBackupEnabled = it })
                Text("Enable Automatic Cloud Backup (Firestore)")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { /* License check */ }) {
                Text("Verify Windows License (DPAPI)")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Local Backup
        SettingsSection("Local Backup & Restore") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { com.college.library.backup.DesktopBackupManager.createBackup() }) {
                    Text("Backup Now")
                }
                Button(onClick = { com.college.library.backup.DesktopBackupManager.restoreBackup() }) {
                    Text("Restore Latest")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { /* Save all settings to Preferences/Firestore */ },
            modifier = Modifier.width(200.dp).height(50.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2563EB))
        ) {
            Icon(Icons.Default.Done, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun SettingsInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = MaterialTheme.colors.onSurface,
                backgroundColor = MaterialTheme.colors.surface
            )
        )
    }
}
