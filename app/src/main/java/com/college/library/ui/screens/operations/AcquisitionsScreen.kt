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
import com.college.library.data.model.PurchaseOrder
import com.college.library.data.repository.dbl
import com.college.library.data.repository.int
import com.college.library.data.repository.long
import com.college.library.data.repository.str
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.CardOrange
import com.college.library.ui.theme.Gold

/**
 * Acquisitions — purchase orders raised with vendors.
 *
 * Writes /institutions/{id}/purchase_orders, matching the web Acquisitions page
 * and the desktop Acquisitions screen, so an order raised anywhere is visible
 * everywhere.
 */
@Composable
fun AcquisitionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AcquisitionsViewModel = hiltViewModel(),
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

    val orders = records.sortedByDescending { it.long("orderDate") }
    val shown = if (filter == "All") orders else orders.filter { it.str("status") == filter }

    // Committed spend excludes cancelled orders — money that will not be spent
    // should not appear in the budget figure.
    val committed = orders
        .filter { it.str("status") != "Cancelled" }
        .sumOf { it.dbl("totalAmount") }
    val pending = orders.count { it.str("status") == "Pending" }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { outer ->
        Box(Modifier.padding(outer)) {
            OperationsScaffold(
                title = "Acquisitions",
                subtitle = "${orders.size} purchase orders",
                onNavigateBack = onNavigateBack,
                onAdd = { showAdd = true },
                addLabel = "New order",
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
                        StatTile("Committed spend", "Rs ${committed.toLong()}", Gold, Modifier.weight(1f))
                        StatTile("Awaiting approval", pending.toString(), CardOrange, Modifier.weight(1f))
                    }

                    StatusFilterRow(
                        options = listOf("All") + PurchaseOrder.STATUSES,
                        selected = filter,
                        onSelect = { filter = it },
                    )

                    if (shown.isEmpty()) {
                        EmptyState(
                            "🧾",
                            if (orders.isEmpty()) "No purchase orders yet" else "No $filter orders",
                            if (orders.isEmpty()) "Tap New order to raise one." else null,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(shown, key = { it.str("syncId") }) { order ->
                                PurchaseOrderRow(order) { newStatus ->
                                    viewModel.patch(
                                        order.str("syncId"),
                                        mapOf("status" to newStatus),
                                        "Order marked $newStatus.",
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
        NewOrderDialog(
            onDismiss = { showAdd = false },
            onConfirm = { vendor, title, isbn, qty, unitPrice, notes ->
                viewModel.add(
                    mapOf(
                        "vendorName" to vendor,
                        "bookTitle" to title,
                        "isbn" to isbn,
                        "quantity" to qty,
                        "unitPrice" to unitPrice,
                        "totalAmount" to qty * unitPrice,
                        "status" to PurchaseOrder.STATUS_PENDING,
                        "orderDate" to System.currentTimeMillis(),
                        "notes" to notes,
                    ),
                    "Purchase order raised.",
                )
                showAdd = false
            },
        )
    }
}

@Composable
internal fun StatusFilterRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options) { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(option, fontSize = 12.sp) },
            )
        }
    }
}

@Composable
private fun PurchaseOrderRow(order: Map<String, Any?>, onStatusChange: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        order.str("bookTitle", "Untitled"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Vendor: ${order.str("vendorName", "—")}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
                StatusMenu(
                    current = order.str("status", "Pending"),
                    options = PurchaseOrder.STATUSES,
                    onSelect = onStatusChange,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${order.int("quantity", 1)} copies · Rs ${order.dbl("totalAmount").toLong()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CardGreen,
                )
                Text(
                    formatDate(order.long("orderDate")),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            val notes = order.str("notes")
            if (notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    notes,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun NewOrderDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        vendor: String,
        title: String,
        isbn: String,
        qty: Int,
        unitPrice: Double,
        notes: String,
    ) -> Unit,
) {
    var vendor by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var isbn by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val qtyValue = qty.toIntOrNull() ?: 0
    val priceValue = price.toDoubleOrNull() ?: 0.0
    val valid = vendor.isNotBlank() && title.isNotBlank() && qtyValue > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New purchase order", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                DialogField("Vendor", vendor, { vendor = it })
                DialogField("Book title", title, { title = it })
                DialogField("ISBN (optional)", isbn, { isbn = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        DialogField("Quantity", qty, { qty = it }, numeric = true)
                    }
                    Box(Modifier.weight(1f)) {
                        DialogField("Unit price", price, { price = it }, numeric = true)
                    }
                }
                if (qtyValue > 0 && priceValue > 0) {
                    Text(
                        "Total: Rs ${(qtyValue * priceValue).toLong()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Gold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                DialogField("Notes (optional)", notes, { notes = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(vendor.trim(), title.trim(), isbn.trim(), qtyValue, priceValue, notes.trim())
                },
                enabled = valid,
            ) { Text("Raise order", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
