package com.college.library.ui.screens.classification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.model.Book
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

val DDC_MAP = mapOf(
    "computer" to "004", "software" to "005", "data" to "006",
    "philosophy" to "100", "psychology" to "150", "logic" to "160",
    "religion" to "200", "islam" to "297", "christianity" to "230",
    "social" to "300", "economics" to "330", "law" to "340", "education" to "370",
    "language" to "400", "english" to "420", "urdu" to "491",
    "science" to "500", "math" to "510", "physics" to "530", "chemistry" to "540",
    "biology" to "570", "botany" to "580", "zoology" to "590",
    "technology" to "600", "medicine" to "610", "engineering" to "620",
    "agriculture" to "630", "management" to "658",
    "art" to "700", "music" to "780", "sports" to "796",
    "literature" to "800", "fiction" to "823", "poetry" to "811",
    "history" to "900", "geography" to "910", "pakistan" to "954.91"
)

fun getSuggestedDdc(title: String, category: String): String {
    val searchStr = "$title $category".lowercase()
    return DDC_MAP.entries.firstOrNull { searchStr.contains(it.key) }?.value ?: "020" // default Library Science
}

fun buildAuthorCutter(author: String): String {
    val parts = author.trim().split(" ")
    if (parts.isEmpty()) return ""
    val last = parts.last().take(3).uppercase()
    val first = if (parts.size > 1) parts.first().take(1).uppercase() else ""
    return last + first
}

// ── ViewModel ───────────────────────────────────────────────────────────────
@HiltViewModel
class ClassificationViewModel @Inject constructor(
    private val bookDao: BookDao
) : ViewModel() {

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    private val _filterUnclassified = MutableStateFlow(true)
    val filterUnclassified: StateFlow<Boolean> = _filterUnclassified.asStateFlow()

    val allBooks: StateFlow<List<Book>> = bookDao.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val displayList: StateFlow<List<Book>> = combine(allBooks, _search, _filterUnclassified) { books, q, unclassifiedOnly ->
        var list = books
        if (unclassifiedOnly) {
            list = list.filter { it.callNumber.isBlank() }
        }
        if (q.isNotBlank()) {
            list = list.filter { b ->
                b.title.contains(q, true) || b.author.contains(q, true) || b.category.contains(q, true)
            }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearch(q: String) { _search.value = q }
    fun setFilterUnclassified(onlyUnclassified: Boolean) { _filterUnclassified.value = onlyUnclassified }

    fun autoClassifyAll() {
        viewModelScope.launch {
            val unclassified = allBooks.value.filter { it.callNumber.isBlank() }
            unclassified.forEach { book ->
                val suggestedCall = getSuggestedDdc(book.title, book.category)
                val suggestedCutter = buildAuthorCutter(book.author)
                bookDao.updateBook(
                    book.copy(
                        callNumber = suggestedCall,
                        authorCutter = suggestedCutter,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun saveClassification(book: Book, ddc: String, cutter: String) {
        viewModelScope.launch {
            bookDao.updateBook(
                book.copy(
                    callNumber = ddc,
                    authorCutter = cutter,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }
}

// ── Edit Dialog ──────────────────────────────────────────────────────────────
@Composable
fun EditClassificationDialog(
    book: Book,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val suggested = remember { getSuggestedDdc(book.title, book.category) }
    var callNo by remember { mutableStateOf(book.callNumber.ifBlank { suggested }) }
    var cutter by remember { mutableStateOf(book.authorCutter.ifBlank { buildAuthorCutter(book.author) }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Classification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(book.title, fontWeight = FontWeight.Bold)
                
                OutlinedTextField(
                    value = callNo,
                    onValueChange = { callNo = it },
                    label = { Text("DDC Call Number") },
                    supportingText = { Text("Suggested: $suggested") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = cutter,
                    onValueChange = { cutter = it },
                    label = { Text("Author Cutter") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(callNo.trim(), cutter.trim()) },
                enabled = callNo.isNotBlank() && cutter.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Main Screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassificationScreen(
    onNavigateBack: () -> Unit,
    viewModel: ClassificationViewModel = hiltViewModel()
) {
    val displayList by viewModel.displayList.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()
    val search by viewModel.search.collectAsState()
    val filterUnclassified by viewModel.filterUnclassified.collectAsState()

    var editTarget by remember { mutableStateOf<Book?>(null) }
    var autoClassifyDialog by remember { mutableStateOf(false) }

    val unclassifiedCount = allBooks.count { it.callNumber.isBlank() }
    val classifiedCount = allBooks.size - unclassifiedCount

    editTarget?.let { book ->
        EditClassificationDialog(
            book = book,
            onSave = { call, cutter -> viewModel.saveClassification(book, call, cutter); editTarget = null },
            onDismiss = { editTarget = null }
        )
    }

    if (autoClassifyDialog) {
        AlertDialog(
            onDismissRequest = { autoClassifyDialog = false },
            title = { Text("Auto-Classify") },
            text = { Text("Automatically assign DDC classes and author cutters to $unclassifiedCount unclassified books based on title and category analysis?") },
            confirmButton = {
                Button(onClick = { viewModel.autoClassifyAll(); autoClassifyDialog = false }) {
                    Text("Classify All")
                }
            },
            dismissButton = { TextButton(onClick = { autoClassifyDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DDC / LC Classification") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            
            // Header Stats & Auto-Classify
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Class, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Dewey Decimal Classification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("$classifiedCount classified · $unclassifiedCount pending", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (unclassifiedCount > 0) {
                        Button(
                            onClick = { autoClassifyDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Auto-Classify All Missing ($unclassifiedCount)")
                        }
                    }
                }
            }

            // Search & Filter
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = viewModel::setSearch,
                    placeholder = { Text("Search title, category") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                
                FilterChip(
                    selected = filterUnclassified,
                    onClick = { viewModel.setFilterUnclassified(!filterUnclassified) },
                    label = { Text("Pending") }
                )
            }
            
            Spacer(Modifier.height(12.dp))

            if (displayList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No books found matching criteria.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(displayList, key = { it.syncId }) { book ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            onClick = { editTarget = book }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${book.author} · ${book.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.width(8.dp))
                                if (book.callNumber.isNotBlank()) {
                                    Box(
                                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text("${book.callNumber} ${book.authorCutter}", color = MaterialTheme.colorScheme.onPrimaryContainer, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else {
                                    Text("⏳ Missing", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
