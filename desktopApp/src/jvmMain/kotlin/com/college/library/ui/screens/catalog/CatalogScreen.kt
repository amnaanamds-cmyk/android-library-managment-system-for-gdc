package com.college.library.ui.screens.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.college.library.data.db.Books
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.college.library.data.DatabaseHelper
import com.college.library.data.db.adapters.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

import com.college.library.data.SyncManager
import com.college.library.data.SyncDatabaseHelper

@Composable
fun CatalogScreen() {
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var isAddBookModalOpen by remember { mutableStateOf(false) }

    val database = remember { DatabaseHelper.getDatabase() }
    val syncService = remember { SyncManager.getSyncService(database) }
    
    // Read from local SQLDelight Database
    val books: List<Books> by database.bookQueriesQueries.getAllBooks()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .collectAsState(initial = emptyList())

    val filteredBooks = books.map { it.toModel() }
        .filter { !it.deleted } // Don't show soft-deleted
        .filter { 
            it.title.contains(searchQuery, ignoreCase = true) || 
            it.author.contains(searchQuery, ignoreCase = true) ||
            it.isbn.contains(searchQuery, ignoreCase = true) 
        }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Action Bar (Search and Add Book)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by title, author, or ISBN...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    modifier = Modifier.width(400.dp).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        backgroundColor = MaterialTheme.colors.surface,
                        unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                        textColor = MaterialTheme.colors.onSurface
                    )
                )

                Button(
                    onClick = { isAddBookModalOpen = true },
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = MaterialTheme.colors.onPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(52.dp).padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Book")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add New Book", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Data Table
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                elevation = 2.dp,
                backgroundColor = MaterialTheme.colors.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Text("Title", modifier = Modifier.weight(2f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        Text("Author", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        Text("ISBN", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        Text("Category", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        Text("Status", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        Text("Actions", modifier = Modifier.width(100.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }

                    // Data Rows
                    if (filteredBooks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No books found.", color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
                        }
                    } else {
                        filteredBooks.forEach { book ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(book.title, modifier = Modifier.weight(2f), fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onSurface)
                                Text(book.author, modifier = Modifier.weight(1.5f), color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                                Text(book.isbn, modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                                Text(book.category, modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                                Text(book.status, modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold, color = if (book.status == "Available") Color(0xFF10B981) else MaterialTheme.colors.error)
                                
                                // Action Buttons
                                Row(modifier = Modifier.width(100.dp), horizontalArrangement = Arrangement.Center) {
                                    IconButton(onClick = { /* TODO Edit */ }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colors.primary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { 
                                        coroutineScope.launch(Dispatchers.IO) {
                                            database.bookQueriesQueries.softDelete(System.currentTimeMillis(), book.id)
                                        }
                                    }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colors.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                        }
                    }
                }
            }
        }

        // Add Book Modal Dialog
        if (isAddBookModalOpen) {
            AddBookModal(
                onClose = { isAddBookModalOpen = false },
                onSave = { title, author, isbn, copies, category ->
                    coroutineScope.launch(Dispatchers.IO) {
                        repeat(copies) {
                            database.bookQueriesQueries.insertBook(
                                syncId = UUID.randomUUID().toString(),
                                isbn = isbn,
                                accNo = UUID.randomUUID().toString().take(8),
                                title = title,
                                author = author,
                                publisher = "Unknown",
                                publisherPlace = "Unknown",
                                publishDate = "2026",
                                edition = "1st",
                                pages = 300,
                                procurement = "Donation",
                                volume = "1",
                                price = 0.0,
                                status = "Available",
                                isDigital = false,
                                digitalUrl = "",
                                category = category,
                                marcData = "",
                                lastUpdated = System.currentTimeMillis(),
                                deleted = false
                            )
                        }
                        isAddBookModalOpen = false
                        // Trigger sync push immediately
                        try {
                            syncService.pushChanges()
                        } catch (e: Exception) {}
                    }
                }
            )
        }
    }
}

@Composable
fun AddBookModal(onClose: () -> Unit, onSave: (String, String, String, Int, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var isbn by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var copies by remember { mutableStateOf("1") }

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier.width(550.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = 16.dp,
            backgroundColor = MaterialTheme.colors.surface
        ) {
            Column(modifier = Modifier.padding(32.dp)) {
                Text("Add New Book", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colors.primary)
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = title, onValueChange = { title = it }, 
                    label = { Text("Book Title") }, 
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        backgroundColor = MaterialTheme.colors.surface,
                        textColor = MaterialTheme.colors.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = author, onValueChange = { author = it }, 
                    label = { Text("Author Name") }, 
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        backgroundColor = MaterialTheme.colors.surface,
                        textColor = MaterialTheme.colors.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = category, onValueChange = { category = it }, 
                    label = { Text("Category") }, 
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        backgroundColor = MaterialTheme.colors.surface,
                        textColor = MaterialTheme.colors.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = isbn, onValueChange = { isbn = it }, 
                        label = { Text("ISBN Number") }, 
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = MaterialTheme.colors.surface,
                            textColor = MaterialTheme.colors.onSurface
                        )
                    )
                    OutlinedTextField(
                        value = copies, onValueChange = { copies = it.filter { char -> char.isDigit() } }, 
                        label = { Text("Number of Copies") }, 
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            backgroundColor = MaterialTheme.colors.surface,
                            textColor = MaterialTheme.colors.onSurface
                        )
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                // Form Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose, modifier = Modifier.height(48.dp)) {
                        Text("Cancel", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { onSave(title, author, isbn, copies.toIntOrNull() ?: 1, category) },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = MaterialTheme.colors.onPrimary)
                    ) {
                        Text("Save Book", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
