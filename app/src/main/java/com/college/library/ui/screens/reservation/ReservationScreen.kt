package com.college.library.ui.screens.reservation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.college.library.data.model.Reservation
import com.college.library.ui.theme.DangerRed
import com.college.library.ui.theme.Gold
import com.college.library.ui.theme.NavyBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReservationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDialog by remember { mutableStateOf<Pair<String, Reservation>?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Reservations", color = Color.White, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.pendingReservations.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.BookmarkAdded,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No Pending Reservations",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "When members reserve unavailable books,\nthey will appear here.",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Pending Reservations",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyBlue,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .background(Gold.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${state.pendingReservations.size}",
                                fontWeight = FontWeight.Bold,
                                color = NavyBlue,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                items(
                    items = state.pendingReservations,
                    key = { it.id }
                ) { reservation ->
                    ReservationCard(
                        reservation = reservation,
                        onFulfill = {
                            confirmDialog = "fulfill" to reservation
                        },
                        onCancel = {
                            confirmDialog = "cancel" to reservation
                        },
                        onNotify = {
                            viewModel.notifyReservation(reservation)
                        }
                    )
                }
            }
        }
    }

    // Confirmation dialog
    confirmDialog?.let { (action, reservation) ->
        val isFulfill = action == "fulfill"
        AlertDialog(
            onDismissRequest = { confirmDialog = null },
            title = {
                Text(if (isFulfill) "Fulfill Reservation" else "Cancel Reservation")
            },
            text = {
                Text(
                    if (isFulfill)
                        "Issue \"${reservation.bookTitle}\" to ${reservation.memberName}? This will mark the reservation as fulfilled."
                    else
                        "Cancel the reservation of \"${reservation.bookTitle}\" for ${reservation.memberName}?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isFulfill) viewModel.fulfillReservation(reservation)
                        else viewModel.cancelReservation(reservation)
                        confirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFulfill) NavyBlue else DangerRed
                    )
                ) {
                    Text(if (isFulfill) "Fulfill" else "Cancel It")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDialog = null }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
private fun ReservationCard(
    reservation: Reservation,
    onFulfill: () -> Unit,
    onCancel: () -> Unit,
    onNotify: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Book title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.BookmarkAdded,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    reservation.bookTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Member info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    reservation.memberName,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Reserved date
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.EventNote,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Reserved: ${reservation.reservedDate}",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                if (reservation.notifiedDate != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Notified: ${reservation.notifiedDate}",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onFulfill,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Fulfill", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onNotify,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = NavyBlue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Notify", fontSize = 13.sp, color = NavyBlue)
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = DangerRed
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel", fontSize = 13.sp, color = DangerRed)
                }
            }
        }
    }
}
