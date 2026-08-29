package com.college.library.ui.screens.unioncatalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.college.library.data.CollegeService
import com.college.library.data.model.Book
import com.college.library.data.model.College
import com.college.library.profile.CollegeProfileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── ViewModel ───────────────────────────────────────────────────────────────
class UnionCatalogViewModel : ViewModel() {
    private val collegeService = CollegeService()
    private val profileManager = CollegeProfileManager.getInstance()

    private val _colleges = MutableStateFlow<List<College>>(emptyList())
    val colleges: StateFlow<List<College>> = _colleges.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Pair<College, Book>>>(emptyList())
    val searchResults: StateFlow<List<Pair<College, Book>>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _illStatus = MutableStateFlow<String?>(null)
    val illStatus: StateFlow<String?> = _illStatus.asStateFlow()

    init {
        viewModelScope.launch {
            _colleges.value = collegeService.getAllColleges()
        }
    }

    fun searchAcrossInstitutions(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            val results = mutableListOf<Pair<College, Book>>()
            
            // Search all colleges
            _colleges.value.forEach { college ->
                val books = collegeService.searchBooksInCollege(college.collegeId, query)
                results.addAll(books.map { college to it })
            }
            
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun submitIllRequest(targetCollege: College, book: Book, requesterName: String) {
        viewModelScope.launch {
            val myCollegeName = profileManager.getProfile()?.collegeName ?: "Unknown Library"
            collegeService.submitIllRequest(
                targetCollegeId = targetCollege.collegeId,
                bookTitle = book.title,
                requesterName = requesterName,
                fromInstitution = myCollegeName
            )
            _illStatus.value = "✅ Inter-Library Loan request sent to ${targetCollege.collegeName} for '${book.title}'."
        }
    }

    fun clearIllStatus() {
        _illStatus.value = null
    }
}

// ── Main Screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnionCatalogScreen(
    onNavigateBack: () -> Unit,
    viewModel: UnionCatalogViewModel = viewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val colleges by viewModel.colleges.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val illStatus by viewModel.illStatus.collectAsState()

    var illTarget by remember { mutableStateOf<Pair<College, Book>?>(null) }
    var requesterName by remember { mutableStateOf("") }

    // ILL Request Dialog
    illTarget?.let { (college, book) ->
        AlertDialog(
            onDismissRequest = { illTarget = null },
            title = { Text("Request ILL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Target: ${college.collegeName}", fontWeight = FontWeight.Bold)
                    Text("Book: ${book.title}")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = requesterName,
                        onValueChange = { requesterName = it },
                        label = { Text("Requester Name (Member)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        viewModel.submitIllRequest(college, book, requesterName.ifBlank { "Library Staff" })
                        illTarget = null
                    }
                ) { Text("Send Request") }
            },
            dismissButton = {
                TextButton(onClick = { illTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Success Status
    illStatus?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearIllStatus() },
            title = { Text("ILL Status") },
            text = { Text(msg, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { viewModel.clearIllStatus() }) { Text("OK") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Union Catalogue") },
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
            
            // Header
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Global Network", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Connected to ${colleges.size} institutions", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search title, author, ISBN") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.searchAcrossInstitutions(searchQuery) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                enabled = !isSearching && searchQuery.isNotBlank()
            ) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Search All Libraries")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Results
            if (searchResults.isEmpty() && !isSearching && searchQuery.isNotBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No results found across the network.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(searchResults, key = { it.second.syncId + it.first.collegeId }) { (college, book) ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "🏛️ ${college.collegeName}", 
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = Color(0xFF10B981), fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = { illTarget = college to book },
                                    enabled = book.status == "Available",
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Text(if (book.status == "Available") "Request ILL" else "Unavailable", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
