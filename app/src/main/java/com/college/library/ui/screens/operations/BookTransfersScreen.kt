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
import com.college.library.data.model.BookTransfer
import com.college.library.data.repository.int
import com.college.library.data.repository.long
import com.college.library.data.repository.str
import com.college.library.ui.theme.CardBlue
import com.college.library.ui.theme.CardGreen

/**
 * Book transfers between colleges in the network.
 *
 * Writes /institutions/{id}/book_transfers, matching the web Book Transfers
 * page and the desktop Transfer screen.
 *
 * A transfer is recorded in the raising college's own tenant. The receiving
 * college sees it once the two are paired through the sync network, which is
 * the same boundary the union catalogue uses — tenant data stays isolated
 * unless a college has explicitly linked to a partner.
 */
@Composable
fun BookTransfersScreen(
    onNavigateBack: () -> Unit,
    viewModel: BookTransfersViewModel = hiltViewModel(),
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

    val myCollege = viewModel.myCollegeId
    val transfers = records.sortedByDescending { it.long("requestedAt") }
    val outgoing = transfers.filter { it.str("fromCollege") == myCollege }
    val incoming = transfers.filter { it.str("toCollege") == myCollege }

    var tab by remember { mutableIntStateOf(0) }
    val shown = if (tab == 0) outgoing else incoming

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { outer ->
        Box(Modifier.padding(outer)) {
            OperationsScaffold(
                title = "Book Transfers",
                subtitle = myCollege.ifEmpty { "No institution" },
                onNavigateBack = onNavigateBack,
                onAdd = { showAdd = true },
                addLabel = "New transfer",
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    if (!viewModel.hasInstitution) {
                        NotSignedInNotice()
                        return@Column
                    }

                    TabRow(selectedTabIndex = tab) {
                        Tab(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            text = { Text("Outgoing (${outgoing.size})", fontSize = 13.sp) },
                        )
                        Tab(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            text = { Text("Incoming (${incoming.size})", fontSize = 13.sp) },
                        )
                    }

                    if (shown.isEmpty()) {
                        EmptyState(
                            "🔄",
                            if (tab == 0) "No outgoing transfers" else "No incoming transfers",
                            if (tab == 0) "Tap New transfer to send a book to another college." else null,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(shown, key = { it.str("syncId") }) { transfer ->
                                TransferRow(transfer, outgoing = tab == 0) { newStatus ->
                                    viewModel.patch(
                                        transfer.str("syncId"),
                                        mapOf(
                                            "status" to newStatus,
                                            "updatedAt" to System.currentTimeMillis(),
                                        ),
                                        "Transfer marked $newStatus.",
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
        NewTransferDialog(
            myCollege = myCollege,
            onDismiss = { showAdd = false },
            onConfirm = { toCollege, title, isbn, qty, notes ->
                val now = System.currentTimeMillis()
                viewModel.add(
                    mapOf(
                        "fromCollege" to myCollege,
                        "toCollege" to toCollege,
                        "bookTitle" to title,
                        "bookIsbn" to isbn,
                        "quantity" to qty,
                        "status" to BookTransfer.STATUS_REQUESTED,
                        "requestedAt" to now,
                        "updatedAt" to now,
                        "notes" to notes,
                    ),
                    "Transfer requested.",
                )
                showAdd = false
            },
        )
    }
}

@Composable
private fun TransferRow(
    transfer: Map<String, Any?>,
    outgoing: Boolean,
    onStatusChange: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        transfer.str("bookTitle", "Untitled"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (outgoing) {
                            "To ${transfer.str("toCollege", "—")}"
                        } else {
                            "From ${transfer.str("fromCollege", "—")}"
                        },
                        fontSize = 12.sp,
                        color = if (outgoing) CardBlue else CardGreen,
                        fontWeight = FontWeight.Bold,
                    )
                }
                StatusMenu(
                    current = transfer.str("status", "requested"),
                    options = BookTransfer.STATUSES,
                    onSelect = onStatusChange,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${transfer.int("quantity", 1)} copies" +
                        transfer.str("bookIsbn").let { if (it.isBlank()) "" else " · ISBN $it" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Text(
                    formatDate(transfer.long("requestedAt")),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun NewTransferDialog(
    myCollege: String,
    onDismiss: () -> Unit,
    onConfirm: (toCollege: String, title: String, isbn: String, qty: Int, notes: String) -> Unit,
) {
    var toCollege by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var isbn by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }

    val qtyValue = qty.toIntOrNull() ?: 0
    // Sending to yourself is always a mistake, and would produce a transfer
    // that shows in both tabs.
    val sameCollege = toCollege.trim().equals(myCollege, ignoreCase = true)
    val valid = toCollege.isNotBlank() && title.isNotBlank() && qtyValue > 0 && !sameCollege

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New book transfer", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                DialogField("Destination college ID", toCollege, { toCollege = it.uppercase() })
                if (sameCollege && toCollege.isNotBlank()) {
                    Text(
                        "That is this college. Choose a different destination.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                DialogField("Book title", title, { title = it })
                DialogField("ISBN (optional)", isbn, { isbn = it })
                DialogField("Quantity", qty, { qty = it }, numeric = true)
                DialogField("Notes (optional)", notes, { notes = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(toCollege.trim(), title.trim(), isbn.trim(), qtyValue, notes.trim())
                },
                enabled = valid,
            ) { Text("Request transfer", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
