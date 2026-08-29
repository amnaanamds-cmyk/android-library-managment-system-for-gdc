package com.college.library.ui.screens.reservations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.college.library.data.DatabaseHelper
import com.college.library.data.db.Reservations
import com.college.library.data.db.adapters.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

import com.college.library.data.SyncManager

@Composable
fun ReservationsScreen() {
    val coroutineScope = rememberCoroutineScope()
    val database = remember { DatabaseHelper.getDatabase() }
    val syncService = remember { SyncManager.getSyncService(database) }
    var bookId by remember { mutableStateOf("") }
    var memberId by remember { mutableStateOf("") }

    val pendingReservations by database.reservationQueriesQueries.getAllPending()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .collectAsState(initial = emptyList<Reservations>())

    val reservations = pendingReservations.map { it.toModel() }.filter { !it.deleted }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // Left Column: Add Reservation
        Column(modifier = Modifier.weight(1f)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = MaterialTheme.colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("New Reservation", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = bookId, onValueChange = { bookId = it }, label = { Text("Book ID / ISBN") }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = memberId, onValueChange = { memberId = it }, label = { Text("Member ID") }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                                database.reservationQueriesQueries.insert(
                                    syncId = UUID.randomUUID().toString(),
                                    bookId = 1L,
                                    bookTitle = "Reserved Book",
                                    memberId = 1L,
                                    memberName = "Reserved Member",
                                    reservedDate = today,
                                    status = "Pending",
                                    notifiedDate = "",
                                    lastUpdated = System.currentTimeMillis(),
                                    deleted = false
                                )
                                bookId = ""
                                memberId = ""
                                // Trigger sync push
                                try {
                                    syncService.pushChanges()
                                } catch (e: Exception) {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = MaterialTheme.colors.onPrimary)
                    ) {
                        Text("Add Reservation", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Right Column: List
        Card(
            modifier = Modifier.weight(2f).fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = MaterialTheme.colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pending Reservations", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f)).padding(12.dp)) {
                    Text("Book", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    Text("Member", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    Text("Date", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    Text("Actions", modifier = Modifier.width(100.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                }
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))

                if (reservations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No pending reservations.", color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
                    }
                } else {
                    reservations.forEach { res ->
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(res.bookTitle, modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onSurface)
                            Text(res.memberName, modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                            Text(res.reservedDate, modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                            
                            Row(modifier = Modifier.width(100.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                IconButton(onClick = { 
                                    coroutineScope.launch(Dispatchers.IO) {
                                        database.reservationQueriesQueries.updateStatus("Notified", res.id)
                                    }
                                }) {
                                    Icon(Icons.Default.Notifications, contentDescription = "Notify", tint = MaterialTheme.colors.primary)
                                }
                                IconButton(onClick = { 
                                    coroutineScope.launch(Dispatchers.IO) {
                                        database.reservationQueriesQueries.softDelete(System.currentTimeMillis(), res.id)
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Cancel", tint = MaterialTheme.colors.error)
                                }
                            }
                        }
                        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                    }
                }
            }
        }
    }
}
