package com.college.library.ui.screens.operations

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.marc.Marc21
import com.college.library.data.model.Book
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.Gold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MarcCatalogViewModel @Inject constructor(
    private val bookDao: BookDao,
) : ViewModel() {

    val books = bookDao.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    /** Persist edited MARC text back onto the book record. */
    fun saveMarc(book: Book, marcText: String) {
        viewModelScope.launch {
            try {
                bookDao.updateBook(Marc21.applyEdits(book, marcText))
                _message.value = "MARC record saved for \"${book.title}\"."
            } catch (e: Exception) {
                _message.value = e.message ?: "Could not save the MARC record."
            }
        }
    }
}

/**
 * Browsable MARC 21 catalogue.
 *
 * Android previously showed MARC only as a read-only dialog inside a single
 * book's detail screen, while the desktop and web apps both have a dedicated
 * catalogue view. This lists every title with its cataloguing state and lets a
 * librarian view, edit and share the record.
 *
 * Records are read from the local catalogue, so this works offline and reflects
 * whatever the sync engine has pulled down.
 */
@Composable
fun MarcCatalogScreen(
    onNavigateBack: () -> Unit,
    viewModel: MarcCatalogViewModel = hiltViewModel(),
) {
    val books by viewModel.books.collectAsState()
    val message by viewModel.message.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Book?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val filtered = remember(books, query) {
        val q = query.trim().lowercase()
        val live = books.filter { !it.deleted }
        if (q.isEmpty()) {
            live
        } else {
            live.filter {
                it.title.lowercase().contains(q) ||
                    it.author.lowercase().contains(q) ||
                    it.isbn.contains(q) ||
                    it.accNo.lowercase().contains(q)
            }
        }.sortedBy { it.title.lowercase() }
    }

    val catalogued = books.count { Marc21.hasStoredRecord(it) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { outer ->
        Box(Modifier.padding(outer)) {
            OperationsScaffold(
                title = "MARC Catalogue",
                subtitle = "MARC 21 bibliographic records",
                onNavigateBack = onNavigateBack,
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatTile("Titles", books.size.toString(), Gold, Modifier.weight(1f))
                        StatTile("Catalogued", catalogued.toString(), CardGreen, Modifier.weight(1f))
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search title, author, ISBN or accession no.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )

                    if (filtered.isEmpty()) {
                        EmptyState(
                            "📑",
                            if (books.isEmpty()) "No books catalogued" else "Nothing matches that search",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filtered, key = { it.syncId.ifEmpty { it.id.toString() } }) { book ->
                                MarcRow(book) { selected = book }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { book ->
        MarcRecordDialog(
            book = book,
            onDismiss = { selected = null },
            onSave = { text ->
                viewModel.saveMarc(book, text)
                selected = null
            },
        )
    }
}

@Composable
private fun MarcRow(book: Book, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(book.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                Spacer(Modifier.height(2.dp))
                Text(
                    book.author.ifBlank { "Unknown author" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append("245 \$a ")
                        append(book.title.take(40))
                        if (book.isbn.isNotBlank()) append("  ·  020 \$a ${book.isbn}")
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
            StatusChip(if (Marc21.hasStoredRecord(book)) "Catalogued" else "Generated")
        }
    }
}

@Composable
private fun MarcRecordDialog(
    book: Book,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    var text by remember(book) { mutableStateOf(Marc21.render(book)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("MARC 21 record", fontWeight = FontWeight.Bold)
                Text(
                    book.title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 380.dp),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "MARC record — ${book.title}")
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share MARC record"))
                }) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share record", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
