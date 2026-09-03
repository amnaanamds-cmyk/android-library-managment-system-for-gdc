package com.college.library.ui.screens.spinelabel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.model.Book
import com.college.library.utils.DDC_MAP
import com.college.library.utils.getSuggestedDdc
import com.college.library.utils.buildAuthorCutter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject



// ── ViewModel ───────────────────────────────────────────────────────────────
@HiltViewModel
class SpineLabelViewModel @Inject constructor(
    private val bookDao: BookDao
) : ViewModel() {

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    val allBooks: StateFlow<List<Book>> = bookDao.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filtered: StateFlow<List<Book>> = combine(allBooks, _search) { books, q ->
        if (q.isBlank()) books
        else books.filter { b ->
            b.title.contains(q, true) || b.author.contains(q, true) ||
            b.accNo.contains(q, true) || b.category.contains(q, true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearch(q: String) { _search.value = q }

    fun saveLabel(book: Book, callNumber: String, authorCutter: String) {
        viewModelScope.launch {
            bookDao.updateBook(book.copy(
                callNumber = callNumber,
                authorCutter = authorCutter,
                lastUpdated = System.currentTimeMillis()
            ))
        }
    }
}

// ── Spine Label Preview composable ──────────────────────────────────────────
@Composable
fun SpineLabelPreview(callNumber: String, authorCutter: String, year: String, title: String,
    modifier: Modifier = Modifier) {
    val blue = Color(0xFF1E40AF)
    val dark = Color(0xFF0F172A)
    Box(
        modifier = modifier
            .width(80.dp)
            .height(130.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Blue header
            Box(modifier = Modifier.fillMaxWidth().height(20.dp).background(blue),
                contentAlignment = Alignment.Center) {
                Text(title.take(12), color = Color.White, fontSize = 7.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
            }
            // Call number
            Box(modifier = Modifier.fillMaxWidth().weight(1f).background(Color.White),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(callNumber.ifBlank { "—" }, fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = dark,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text(authorCutter.ifBlank { "" }, fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp, color = dark, textAlign = TextAlign.Center)
                    Text(year.take(4), fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                }
            }
            // Blue footer
            Box(modifier = Modifier.fillMaxWidth().height(12.dp).background(blue))
        }
    }
}

// ── Edit Label Dialog ────────────────────────────────────────────────────────
@Composable
fun EditLabelDialog(
    book: Book,
    onSave: (callNumber: String, authorCutter: String) -> Unit,
    onDismiss: () -> Unit
) {
    val suggested = remember { getSuggestedDdc(book.title, book.category) }
    var callNo by remember { mutableStateOf(book.callNumber.ifBlank { suggested }) }
    var cutter by remember { mutableStateOf(book.authorCutter.ifBlank { buildAuthorCutter(book.author) }) }
    val year = remember { book.publishDate.take(4).ifBlank { "2024" } }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {

                Text("🏷️ Spine Label Editor",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Text(book.title, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)

                OutlinedTextField(
                    value = callNo,
                    onValueChange = { callNo = it },
                    label = { Text("DDC Call Number") },
                    placeholder = { Text("e.g. 005.13") },
                    supportingText = { Text("Suggested: $suggested") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = cutter,
                    onValueChange = { cutter = it },
                    label = { Text("Author Cutter") },
                    placeholder = { Text("e.g. SMIA") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Live preview
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Preview:", style = MaterialTheme.typography.labelLarge)
                    SpineLabelPreview(callNo, cutter, year, book.title)
                }

                // DDC hint
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        "💡 DDC Classes: 000=CS · 100=Philosophy · 200=Religion · " +
                        "300=Social · 400=Language · 500=Science · 600=Tech · 700=Art · " +
                        "800=Literature · 900=History",
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = { onSave(callNo.trim(), cutter.trim()) },
                        modifier = Modifier.weight(1f),
                        enabled = callNo.isNotBlank() && cutter.isNotBlank()) {
                        Text("Save Label")
                    }
                }
            }
        }
    }
}

// ── Book Label Row ───────────────────────────────────────────────────────────
@Composable
fun BookLabelRow(book: Book, onEdit: () -> Unit) {
    val hasLabel = book.callNumber.isNotBlank()
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Row(modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hasLabel) {
                SpineLabelPreview(book.callNumber, book.authorCutter,
                    book.publishDate.take(4), book.title,
                    modifier = Modifier.size(width = 60.dp, height = 96.dp))
            } else {
                Box(modifier = Modifier.size(width = 60.dp, height = 96.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center) {
                    Text("No\nLabel", style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Acc: ${book.accNo}  ·  ${book.category}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hasLabel) {
                    Text("📌 ${book.callNumber} / ${book.authorCutter}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF10B981), fontWeight = FontWeight.Medium)
                } else {
                    Text("⏳ Label Missing",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B))
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit Label",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ── Main Screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpineLabelScreen(
    onNavigateBack: () -> Unit,
    viewModel: SpineLabelViewModel = hiltViewModel()
) {
    val filtered by viewModel.filtered.collectAsState()
    val allBooks by viewModel.allBooks.collectAsState()
    val search by viewModel.search.collectAsState()

    var editTarget by remember { mutableStateOf<Book?>(null) }
    var savedSnack by remember { mutableStateOf<String?>(null) }

    val done = allBooks.count { it.callNumber.isNotBlank() }
    val total = allBooks.size

    editTarget?.let { book ->
        EditLabelDialog(
            book = book,
            onSave = { callNo, cutter ->
                viewModel.saveLabel(book, callNo, cutter)
                savedSnack = "✅ Label saved for '${book.title.take(30)}'"
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    savedSnack?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            savedSnack = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spine Label Generator") },
                navigationIcon = { IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = {
            savedSnack?.let { msg ->
                Snackbar(modifier = Modifier.padding(8.dp)) { Text(msg) }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Stats
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple("Total Books", "$total", MaterialTheme.colorScheme.primary),
                    Triple("Labels Done", "$done", Color(0xFF10B981)),
                    Triple("Missing", "${total - done}", Color(0xFFF59E0B))
                ).forEach { (label, value, color) ->
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = color)
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = viewModel::setSearch,
                placeholder = { Text("Search books…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No books found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filtered, key = { it.syncId }) { book ->
                        BookLabelRow(book = book, onEdit = { editTarget = book })
                    }
                }
            }
        }
    }
}
