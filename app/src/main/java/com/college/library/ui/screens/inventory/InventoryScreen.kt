package com.college.library.ui.screens.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.model.Book
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.college.library.ui.theme.*

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val bookDao: BookDao
) : ViewModel() {
    private val _allBooks = MutableStateFlow<List<Book>>(emptyList())
    
    private val _foundIds = MutableStateFlow<Set<Long>>(emptySet())
    val foundIds = _foundIds.asStateFlow()

    private val _displayList = MutableStateFlow<List<Book>>(emptyList())
    val displayList = _displayList.asStateFlow()

    init {
        loadBooks()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            bookDao.getAllBooks().collect { books ->
                _allBooks.value = books
                updateDisplay()
            }
        }
    }

    fun markFound(barcode: String) {
        val book = _allBooks.value.find { it.accNo == barcode || it.isbn == barcode }
        if (book != null) {
            _foundIds.value = _foundIds.value + book.id
            updateDisplay()
        }
    }

    private fun updateDisplay() {
        // Sort so missing books are at the top
        _displayList.value = _allBooks.value.sortedBy { it.id in _foundIds.value }
    }

    fun getStats(): Pair<Int, Int> = Pair(_foundIds.value.size, _allBooks.value.size)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val books by viewModel.displayList.collectAsState()
    val foundIds by viewModel.foundIds.collectAsState()
    val context = LocalContext.current

    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
            .build()
    }
    val scanner = remember { GmsBarcodeScanning.getClient(context, scannerOptions) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📦 Stocktaking & Inventory", color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scanner.startScan().addOnSuccessListener { barcode ->
                        barcode.rawValue?.let { viewModel.markFound(it) }
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Text("SCAN", modifier = Modifier.padding(16.dp), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val (found, total) = viewModel.getStats()
            val progress = if (total > 0) found.toFloat() / total else 0f
            
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Inventory Progress", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Positive
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$found / $total Books Found", fontSize = 14.sp)
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(books, key = { it.id }) { book ->
                    val isFound = book.id in foundIds
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFound) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                        )
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isFound) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isFound) Positive else Danger,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(book.title, fontWeight = FontWeight.Bold)
                                Text("Acc No: ${book.accNo}", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
