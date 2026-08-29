package com.college.library.ui.screens.director

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.college.library.data.CollegeQrHelper
import com.college.library.data.CollegeService
import com.college.library.data.FirebaseAvailability
import com.college.library.data.model.College
import com.college.library.domain.session.SessionManager
import com.college.library.ui.components.QrImageGenerator
import kotlinx.coroutines.launch
import java.awt.Cursor
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne

/**
 * Multi-college Director Dashboard.
 *
 * Shows every college assigned to the logged-in director as a live card
 * (books / members / circulation counters), lets the director create a
 * new college (which produces a QR link + invite code) or join an
 * existing one by invite code, and supports drilling into a college's
 * live book catalog.
 *
 * On desktop builds without a configured Firebase project the dashboard
 * renders a local DEMO card backed by the on-device database so the
 * network UI remains explorable in offline mode.
 */
@Composable
fun DirectorDashboardScreen(
    sessionManager: SessionManager,
    collegeService: CollegeService
) {
    val scope = rememberCoroutineScope()
    val directorUid = sessionManager.token ?: ""
    val isOnline = FirebaseAvailability.isInitialized

    // Live colleges assigned to this director.
    val liveColleges by collegeService.observeColleges(directorUid)
        .collectAsState(initial = emptyList())

    // DEMO fallback (offline desktop): a local card seeded from the on-device database.
    val database = remember { com.college.library.data.DatabaseHelper.getDatabase() }
    val totalBooksFlow = remember {
        database.bookQueriesQueries.getTotalCount().asFlow().mapToOne(kotlinx.coroutines.Dispatchers.IO)
    }
    val totalBooks by totalBooksFlow.collectAsState(initial = 0L)
    val totalMembersFlow = remember {
        database.memberQueriesQueries.getTotalCount().asFlow().mapToOne(kotlinx.coroutines.Dispatchers.IO)
    }
    val totalMembers by totalMembersFlow.collectAsState(initial = 0L)

    val demoCollege = remember {
        College(
            collegeId = sessionManager.institutionId ?: "gdc-peshawar",
            collegeName = "${sessionManager.librarianName ?: "GDC Peshawar"} Library",
            inviteCode = "DEMO01",
            directorUid = directorUid,
            createdAt = 0L
        )
    }

    val colleges = if (isOnline) liveColleges else listOf(demoCollege.copy(booksCount = totalBooks, membersCount = totalMembers))

    // Dialogs
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var qrCollege by remember { mutableStateOf<College?>(null) }
    var detailCollege by remember { mutableStateOf<College?>(null) }
    var createName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        // Banner for offline demo mode.
        if (!isOnline) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF7E6), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFF0C36D), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Offline demo mode - the Director Network connects in real time once this build is linked to a Firebase project.",
                    fontSize = 13.sp,
                    color = Color(0xFF8A6D1A),
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Header actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "College Network",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
                )
                Text(
                    text = "Live oversight of every library in your network.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isOnline) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                liveColleges.forEach { collegeService.refreshCollegeStats(it.collegeId) }
                                isLoading = false
                            }
                        },
                        enabled = !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Refresh Stats", tint = MaterialTheme.colors.primary)
                    }
                }
                OutlinedButton(onClick = { showJoinDialog = true }) {
                    Icon(Icons.Default.Link, contentDescription = "Join", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Join College")
                }
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = MaterialTheme.colors.onPrimary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create College")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (colleges.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No colleges in your network yet. Create one or join via invite code.",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                colleges.forEach { college ->
                    DirectorCollegeCard(
                        college = college,
                        onOpen = { detailCollege = college },
                        onShowQr = { qrCollege = college }
                    )
                }
            }
        }
    }

    // ── Create College dialog ─────────────────────────────────
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create College") },
            text = {
                Column {
                    Text(
                        text = "A QR link and 6-digit invite code are generated automatically.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        label = { Text("College / Institution Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (message != null) {
                        Text(
                            text = message!!,
                            color = MaterialTheme.colors.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (createName.isBlank()) {
                            message = "Please enter a college name."
                            return@Button
                        }
                        isLoading = true
                        message = null
                        scope.launch {
                            collegeService.createCollege(createName.trim(), directorUid)
                                .onSuccess { college ->
                                    showCreateDialog = false
                                    createName = ""
                                    qrCollege = college
                                }
                                .onFailure { ex ->
                                    message = ex.message ?: "Could not create the college."
                                }
                            isLoading = false
                        }
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Create")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Join College dialog ───────────────────────────────────
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join College") },
            text = {
                Column {
                    Text(
                        text = "Enter the 6-digit invite code shared by another college.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it },
                        label = { Text("Invite Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (message != null) {
                        Text(
                            text = message!!,
                            color = MaterialTheme.colors.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (joinCode.isBlank()) {
                            message = "Please enter an invite code."
                            return@Button
                        }
                        isLoading = true
                        message = null
                        scope.launch {
                            collegeService.findCollegeByInviteCode(joinCode.trim())
                                .onSuccess { college ->
                                    collegeService.assignDirector(college.collegeId, directorUid)
                                    showJoinDialog = false
                                    joinCode = ""
                                    message = null
                                }
                                .onFailure { ex ->
                                    message = ex.message ?: "Could not join the college."
                                }
                            isLoading = false
                        }
                    },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Join")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── QR link dialog ────────────────────────────────────────
    qrCollege?.let { college ->
        Dialog(onDismissRequest = { qrCollege = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = 8.dp,
                backgroundColor = MaterialTheme.colors.surface
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Link ${college.collegeName}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scan to add this library to your director dashboard.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    var showMobileQr by remember { mutableStateOf(true) }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = { showMobileQr = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = if (showMobileQr) MaterialTheme.colors.primary else Color.Gray)
                        ) { Text("Mobile Link", fontWeight = if (showMobileQr) FontWeight.Bold else FontWeight.Normal) }
                        Spacer(modifier = Modifier.width(16.dp))
                        TextButton(
                            onClick = { showMobileQr = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = if (!showMobileQr) MaterialTheme.colors.primary else Color.Gray)
                        ) { Text("Admin JSON", fontWeight = if (!showMobileQr) FontWeight.Bold else FontWeight.Normal) }
                    }

                    val payload = remember(college.collegeId, showMobileQr) {
                        if (showMobileQr) CollegeQrHelper.generateMobilePayload(college.collegeId)
                        else CollegeQrHelper.generatePayload(college.collegeId, college.collegeName)
                    }
                    
                    Box(
                        modifier = Modifier.border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(8.dp)
                    ) {
                        Image(
                            bitmap = remember(payload) { QrImageGenerator.generate(payload, 256) },
                            contentDescription = "College QR link",
                            modifier = Modifier.size(256.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (showMobileQr) "SCAN WITH MOBILE APP" else "INVITE CODE SYSTEM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Invite code: ${college.inviteCode}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "College ID: ${college.collegeId}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { qrCollege = null }) {
                        Text("Done")
                    }
                }
            }
        }
    }

    // ── College detail dialog (live drill-down) ───────────────
    detailCollege?.let { college ->
        CollegeDetailDialog(
            college = college,
            collegeService = collegeService,
            onClose = { detailCollege = null },
            onShowQr = { qrCollege = college }
        )
    }
}

/**
 * A single network card: name, invite code, live counters and quick
 * actions (open catalog, show QR).
 */
@Composable
fun DirectorCollegeCard(
    college: College,
    onOpen: () -> Unit,
    onShowQr: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isHovered) 8.dp else 0.dp, RoundedCornerShape(12.dp))
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        elevation = 0.dp,
        backgroundColor = MaterialTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = college.collegeName.take(1).uppercase(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colors.primary
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = college.collegeName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
                )
                Text(
                    text = "ID ${college.collegeId}  •  Invite ${college.inviteCode}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
            DirectorStat(value = college.booksCount.toString(), label = "Books")
            DirectorStat(value = college.membersCount.toString(), label = "Members")
            DirectorStat(value = college.circulationCount.toString(), label = "Circulation")
            Spacer(modifier = Modifier.width(16.dp))
            TextButton(onClick = onShowQr) {
                Icon(Icons.Default.QrCode, contentDescription = "QR", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("QR Link")
            }
        }
    }
}

@Composable
fun DirectorStat(value: String, label: String) {
    Column(
        modifier = Modifier.width(88.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colors.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * Drill-down into a college's live catalog. On desktop demo mode the
 * catalog is empty (Firestore is not connected) so an empty state is shown.
 */
@Composable
fun CollegeDetailDialog(
    college: College,
    collegeService: CollegeService,
    onClose: () -> Unit,
    onShowQr: () -> Unit
) {
    val books by collegeService.observeCollegeBooks(college.collegeId)
        .collectAsState(initial = emptyList())

    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = 8.dp,
            backgroundColor = MaterialTheme.colors.surface,
            modifier = Modifier.width(640.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = college.collegeName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.onSurface
                        )
                        Text(
                            text = "College ID ${college.collegeId}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onShowQr) {
                    Icon(Icons.Default.QrCode, contentDescription = "QR", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Show QR link")
                }
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Live Catalog",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (books.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (FirebaseAvailability.isInitialized)
                                "No books synced to this college yet."
                            else
                                "Offline demo - connect Firebase to see the live catalog.",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    books.forEach { book ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = book.title,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colors.onSurface
                            )
                            Text(
                                text = book.author,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}
