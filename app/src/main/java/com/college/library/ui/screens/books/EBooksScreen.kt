package com.college.library.ui.screens.books

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.model.Book
import com.college.library.ui.theme.Gold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EBooksViewModel @Inject constructor(
    private val bookDao: BookDao
) : ViewModel() {

    private val _allBooks = MutableStateFlow<List<Book>>(emptyList())
    val allBooks = _allBooks.asStateFlow()

    var searchQuery by androidx.compose.runtime.mutableStateOf("")
    var selectedCategory by androidx.compose.runtime.mutableStateOf("All")

    init {
        viewModelScope.launch {
            bookDao.getAllBooks().collect { books ->
                _allBooks.value = books
            }
        }
    }

    fun addEBookLink(bookId: Long, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val book = bookDao.getBookById(bookId) ?: return@launch
            bookDao.updateBook(book.copy(digitalUrl = url, isDigital = true))
        }
    }

    fun importLocalEBook(bookTitle: String, author: String, localUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newBook = Book(
                isbn = "LOCAL-${System.currentTimeMillis()}",
                accNo = "L${System.currentTimeMillis() % 100000}",
                title = bookTitle,
                author = author,
                publisher = "Local Storage",
                publisherPlace = "",
                publishDate = "",
                edition = "",
                pages = 0,
                procurement = "Local",
                volume = "1",
                price = 0.0,
                status = "Available",
                isDigital = true,
                digitalUrl = localUri
            )
            bookDao.insertBook(newBook)
        }
    }
}

// Color scheme for categories
private val categoryColors = listOf(
    Color(0xFF6C63FF), Color(0xFFFF6584), Color(0xFF43C6AC),
    Color(0xFFFF9A3C), Color(0xFF4ECDC4), Color(0xFF45B7D1),
    Color(0xFF96CEB4), Color(0xFFFECEA8)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EBooksScreen(
    viewModel: EBooksViewModel = hiltViewModel()
) {
    val allBooks by viewModel.allBooks.collectAsState()
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<Book?>(null) }
    var importTitle by remember { mutableStateOf("") }
    var importAuthor by remember { mutableStateOf("") }
    var importedUri by remember { mutableStateOf("") }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            importedUri = uri.toString()
            showImportDialog = true
        }
    }

    val eBooks = allBooks.filter { it.isDigital || it.digitalUrl != null }

    val categories = listOf("All") + eBooks.map { it.publisher }.filter { it.isNotBlank() }.distinct().take(6)

    val filteredBooks = eBooks.filter { book ->
        (viewModel.selectedCategory == "All" || book.publisher == viewModel.selectedCategory) &&
        (viewModel.searchQuery.isBlank() ||
            book.title.contains(viewModel.searchQuery, ignoreCase = true) ||
            book.author.contains(viewModel.searchQuery, ignoreCase = true) ||
            book.publisher.contains(viewModel.searchQuery, ignoreCase = true))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Digital Library", color = Gold, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("E-Books & Digital Resources", color = Gold.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { pdfPickerLauncher.launch("application/pdf") }) {
                        Icon(Icons.Default.Add, contentDescription = "Import PDF", tint = Gold)
                    }
                    IconButton(onClick = {
                        pdfPickerLauncher.launch("*/*")
                    }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Browse Files", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pdfPickerLauncher.launch("application/pdf") },
                containerColor = Gold,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import E-Book")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Hero Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(MaterialTheme.colorScheme.primary, Color(0xFF1A3A6B))
                        )
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Gold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = Gold, modifier = Modifier.size(34.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("${eBooks.size} Digital Books", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Available in your library", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Gold.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "  Tap + to import from device  ",
                                color = Gold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                placeholder = { Text("Search books, authors...") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Category Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = viewModel.selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectedCategory = category },
                        label = { Text(category, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Gold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredBooks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LibraryBooks, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (viewModel.searchQuery.isBlank()) "No E-Books yet" else "No results for \"${viewModel.searchQuery}\"",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap the + button to import a PDF\nor add a URL to an existing book",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { pdfPickerLauncher.launch("application/pdf") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import from Storage")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredBooks, key = { it.id }) { book ->
                        EBookCard(
                            book = book,
                            colorIndex = filteredBooks.indexOf(book) % categoryColors.size,
                            onReadClick = {
                                val url = book.digitalUrl ?: "https://www.google.com/search?q=${Uri.encode(book.title)}+pdf"
                                val intent = android.content.Intent(context, com.college.library.ui.screens.books.EBookReaderActivity::class.java).apply {
                                    putExtra("ebook_url", url)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open this file. Please install a PDF viewer.", Toast.LENGTH_LONG).show()
                                }
                            },
                            onShareClick = {
                                val shareMsg = "📚 ${book.title}\n✍️ ${book.author}\n${book.digitalUrl ?: ""}"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareMsg)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share E-Book via..."))
                            },
                            onAddLinkClick = {
                                selectedBook = book
                                showLinkDialog = true
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Import Local PDF Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import E-Book", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter book details for the imported file:", fontSize = 14.sp)
                    OutlinedTextField(
                        value = importTitle,
                        onValueChange = { importTitle = it },
                        label = { Text("Book Title *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = importAuthor,
                        onValueChange = { importAuthor = it },
                        label = { Text("Author Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "File: ${importedUri.substringAfterLast("/").take(40)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importTitle.isNotBlank()) {
                            viewModel.importLocalEBook(importTitle, importAuthor, importedUri)
                            importTitle = ""
                            importAuthor = ""
                            importedUri = ""
                            showImportDialog = false
                            Toast.makeText(context, "E-Book imported successfully!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Import", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add/Edit Link Dialog
    if (showLinkDialog && selectedBook != null) {
        var linkInput by remember { mutableStateOf(selectedBook?.digitalUrl ?: "") }
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Set E-Book URL", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = {
                Column {
                    Text("Book: ${selectedBook?.title}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = linkInput,
                        onValueChange = { linkInput = it },
                        label = { Text("Google Drive / URL") },
                        placeholder = { Text("https://drive.google.com/...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedBook?.let { viewModel.addEBookLink(it.id, linkInput) }
                        showLinkDialog = false
                        Toast.makeText(context, "E-Book link saved!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Save", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun EBookCard(
    book: Book,
    colorIndex: Int = 0,
    onReadClick: () -> Unit,
    onShareClick: () -> Unit,
    onAddLinkClick: () -> Unit
) {
    val accentColor = categoryColors[colorIndex]
    val isLocalFile = book.digitalUrl?.startsWith("content://") == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Colored spine strip
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(160.dp)
                    .background(accentColor)
            )

            // Book cover thumbnail area
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(160.dp)
                    .background(
                        Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.2f), accentColor.copy(alpha = 0.05f)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isLocalFile) Icons.Default.PictureAsPdf else Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accentColor
                    ) {
                        Text(
                            if (isLocalFile) " PDF " else " URL ",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Book info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Text(
                    book.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    book.author.ifBlank { "Unknown Author" },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (book.publisher.isNotBlank() && book.publisher != "Local Storage") {
                    Text(
                        "📕 ${book.publisher}",
                        fontSize = 12.sp,
                        color = accentColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (book.publishDate.isNotBlank()) {
                    Text("📅 ${book.publishDate}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onReadClick,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Read", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onShareClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = onAddLinkClick,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Set URL", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
