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
import com.college.library.data.model.IllRequest
import com.college.library.data.repository.long
import com.college.library.data.repository.str
import com.college.library.ui.theme.CardOrange
import com.college.library.ui.theme.Gold

/**
 * Inter-library loan requests raised against partner institutions.
 *
 * Writes /institutions/{id}/ill_requests, the collection the web union
 * catalogue already reads. The desktop ILL screen previously kept these in
 * local SQLite only, so a request raised there was invisible everywhere else.
 */
@Composable
fun IllScreen(
    onNavigateBack: () -> Unit,
    viewModel: IllViewModel = hiltViewModel(),
) {
    val records by viewModel.records.collectAsState()
    val message by viewModel.message.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("All") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val requests = records.sortedByDescending { it.long("requestDate") }
    val shown = if (filter == "All") requests else requests.filter { it.str("status") == filter }
    val open = requests.count { it.str("status") !in listOf("Fulfilled", "Returned", "Rejected") }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { outer ->
        Box(Modifier.padding(outer)) {
            OperationsScaffold(
                title = "Inter-Library Loans",
                subtitle = "$open open of ${requests.size}",
                onNavigateBack = onNavigateBack,
                onAdd = { showAdd = true },
                addLabel = "New request",
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
                        StatTile("Open requests", open.toString(), CardOrange, Modifier.weight(1f))
                        StatTile("Total", requests.size.toString(), Gold, Modifier.weight(1f))
                    }

                    StatusFilterRow(
                        options = listOf("All") + IllRequest.STATUSES,
                        selected = filter,
                        onSelect = { filter = it },
                    )

                    if (shown.isEmpty()) {
                        EmptyState(
                            "🌍",
                            if (requests.isEmpty()) "No ILL requests yet" else "No $filter requests",
                            if (requests.isEmpty()) "Request a title from a partner college." else null,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(shown, key = { it.str("syncId") }) { req ->
                                IllRow(req) { newStatus ->
                                    val patch = mutableMapOf<String, Any?>("status" to newStatus)
                                    // Stamp the fulfilment date so turnaround
                                    // time can be reported later.
                                    if (newStatus == "Fulfilled") {
                                        patch["fulfilledDate"] = System.currentTimeMillis()
                                    }
                                    viewModel.patch(
                                        req.str("syncId"),
                                        patch,
                                        "Request marked $newStatus.",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        NewIllDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title, author, isbn, memberId, memberName, target, notes ->
                viewModel.add(
                    mapOf(
                        "bookTitle" to title,
                        "author" to author,
                        "isbn" to isbn,
                        "memberId" to memberId,
                        "memberName" to memberName,
                        "targetInstitution" to target,
                        "status" to IllRequest.STATUS_PENDING,
                        "requestDate" to System.currentTimeMillis(),
                        "fulfilledDate" to null,
                        "notes" to notes,
                    ),
                    "ILL request raised.",
                )
                showAdd = false
            },
        )
    }
}

@Composable
private fun IllRow(req: Map<String, Any?>, onStatusChange: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(req.str("bookTitle", "Untitled"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val author = req.str("author")
                    if (author.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            author,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }
                StatusMenu(
                    current = req.str("status", "Pending"),
                    options = IllRequest.STATUSES,
                    onSelect = onStatusChange,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "From: ${req.str("targetInstitution", "—")}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
            )
            val member = req.str("memberName")
            if (member.isNotBlank()) {
                Text(
                    "For: $member",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Requested ${formatDate(req.long("requestDate"))}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun NewIllDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        author: String,
        isbn: String,
        memberId: String,
        memberName: String,
        target: String,
        notes: String,
    ) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var isbn by remember { mutableStateOf("") }
    var memberId by remember { mutableStateOf("") }
    var memberName by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val valid = title.isNotBlank() && target.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New ILL request", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                DialogField("Book title", title, { title = it })
                DialogField("Author (optional)", author, { author = it })
                DialogField("ISBN (optional)", isbn, { isbn = it })
                DialogField("Target institution", target, { target = it })
                DialogField("Requesting member ID (optional)", memberId, { memberId = it })
                DialogField("Member name (optional)", memberName, { memberName = it })
                DialogField("Notes (optional)", notes, { notes = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        title.trim(), author.trim(), isbn.trim(),
                        memberId.trim(), memberName.trim(), target.trim(), notes.trim(),
                    )
                },
                enabled = valid,
            ) { Text("Raise request", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
