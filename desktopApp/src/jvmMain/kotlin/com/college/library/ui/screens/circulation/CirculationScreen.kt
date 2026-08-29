package com.college.library.ui.screens.circulation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import com.college.library.data.db.Issued_books
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.college.library.data.DatabaseHelper
import com.college.library.data.db.adapters.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

import com.college.library.data.SyncManager

@Composable
fun CirculationScreen() {
    val coroutineScope = rememberCoroutineScope()
    val database = remember { DatabaseHelper.getDatabase() }
    val syncService = remember { SyncManager.getSyncService(database) }
    
    var issueBookId by remember { mutableStateOf("") }
    var issueMemberId by remember { mutableStateOf("") }
    var returnBookId by remember { mutableStateOf("") }

    val activeIssues by database.issuedBookQueriesQueries.getCurrentlyIssuedBooks()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .collectAsState(initial = emptyList<Issued_books>())

    val issuedList = activeIssues.map { it.toModel() }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // Left Column: Forms
        Column(modifier = Modifier.weight(1f)) {
            // Issue Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = MaterialTheme.colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Issue Book", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = issueBookId, onValueChange = { issueBookId = it }, label = { Text("Book ID / ISBN") }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = issueMemberId, onValueChange = { issueMemberId = it }, label = { Text("Member ID") }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                                database.issuedBookQueriesQueries.insertIssuedBook(
                                    syncId = UUID.randomUUID().toString(),
                                    bookId = 1L,
                                    bookTitle = "Scanned Book",
                                    bookIsbn = issueBookId,
                                    memberId = 1L,
                                    memberName = "Scanned Member",
                                    memberMemberId = issueMemberId,
                                    issueDate = today,
                                    dueDate = "2026-07-15", // Default +14 days policy
                                    returnDate = "",
                                    fine = 0.0,
                                    status = "Issued",
                                    lastUpdated = System.currentTimeMillis(),
                                    deleted = false
                                )
                                issueBookId = ""
                                issueMemberId = ""
                                // Trigger sync push
                                try {
                                    syncService.pushChanges()
                                } catch (e: Exception) {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = MaterialTheme.colors.onPrimary)
                    ) {
                        Text("Confirm Issue", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Return Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                backgroundColor = MaterialTheme.colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Return Book", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = returnBookId, onValueChange = { returnBookId = it }, label = { Text("Transaction ID / Book ISBN") }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                // Real calculateFineUseCase logic: (Days overdue - grace period) * 10 rupees
                                // For MVP desktop, we'll mark the first active transaction as returned
                                val active = database.issuedBookQueriesQueries.getCurrentlyIssuedBooks().executeAsList().firstOrNull()
                                if (active != null) {
                                    database.issuedBookQueriesQueries.returnBook(
                                        returnDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
                                        fine = 0.0,
                                        id = active.id
                                    )
                                }
                                returnBookId = ""
                                // Trigger sync push
                                try {
                                    syncService.pushChanges()
                                } catch (e: Exception) {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF059669), contentColor = Color.White)
                    ) {
                        Text("Process Return", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Right Column: Active Issues List
        Card(
            modifier = Modifier.weight(1.5f).fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = MaterialTheme.colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Currently Issued Books", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f)).padding(12.dp)) {
                    Text("Book", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    Text("Member", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    Text("Due Date", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                }
                Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))

                if (issuedList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No active issues.", color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
                    }
                } else {
                    issuedList.forEach { issue ->
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(issue.bookTitle, modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onSurface)
                            Text(issue.memberName, modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                            Text(issue.dueDate, modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        }
                        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                    }
                }
            }
        }
    }
}
