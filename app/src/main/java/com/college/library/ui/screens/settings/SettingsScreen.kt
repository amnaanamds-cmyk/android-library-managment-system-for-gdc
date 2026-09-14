package com.college.library.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import com.college.library.ui.components.FeatureListItem
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.college.library.ui.theme.DangerRed
import com.college.library.ui.theme.Gold
import com.college.library.utils.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToExport: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToReservations: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToCollegeProfile: () -> Unit = {},
    // ── NEW FEATURE NAVIGATION ─────────────────────────────────────
    onNavigateToHeatmap: () -> Unit = {},
    onNavigateToRecommendations: () -> Unit = {},
    onNavigateToFineWaiver: () -> Unit = {},
    onNavigateToReadingGoals: () -> Unit = {},
    onNavigateToClassification: () -> Unit = {},
    onNavigateToSpineLabels: () -> Unit = {},
    onNavigateToBiometric: () -> Unit = {},
    onNavigateToUnionCatalog: () -> Unit = {},
    // ── Operations screens (parity with the desktop and web apps) ───
    onNavigateToGateLog: () -> Unit = {},
    onNavigateToAcquisitions: () -> Unit = {},
    onNavigateToBookTransfers: () -> Unit = {},
    onNavigateToSerials: () -> Unit = {},
    onNavigateToIll: () -> Unit = {},
    onNavigateToMarcCatalog: () -> Unit = {},
    onNavigateToEnterprise: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: com.college.library.ui.screens.auth.AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var fineInput by remember { mutableStateOf("") }
    var durationInput by remember { mutableStateOf("") }
    var maxBooksInput by remember { mutableStateOf("") }

    // Initialize inputs when data loads
    LaunchedEffect(state.finePerDay, state.borrowDuration, state.maxBooks) {
        fineInput = state.finePerDay.toString()
        durationInput = state.borrowDuration.toString()
        maxBooksInput = state.maxBooks.toString()
    }

    // File Picker launcher (supports CSV & Excel)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importBooksFromFile(uri)
        }
    }

    // Handle Operation Success/Failure Toasts
    LaunchedEffect(state.importSuccessCount, state.importError, state.resetSuccess, state.seedSuccess) {
        if (state.importSuccessCount >= 0) {
            Toast.makeText(context, "Successfully imported ${state.importSuccessCount} books!", Toast.LENGTH_LONG).show()
            viewModel.clearStatus()
        }
        if (state.importError != null) {
            Toast.makeText(context, "Import failed: ${state.importError}", Toast.LENGTH_LONG).show()
            viewModel.clearStatus()
        }
        if (state.resetSuccess) {
            Toast.makeText(context, "Database cleared successfully!", Toast.LENGTH_SHORT).show()
            viewModel.clearStatus()
        }
        if (state.seedSuccess) {
            Toast.makeText(context, "Sample data seeded successfully!", Toast.LENGTH_SHORT).show()
            viewModel.clearStatus()
        }
    }

    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Maintenance", color = Gold, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Language Toggle ────────────────────────────────────────────────
            item {
                Text(
                    text = "Language / زبان",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.currentLanguage == AppLanguage.URDU) "اردو" else "English",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (state.currentLanguage == AppLanguage.URDU) "Switch to English" else "اردو میں تبدیل کریں",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.currentLanguage == AppLanguage.URDU,
                            onCheckedChange = { isUrdu ->
                                viewModel.setLanguage(if (isUrdu) AppLanguage.URDU else AppLanguage.ENGLISH)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Library Preferences",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = fineInput,
                            onValueChange = { fineInput = it },
                            label = { Text("Fine per Day (Rs.)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = durationInput,
                            onValueChange = { durationInput = it },
                            label = { Text("Borrow Duration (Days)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = maxBooksInput,
                            onValueChange = { maxBooksInput = it },
                            label = { Text("Max Borrow Limit (Books)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Dark Mode Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dark Mode", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                            Switch(
                                checked = state.darkModeEnabled,
                                onCheckedChange = { viewModel.setDarkMode(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // Font Scale Slider
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Font Scale: ${state.fontScale}x", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Slider(
                                value = state.fontScale,
                                onValueChange = { viewModel.setFontScale(it) },
                                valueRange = 0.8f..1.5f,
                                steps = 7,
                                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // Reset Onboarding Button
                        Button(
                            onClick = { viewModel.setOnboardingCompleted(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset Onboarding", color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // Save Preferences Button
                        Button(
                            onClick = {
                                val fine = fineInput.toFloatOrNull() ?: 1.0f
                                val duration = durationInput.toIntOrNull() ?: 14
                                val max = maxBooksInput.toIntOrNull() ?: 3
                                viewModel.saveSettings(fine, duration, max)
                                Toast.makeText(context, "Preferences saved!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Preferences", color = Color.White)
                        }
                    }
                }
            }

            // ── Cloud Synchronization ──────────────────────────────────────────
            item {
                Text(
                    text = "Cloud Synchronization",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Sync your local data with the cloud to backup books, members, and transactions.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (state.isSyncing) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Syncing with cloud...")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.triggerSync() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync All Data Now", color = Color.White)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Data Operations",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Import books in bulk using a CSV or Excel file (.xlsx / .xls). The file should contain columns for ISBN, Accession No, Title, Author, Publisher, etc.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (state.isImporting) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Parsing & importing books...", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { 
                                        viewModel.isImportingOrganized = true
                                        filePickerLauncher.launch("*/*") 
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Import Subject-Wise", color = Color.White, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { 
                                        viewModel.isImportingOrganized = false
                                        filePickerLauncher.launch("*/*") 
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Import Unorganized", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = "Maintenance Actions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.isSeeding) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Button(
                                    onClick = { viewModel.seedSampleData() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0A800)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Seed Sample", color = Color.White, fontSize = 12.sp)
                                }
                            }

                            if (state.isResetting) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Button(
                                    onClick = { showResetDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reset DB", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Backup & Recovery",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Automatic Crash Backup is ENABLED. If the app stops working unexpectedly, it will automatically save a secure backup of your database to your local Documents folder and prompt you to upload it to Google Drive on next launch.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Button(
                            onClick = { 
                                android.widget.Toast.makeText(context, "Simulating App Crash...", android.widget.Toast.LENGTH_SHORT).show()
                                throw RuntimeException("Simulated Crash to test Backup Feature!")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simulate App Crash (Test Backup)", color = Color.White)
                        }

                        Button(
                            onClick = { 
                                try {
                                    val dbFile = context.getDatabasePath("library_db")
                                    if (dbFile.exists()) {
                                        val backupDir = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "LibraryBackups")
                                        if (!backupDir.exists()) backupDir.mkdirs()
                                        val backupFile = java.io.File(backupDir, "library_db_manual_backup.sqlite")
                                        java.io.FileInputStream(dbFile).use { input ->
                                            java.io.FileOutputStream(backupFile).use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        android.widget.Toast.makeText(context, "Backup saved to Documents/LibraryBackups", android.widget.Toast.LENGTH_LONG).show()
                                        
                                        // Upload to Google Drive
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", backupFile)
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "application/x-sqlite3"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Upload Backup to Google Drive"))
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Backup failed", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Manual Backup to Local & Drive", color = Color.White)
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Advanced Features",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onNavigateToBackup,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Backup & Restore Center", color = Color.White)
                        }
                        Button(
                            onClick = onNavigateToExport,
                            colors = ButtonDefaults.buttonColors(containerColor = Gold),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Reports (PDF)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onNavigateToStats,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.BarChart, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Library Analytics", color = Color.White)
                        }
                        Button(
                            onClick = onNavigateToReservations,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Book Reservations", color = Color.White)
                        }
                        Button(
                            onClick = onNavigateToNotifications,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Notification Center", color = Color.White)
                        }
                        Button(
                            onClick = onNavigateToCollegeProfile,
                            colors = ButtonDefaults.buttonColors(containerColor = Gold),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("College Profile", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── NEW PREMIUM FEATURES ─────────────────────────────────────
            item {
                Text(
                    text = "🚀 New Premium Features",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeatureListItem(
                        icon = Icons.Default.AutoAwesome,
                        label = "AI Smart Book Recommender",
                        accent = Color(0xFF7C3AED),
                        onClick = onNavigateToRecommendations,
                    )
                    FeatureListItem(
                        icon = Icons.Default.Whatshot,
                        label = "Library Usage Heatmap",
                        accent = Color(0xFFEF4444),
                        onClick = onNavigateToHeatmap,
                    )
                    FeatureListItem(
                        icon = Icons.Default.Balance,
                        label = "AI Fine Waiver Judge",
                        accent = Color(0xFF10B981),
                        onClick = onNavigateToFineWaiver,
                    )
                    FeatureListItem(
                        icon = Icons.Default.EmojiEvents,
                        label = "Reading Goals & Streaks",
                        accent = Color(0xFFF59E0B),
                        onClick = onNavigateToReadingGoals,
                    )
                    FeatureListItem(
                        icon = Icons.Default.Folder,
                        label = "Classification System",
                        accent = Color(0xFF3B82F6),
                        onClick = onNavigateToClassification,
                    )
                    FeatureListItem(
                        icon = Icons.Default.Sell,
                        label = "Spine Label Generator",
                        accent = Color(0xFF8B5CF6),
                        onClick = onNavigateToSpineLabels,
                    )
                    FeatureListItem(
                        icon = Icons.Default.Fingerprint,
                        label = "Biometric Verification",
                        accent = Color(0xFF10B981),
                        onClick = onNavigateToBiometric,
                    )
                    FeatureListItem(
                        icon = Icons.Default.Public,
                        label = "Union Catalogue",
                        accent = Color(0xFFEC4899),
                        onClick = onNavigateToUnionCatalog,
                    )
                    FeatureListItem(
                        icon = Icons.Default.Shield,
                        label = "Gate Log",
                        accent = Color(0xFF0EA5E9),
                        onClick = onNavigateToGateLog,
                    )
                    FeatureListItem(
                        icon = Icons.Default.Assignment,
                        label = "Acquisitions",
                        accent = Color(0xFF14B8A6),
                        onClick = onNavigateToAcquisitions,
                    )
                    FeatureListItem(
                        icon = Icons.Default.SwapHoriz,
                        label = "Book Transfers",
                        accent = Color(0xFF6366F1),
                        onClick = onNavigateToBookTransfers,
                    )
                    FeatureListItem(
                        icon = Icons.Default.Article,
                        label = "Serials",
                        accent = Color(0xFFA855F7),
                        onClick = onNavigateToSerials,
                    )
                    FeatureListItem(
                        icon = Icons.Default.AccountBalance,
                        label = "Inter-Library Loans",
                        accent = Color(0xFFF97316),
                        onClick = onNavigateToIll,
                    )
                    FeatureListItem(
                        icon = Icons.Default.Description,
                        label = "MARC Catalogue",
                        accent = Color(0xFF0891B2),
                        onClick = onNavigateToMarcCatalog,
                    )
                    FeatureListItem(
                        icon = Icons.Default.Star,
                        label = "Enterprise Features",
                        accent = Color(0xFF7C3AED),
                        onClick = onNavigateToEnterprise,
                    )
                }
            }

            item {
                Text(
                    text = "App Information",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onNavigateToAbout,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("About App", color = Color.White)
                        }

                        Button(
                            onClick = { shareApp(context, coroutineScope) },
                            colors = ButtonDefaults.buttonColors(containerColor = Gold),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share App (.apk)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        android.widget.Toast.makeText(context, "Logged out successfully!", android.widget.Toast.LENGTH_SHORT).show()
                        authViewModel.logout() 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = "Logout")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Database?") },
            text = { Text("This will permanently delete all books, members, issues, and transactions. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetDatabase()
                        showResetDialog = false
                    }
                ) {
                    Text("RESET", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

private fun shareApp(context: Context, coroutineScope: kotlinx.coroutines.CoroutineScope) {
    coroutineScope.launch {
        try {
            val isSplit = context.applicationInfo.splitSourceDirs?.isNotEmpty() == true
            
            val intent = withContext(Dispatchers.IO) {
                val appInfo = context.applicationInfo
                val baseApk = File(appInfo.sourceDir)
                val splitApks = appInfo.splitSourceDirs?.map { File(it) } ?: emptyList()
                
                // Copy APKs to cache directory so they can be shared via FileProvider
                val cachePath = File(context.cacheDir, "shared_apk")
                cachePath.mkdirs()
                
                if (splitApks.isEmpty()) {
                    // Single Universal APK, simple copy
                    val destFile = File(cachePath, "GDC_Library.apk")
                    FileInputStream(baseApk).use { inStream ->
                        FileOutputStream(destFile).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", destFile)
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.android.package-archive"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    // Split APKs installed (e.g. from Android Studio deployment)
                    // We must package all splits together so no functionality or resources are missing
                    val destFile = File(cachePath, "GDC_Library.apks")
                    java.util.zip.ZipOutputStream(FileOutputStream(destFile)).use { zos ->
                        val filesToZip = listOf(baseApk) + splitApks
                        filesToZip.forEach { file ->
                            FileInputStream(file).use { fis ->
                                val entry = java.util.zip.ZipEntry(file.name)
                                zos.putNextEntry(entry)
                                fis.copyTo(zos)
                                zos.closeEntry()
                            }
                        }
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", destFile)
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                if (isSplit) {
                    Toast.makeText(context, "App is installed as Split APKs. Sharing as .apks (Requires a Split Installer like SAI to install).", Toast.LENGTH_LONG).show()
                }
                context.startActivity(Intent.createChooser(intent, "Share App Via"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error sharing app: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
