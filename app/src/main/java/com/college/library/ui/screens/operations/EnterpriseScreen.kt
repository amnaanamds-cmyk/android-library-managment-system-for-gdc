package com.college.library.ui.screens.operations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.college.library.data.model.LibraryEvent
import com.college.library.data.model.LostFoundItem
import com.college.library.data.repository.int
import com.college.library.data.repository.long
import com.college.library.data.repository.str
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.CardOrange
import com.college.library.ui.theme.Gold

/** Seats laid out in the reading room. */
private const val SEAT_COUNT = 20

/**
 * Enterprise features — reading room, lost & found, and library events.
 *
 * The desktop and web Enterprise panels hold all of this in memory only, so an
 * occupied seat or a found item disappears on refresh and is invisible to every
 * other device. These three tabs are backed by real collections
 * (reading_room, lost_found, library_events) so the state is shared, which is
 * the point of having the same feature on three platforms.
 */
@Composable
fun EnterpriseScreen(onNavigateBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Reading Room", "Lost & Found", "Events")

    OperationsScaffold(
        title = "Enterprise",
        subtitle = "Reading room, lost property and events",
        onNavigateBack = onNavigateBack,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label, fontSize = 12.sp) },
                    )
                }
            }
            when (tab) {
                0 -> ReadingRoomTab()
                1 -> LostFoundTab()
                else -> EventsTab()
            }
        }
    }
}

// ─── Reading room ────────────────────────────────────────────────────────────

