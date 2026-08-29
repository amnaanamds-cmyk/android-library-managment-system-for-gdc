package com.college.library.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.datetime.toLocalDateTime
import com.college.library.data.db.Issued_books
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Cursor

@Composable
fun DashboardScreen(
    onNavigateToCatalog: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onNavigateToCirculation: () -> Unit
) {
    val database = remember { com.college.library.data.DatabaseHelper.getDatabase() }
    
    // State for sortable table
    var sortAscending by remember { mutableStateOf(true) }
    
    // Fetch Data
    val totalBooksFlow = remember { database.bookQueriesQueries.getTotalCount().asFlow().mapToOne(kotlinx.coroutines.Dispatchers.IO) }
    val totalBooks by totalBooksFlow.collectAsState(initial = 0L)
    
    val totalMembersFlow = remember { database.memberQueriesQueries.getTotalCount().asFlow().mapToOne(kotlinx.coroutines.Dispatchers.IO) }
    val totalMembers by totalMembersFlow.collectAsState(initial = 0L)
    
    val issuedFlow = remember { database.issuedBookQueriesQueries.getIssuedCount().asFlow().mapToOne(kotlinx.coroutines.Dispatchers.IO) }
    val totalIssued by issuedFlow.collectAsState(initial = 0L)
    
    val overdueFlow = remember { 
        val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()
        database.issuedBookQueriesQueries.getOverdueBooks(today).asFlow().mapToList(kotlinx.coroutines.Dispatchers.IO)
    }
    val overdueBooksList by overdueFlow.collectAsState(initial = emptyList<Issued_books>())
    
    // Premium Color Palette
    val borderLight = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
    
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        // Summary Cards Row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InteractiveSummaryCard(title = "Total Books", value = totalBooks.toString(), color = MaterialTheme.colors.primary, modifier = Modifier.weight(1f)) { onNavigateToCatalog() }
            InteractiveSummaryCard(title = "Total Members", value = totalMembers.toString(), color = Color(0xFF059669), modifier = Modifier.weight(1f)) { onNavigateToMembers() }
            InteractiveSummaryCard(title = "Issued Currently", value = totalIssued.toString(), color = Color(0xFFD97706), modifier = Modifier.weight(1f)) { onNavigateToCirculation() }
            InteractiveSummaryCard(title = "Overdue Books", value = overdueBooksList.size.toString(), color = MaterialTheme.colors.error, modifier = Modifier.weight(1f)) { onNavigateToCirculation() }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Transaction Table (Premium UI)
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp),
            elevation = 0.dp,
            backgroundColor = MaterialTheme.colors.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderLight)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Table Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Due Today & Overdue", 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = MaterialTheme.colors.onSurface
                    )
                    // Fake sort toggle for demonstration
                    TextButton(onClick = { sortAscending = !sortAscending }) {
                        Text(if (sortAscending) "Sort: Oldest First ↓" else "Sort: Newest First ↑", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    }
                }
                
                Divider(color = borderLight)
                
                // Table Header
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f)).padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("Book Title", modifier = Modifier.weight(2f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    Text("Member", modifier = Modifier.weight(1.5f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    Text("Due Date", modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    Text("Status", modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                }
                
                Divider(color = borderLight)
                
                // Empty State Demonstration
                if (overdueBooksList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "All Good", modifier = Modifier.size(48.dp), tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No overdue books right now!", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onSurface)
                            Text("All circulation is up to date.", fontSize = 14.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        }
                    }
                } else {
                    val sortedList = if (sortAscending) overdueBooksList.sortedBy { it.dueDate } else overdueBooksList.sortedByDescending { it.dueDate }
                    sortedList.forEach { overdue ->
                        InteractiveTableRow(
                            title = overdue.bookTitle,
                            member = overdue.memberName,
                            date = overdue.dueDate,
                            status = "Overdue",
                            statusColor = MaterialTheme.colors.error,
                            onClick = { onNavigateToCirculation() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveSummaryCard(title: String, value: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val elevation by animateDpAsState(if (isHovered) 8.dp else 0.dp)
    
    Card(
        modifier = modifier
            .height(120.dp)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .shadow(elevation, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = MaterialTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = color, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun InteractiveTableRow(title: String, member: String, date: String, status: String, statusColor: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (isHovered) MaterialTheme.colors.onSurface.copy(alpha = 0.05f) else MaterialTheme.colors.surface)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(2f), fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onSurface)
        Text(member, modifier = Modifier.weight(1.5f), color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        Text(date, modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        
        // Status Badge
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = status, 
                color = statusColor, 
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
}
