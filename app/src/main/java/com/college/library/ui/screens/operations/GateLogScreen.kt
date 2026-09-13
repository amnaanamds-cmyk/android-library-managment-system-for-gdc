package com.college.library.ui.screens.operations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.college.library.data.repository.long
import com.college.library.data.repository.longOrNull
import com.college.library.data.repository.str
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.Gold

/**
 * Gate log — who is in the library right now, and today's footfall.
 *
 * Writes /institutions/{id}/visitor_log, the same collection the web app's Gate
 * Log page uses, so an entry recorded on a phone at the door shows up on the
 * librarian's web dashboard immediately.
 */
@Composable
fun GateLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: GateLogViewModel = hiltViewModel(),
) {
    val records by viewModel.records.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val today = todayStamp()
    // Only today's entries: the log is a daily register, and showing every
    // entry ever recorded would make the "currently inside" count meaningless.
    val todays = records.filter { it.str("dateStr") == today }
        .sortedByDescending { it.long("entryTime") }
    val inside = todays.filter { it.longOrNull("exitTime") == null }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { outer ->
        Box(Modifier.padding(outer)) {
            OperationsScaffold(
                title = "Gate Log",
                subtitle = "Visitor register for $today",
                onNavigateBack = onNavigateBack,
                onAdd = { showAdd = true },
                addLabel = "Sign in visitor",
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (!viewModel.hasInstitution) {
                        NotSignedInNotice()
                        return@Column
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatTile("Currently inside", inside.size.toString(), CardGreen, Modifier.weight(1f))
                        StatTile("Visits today", todays.size.toString(), Gold, Modifier.weight(1f))
                    }

                    if (todays.isEmpty()) {
                        EmptyState("🚪", "No visitors logged today", "Tap Sign in visitor to record an entry.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(todays, key = { it.str("syncId") }) { entry ->
                                VisitorRow(
                                    entry = entry,
                                    busy = busy,
                                    onSignOut = {
                                        viewModel.patch(
                                            entry.str("syncId"),
                                            mapOf("exitTime" to System.currentTimeMillis()),
                                            "Signed out.",
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        SignInVisitorDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, type, purpose, memberId ->
                viewModel.add(
                    mapOf(
                        "name" to name,
                        "visitorType" to type,
                        "purpose" to purpose,
                        "memberId" to memberId,
                        "entryTime" to System.currentTimeMillis(),
                        "exitTime" to null,
                        "dateStr" to todayStamp(),
                    ),
                    "$name signed in.",
                )
                showAdd = false
            },
        )
    }
}

@Composable
private fun VisitorRow(
    entry: Map<String, Any?>,
    busy: Boolean,
    onSignOut: () -> Unit,
) {
    val exitTime = entry.longOrNull("exitTime")
    val stillInside = exitTime == null

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.str("name", "Unnamed visitor"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(entry.str("visitorType", "Guest"))
                        val purpose = entry.str("purpose")
                        if (purpose.isNotBlank()) append(" · $purpose")
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (stillInside) {
                        "In at ${formatTime(entry.long("entryTime"))}"
                    } else {
                        "In ${formatTime(entry.long("entryTime"))} · Out ${formatTime(exitTime!!)}"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            if (stillInside) {
                Button(
                    onClick = onSignOut,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                ) {
                    Text(
                        "Sign out",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            } else {
                StatusChip("Left")
            }
        }
    }
}

@Composable
private fun SignInVisitorDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, purpose: String, memberId: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Student") }
    var purpose by remember { mutableStateOf("") }
    var memberId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in a visitor", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                DialogField("Full name", name, { name = it })
                DialogDropdown(
                    "Visitor type",
                    listOf("Student", "Staff", "Faculty", "Guest"),
                    type,
                    onSelect = { type = it },
                )
                DialogField("Member ID (optional)", memberId, { memberId = it })
                DialogField("Purpose (optional)", purpose, { purpose = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), type, purpose.trim(), memberId.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Sign in", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
