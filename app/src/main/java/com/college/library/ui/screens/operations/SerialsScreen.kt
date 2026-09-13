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
import com.college.library.data.model.Serial
import com.college.library.data.repository.int
import com.college.library.data.repository.str
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.Gold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serials — periodicals the library subscribes to, and issue receipting.
 *
 * Writes /institutions/{id}/serials. The desktop Serials screen previously kept
 * this in local SQLite only, so a subscription recorded there never reached any
 * other device; both now use the same collection.
 */
@Composable
fun SerialsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SerialsViewModel = hiltViewModel(),
) {
    val records by viewModel.records.collectAsState()
    val message by viewModel.message.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val serials = records.sortedBy { it.str("title").lowercase() }
    val active = serials.count { it.str("status") == "Active" }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { outer ->
        Box(Modifier.padding(outer)) {
            OperationsScaffold(
                title = "Serials",
                subtitle = "Periodicals and subscriptions",
                onNavigateBack = onNavigateBack,
                onAdd = { showAdd = true },
                addLabel = "Add serial",
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
                        StatTile("Subscriptions", serials.size.toString(), Gold, Modifier.weight(1f))
                        StatTile("Active", active.toString(), CardGreen, Modifier.weight(1f))
                    }

                    if (serials.isEmpty()) {
                        EmptyState("📰", "No serials yet", "Tap Add serial to record a subscription.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(serials, key = { it.str("syncId") }) { serial ->
                                SerialRow(
                                    serial = serial,
                                    onReceiveIssue = {
                                        // Receipting an issue stamps today and
                                        // increments the count, which is what the
                                        // desktop "Receive Latest Issue" action does.
                                        viewModel.patch(
                                            serial.str("syncId"),
                                            mapOf(
                                                "lastIssueReceived" to todayStamp(),
                                                "issuesReceived" to serial.int("issuesReceived") + 1,
                                            ),
                                            "Issue receipted for ${serial.str("title")}.",
                                        )
                                    },
                                    onStatusChange = { newStatus ->
                                        viewModel.patch(
                                            serial.str("syncId"),
                                            mapOf("status" to newStatus),
                                            "Marked $newStatus.",
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
        NewSerialDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, issn, frequency, publisher, subEnd ->
                viewModel.add(
                    mapOf(
                        "title" to title,
                        "issn" to issn,
                        "frequency" to frequency,
                        "publisher" to publisher,
                        "status" to "Active",
                        "subscriptionEnd" to subEnd,
                        "lastIssueReceived" to "",
                        "issuesReceived" to 0,
                    ),
                    "Subscription added.",
                )
                showAdd = false
            },
        )
    }
}

@Composable
private fun SerialRow(
    serial: Map<String, Any?>,
    onReceiveIssue: () -> Unit,
    onStatusChange: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(serial.str("title", "Untitled"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        buildString {
                            append(serial.str("frequency", "—"))
                            val pub = serial.str("publisher")
                            if (pub.isNotBlank()) append(" · $pub")
                            val issn = serial.str("issn")
                            if (issn.isNotBlank()) append(" · ISSN $issn")
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
                StatusMenu(
                    current = serial.str("status", "Active"),
                    options = Serial.STATUSES,
                    onSelect = onStatusChange,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "${serial.int("issuesReceived")} issues received",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CardGreen,
                    )
                    val last = serial.str("lastIssueReceived")
                    Text(
                        if (last.isBlank()) "None received yet" else "Latest: $last",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                OutlinedButton(onClick = onReceiveIssue) {
                    Text("Receive issue", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NewSerialDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        issn: String,
        frequency: String,
        publisher: String,
        subscriptionEnd: String,
    ) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var issn by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("Monthly") }
    var publisher by remember { mutableStateOf("") }
    var subEnd by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a serial", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                DialogField("Title", title, { title = it })
                DialogField("ISSN (optional)", issn, { issn = it })
                DialogDropdown("Frequency", Serial.FREQUENCIES, frequency, onSelect = { frequency = it })
                DialogField("Publisher (optional)", publisher, { publisher = it })
                DialogField("Subscription ends (YYYY-MM-DD, optional)", subEnd, { subEnd = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(title.trim(), issn.trim(), frequency, publisher.trim(), subEnd.trim())
                },
                enabled = title.isNotBlank(),
            ) { Text("Add", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