@Composable
private fun ReadingRoomTab(viewModel: ReadingRoomViewModel = hiltViewModel()) {
    val records by viewModel.records.collectAsState()
    var assigning by remember { mutableStateOf<Int?>(null) }

    if (!viewModel.hasInstitution) {
        NotSignedInNotice()
        return
    }

    // Seat number is the identity; a seat with no record is free.
    val bySeat = records.associateBy { it.int("seatNumber") }
    val occupied = (1..SEAT_COUNT).count { bySeat[it]?.str("occupantName")?.isNotBlank() == true }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile("Occupied", "$occupied / $SEAT_COUNT", CardOrange, Modifier.weight(1f))
            StatTile("Free", "${SEAT_COUNT - occupied}", CardGreen, Modifier.weight(1f))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((1..SEAT_COUNT).toList()) { seatNumber ->
                val record = bySeat[seatNumber]
                val occupant = record?.str("occupantName") ?: ""
                SeatTile(
                    seatNumber = seatNumber,
                    occupant = occupant,
                    onClick = {
                        if (occupant.isBlank()) {
                            assigning = seatNumber
                        } else {
                            // Freeing a seat clears the occupant rather than
                            // deleting the record, so its history survives.
                            val syncId = record?.str("syncId").orEmpty()
                            if (syncId.isNotEmpty()) {
                                viewModel.patch(
                                    syncId,
                                    mapOf(
                                        "occupantName" to "",
                                        "occupantMemberId" to "",
                                        "occupiedAt" to null,
                                    ),
                                    "Seat $seatNumber freed.",
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    assigning?.let { seatNumber ->
        var name by remember { mutableStateOf("") }
        var memberId by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { assigning = null },
            title = { Text("Assign seat $seatNumber", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    DialogField("Occupant name", name, { name = it })
                    DialogField("Member ID (optional)", memberId, { memberId = it })
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val existing = bySeat[seatNumber]?.str("syncId").orEmpty()
                        val fields = mapOf(
                            "seatNumber" to seatNumber,
                            "occupantName" to name.trim(),
                            "occupantMemberId" to memberId.trim(),
                            "occupiedAt" to System.currentTimeMillis(),
                        )
                        if (existing.isNotEmpty()) {
                            viewModel.patch(existing, fields, "Seat $seatNumber assigned.")
                        } else {
                            viewModel.add(fields, "Seat $seatNumber assigned.")
                        }
                        assigning = null
                    },
                ) { Text("Assign", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { assigning = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SeatTile(seatNumber: Int, occupant: String, onClick: () -> Unit) {
    val free = occupant.isBlank()
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (free) {
                CardGreen.copy(alpha = 0.15f)
            } else {
                CardOrange.copy(alpha = 0.2f)
            },
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "$seatNumber",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = if (free) CardGreen else CardOrange,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (free) "Free" else occupant.take(10),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

// ─── Lost & found ────────────────────────────────────────────────────────────

@Composable
private fun LostFoundTab(viewModel: LostFoundViewModel = hiltViewModel()) {
    val records by viewModel.records.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    if (!viewModel.hasInstitution) {
        NotSignedInNotice()
        return
    }

    val items = records.sortedByDescending { it.long("reportedAt") }

    Box(Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            EmptyState("🔍", "Nothing logged", "Report an item found in the library.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.str("syncId") }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(Modifier.padding(14.dp).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.str("itemName", "Item"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                    )
                                    val where = item.str("location")
                                    if (where.isNotBlank()) {
                                        Text(
                                            "Found at $where",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                .copy(alpha = 0.75f),
                                        )
                                    }
                                }
                                StatusMenu(
                                    current = item.str("status", "Found"),
                                    options = LostFoundItem.STATUSES,
                                    onSelect = { newStatus ->
                                        viewModel.patch(
                                            item.str("syncId"),
                                            mapOf("status" to newStatus),
                                            "Marked $newStatus.",
                                        )
                                    },
                                )
                            }
                            val desc = item.str("description")
                            if (desc.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(desc, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                formatDate(item.long("reportedAt")),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            containerColor = Gold,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Text("Report item", fontWeight = FontWeight.Bold) }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var where by remember { mutableStateOf("") }
        var status by remember { mutableStateOf(LostFoundItem.STATUS_FOUND) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Report an item", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    DialogField("Item", name, { name = it })
                    DialogField("Description (optional)", desc, { desc = it })
                    DialogField("Location", where, { where = it })
                    DialogDropdown("Status", LostFoundItem.STATUSES, status) { status = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.add(
                            mapOf(
                                "itemName" to name.trim(),
                                "description" to desc.trim(),
                                "location" to where.trim(),
                                "status" to status,
                                "reportedAt" to System.currentTimeMillis(),
                            ),
                            "Item logged.",
                        )
                        showAdd = false
                    },
                ) { Text("Log item", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

// ─── Events ──────────────────────────────────────────────────────────────────

@Composable
private fun EventsTab(viewModel: LibraryEventsViewModel = hiltViewModel()) {
    val records by viewModel.records.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    if (!viewModel.hasInstitution) {
        NotSignedInNotice()
        return
    }

    val events = records.sortedByDescending { it.str("eventDate") }

    Box(Modifier.fillMaxSize()) {
        if (events.isEmpty()) {
            EmptyState("🎉", "No events scheduled", "Add a book fair, orientation or reading week.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(events, key = { it.str("syncId") }) { event ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(Modifier.padding(14.dp).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        event.str("title", "Untitled event"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                    )
                                    Text(
                                        buildString {
                                            append(event.str("eventDate", "Date TBC"))
                                            val venue = event.str("venue")
                                            if (venue.isNotBlank()) append(" · $venue")
                                        },
                                        fontSize = 12.sp,
                                        color = Gold,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                StatusMenu(
                                    current = event.str("status", "Planned"),
                                    options = LibraryEvent.STATUSES,
                                    onSelect = { newStatus ->
                                        viewModel.patch(
                                            event.str("syncId"),
                                            mapOf("status" to newStatus),
                                            "Marked $newStatus.",
                                        )
                                    },
                                )
                            }
                            val desc = event.str("description")
                            if (desc.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(desc, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            containerColor = Gold,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Text("Add event", fontWeight = FontWeight.Bold) }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(todayStamp()) }
        var venue by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add an event", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    DialogField("Title", title, { title = it })
                    DialogField("Date (YYYY-MM-DD)", date, { date = it })
                    DialogField("Venue (optional)", venue, { venue = it })
                    DialogField("Description (optional)", desc, { desc = it })
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        viewModel.add(
                            mapOf(
                                "title" to title.trim(),
                                "eventDate" to date.trim(),
                                "venue" to venue.trim(),
                                "description" to desc.trim(),
                                "status" to "Planned",
                                "attendees" to 0,
                            ),
                            "Event added.",
                        )
                        showAdd = false
                    },
                ) { Text("Add", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}
