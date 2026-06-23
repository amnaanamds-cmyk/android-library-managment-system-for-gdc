package com.college.library.ui.screens.search

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Book
import com.college.library.data.model.Member
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.CardOrange
import com.college.library.ui.theme.DangerRed
import com.college.library.ui.theme.Gold
import com.college.library.ui.theme.NavyBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────
@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val memberDao: MemberDao,
    @ApplicationContext context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
    private val RECENT_KEY = "recent_searches"

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Filter state
    var bookStatusFilter by mutableStateOf<String?>(null)   // null = all, "Available", "Issued"
    var entityFilter by mutableStateOf("All")               // "All", "Books", "Members"
    var categoryFilter by mutableStateOf<String?>(null)     // e.g. publisher filter

    private val _recentSearches = MutableStateFlow<List<String>>(loadRecentSearches())
    val recentSearches = _recentSearches.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val bookResults: StateFlow<List<Book>> = _searchQuery.flatMapLatest { q ->
        if (q.length < 2) flowOf(emptyList()) else bookDao.searchBooks(q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val memberResults: StateFlow<List<Member>> = _searchQuery.flatMapLatest { q ->
        if (q.length < 2) flowOf(emptyList()) else memberDao.searchMembers(q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived filtered results
    val filteredBooks: List<Book>
        @Composable get() {
            val raw by bookResults.collectAsState()
            return raw
                .let { list -> if (bookStatusFilter != null) list.filter { it.status == bookStatusFilter } else list }
                .let { list -> if (categoryFilter != null) list.filter { it.publisher.equals(categoryFilter, true) } else list }
        }

    val filteredMembers: List<Member>
        @Composable get() {
            val raw by memberResults.collectAsState()
            return raw
        }

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    fun saveSearchQuery(query: String) {
        if (query.isBlank()) return
        val current = loadRecentSearches().toMutableList()
        current.remove(query)
        current.add(0, query)
        val latest = current.take(5)
        prefs.edit().putString(RECENT_KEY, latest.joinToString("||")).apply()
        _recentSearches.value = latest
    }

    fun clearRecentSearches() {
        prefs.edit().remove(RECENT_KEY).apply()
        _recentSearches.value = emptyList()
    }

    private fun loadRecentSearches(): List<String> {
        val str = prefs.getString(RECENT_KEY, "") ?: ""
        return if (str.isBlank()) emptyList() else str.split("||")
    }

    fun clearFilters() {
        bookStatusFilter = null
        entityFilter = "All"
        categoryFilter = null
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onNavigateToBookDetail: (Long) -> Unit,
    onNavigateToMemberDetail: (Long) -> Unit,
    viewModel: GlobalSearchViewModel = hiltViewModel()
) {
    val query by viewModel.searchQuery.collectAsState()
    val filteredBooks = viewModel.filteredBooks
    val filteredMembers = viewModel.filteredMembers
    val recentSearches by viewModel.recentSearches.collectAsState()
    
    val context = LocalContext.current
    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
            .build()
    }
    val scanner = remember { GmsBarcodeScanning.getClient(context, scannerOptions) }

    var showFilters by remember { mutableStateOf(false) }
    val hasActiveFilter = viewModel.bookStatusFilter != null ||
            viewModel.entityFilter != "All" || viewModel.categoryFilter != null

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // ── Search bar + filter toggle ────────────────────────────────────
        Surface(modifier = Modifier.fillMaxWidth(), color = NavyBlue, shadowElevation = 4.dp) {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search books, members…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                    trailingIcon = {
                        Row {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, "Clear")
                                }
                            } else {
                                IconButton(onClick = {
                                    scanner.startScan().addOnSuccessListener { barcode ->
                                        barcode.rawValue?.let { 
                                            viewModel.updateSearchQuery(it)
                                            viewModel.saveSearchQuery(it)
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Scan Barcode")
                                }
                            }
                            IconButton(onClick = { showFilters = !showFilters }) {
                                Icon(
                                    if (hasActiveFilter) Icons.Default.FilterAlt else Icons.Default.FilterList,
                                    contentDescription = "Filters",
                                    tint = if (hasActiveFilter) Gold else Color.White
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.saveSearchQuery(query) })
                )

                // ── Filter panel ─────────────────────────────────────────
                AnimatedVisibility(
                    visible = showFilters,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavyBlue.copy(alpha = 0.85f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Entity filter
                        Text("Show", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("All", "Books", "Members").forEach { entity ->
                                FilterChip(
                                    selected = viewModel.entityFilter == entity,
                                    onClick = { viewModel.entityFilter = entity },
                                    label = { Text(entity, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Gold,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }

                        // Book status filter (only when Books or All)
                        if (viewModel.entityFilter != "Members") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Book Status", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = viewModel.bookStatusFilter == null,
                                    onClick = { viewModel.bookStatusFilter = null },
                                    label = { Text("Any", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Gold,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                                listOf("Available", "Issued").forEach { status ->
                                    FilterChip(
                                        selected = viewModel.bookStatusFilter == status,
                                        onClick = { viewModel.bookStatusFilter = status },
                                        label = { Text(status, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Gold,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                }
                            }
                        }

                        // Category filter
                        if (viewModel.entityFilter != "Members") {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Category", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val categories = listOf(null, "Fiction", "Science", "History", "Technology", "Arts", "Religion")
                                categories.forEach { cat ->
                                    FilterChip(
                                        selected = viewModel.categoryFilter == cat,
                                        onClick = { viewModel.categoryFilter = cat },
                                        label = { Text(cat ?: "All", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Gold,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                }
                            }
                        }

                        // Clear all
                        if (hasActiveFilter) {
                            TextButton(onClick = viewModel::clearFilters) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Filters", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear all filters", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── Results ───────────────────────────────────────────────────────
        if (query.length < 2) {
            // Recent searches
            if (recentSearches.isNotEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Searches", fontWeight = FontWeight.Bold, color = Color.Gray)
                        TextButton(onClick = viewModel::clearRecentSearches) {
                            Text("Clear", color = NavyBlue)
                        }
                    }
                    recentSearches.forEach { recent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSearchQuery(recent)
                                    viewModel.saveSearchQuery(recent)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.History, contentDescription = "Recent Search", tint = Color.Gray)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(recent, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                EmptyStateIllustration("Type at least 2 characters to search across all books and members.")
            }
        } else {
            val showBooks   = viewModel.entityFilter != "Members"
            val showMembers = viewModel.entityFilter != "Books"
            val booksToShow   = if (showBooks) filteredBooks else emptyList()
            val membersToShow = if (showMembers) filteredMembers else emptyList()

            if (booksToShow.isEmpty() && membersToShow.isEmpty()) {
                EmptyStateIllustration("No results found for \"$query\"")
            } else {
                // Active filter summary
                if (hasActiveFilter) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Gold.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FilterAlt, contentDescription = "Filter Indicator",
                                tint = NavyBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                buildString {
                                    if (viewModel.entityFilter != "All") append(viewModel.entityFilter)
                                    if (viewModel.bookStatusFilter != null) append(" · ${viewModel.bookStatusFilter}")
                                    if (viewModel.categoryFilter != null) append(" · ${viewModel.categoryFilter}")
                                },
                                fontSize = 12.sp, color = NavyBlue, fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = viewModel::clearFilters,
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("Clear", fontSize = 12.sp, color = NavyBlue) }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (booksToShow.isNotEmpty()) {
                        item {
                            Text(
                                "Books (${booksToShow.size})",
                                fontWeight = FontWeight.Bold,
                                color = NavyBlue,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(booksToShow, key = { "b_${it.id}" }) { book ->
                            BookSearchResult(book, onClick = {
                                viewModel.saveSearchQuery(query)
                                onNavigateToBookDetail(book.id)
                            })
                        }
                    }

                    if (membersToShow.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Members (${membersToShow.size})",
                                fontWeight = FontWeight.Bold,
                                color = NavyBlue,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(membersToShow, key = { "m_${it.id}" }) { member ->
                            MemberSearchResult(member, onClick = {
                                viewModel.saveSearchQuery(query)
                                onNavigateToMemberDetail(member.id)
                            })
                        }
                    }
                }
            }
        }
    }
}

// ── Result cards ──────────────────────────────────────────────────────────────
@Composable
fun BookSearchResult(book: Book, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, fontWeight = FontWeight.Bold)
                Text(book.author, fontSize = 12.sp, color = Color.Gray)
                if (book.publisher.isNotBlank()) {
                    Text(book.publisher, fontSize = 11.sp, color = NavyBlue.copy(alpha = 0.6f))
                }
            }
            Surface(
                color = if (book.status == "Available") CardGreen.copy(alpha = 0.1f) else DangerRed.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = book.status,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = if (book.status == "Available") CardGreen else DangerRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MemberSearchResult(member: Member, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(member.name, fontWeight = FontWeight.Bold)
                Text("${member.memberId} · ${member.department}", fontSize = 12.sp, color = Color.Gray)
            }
            Surface(
                color = CardOrange.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "${member.booksIssued} books",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = CardOrange,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EmptyStateIllustration(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Search, contentDescription = "Empty Search Results", tint = Color.LightGray, modifier = Modifier.size(120.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, color = Color.Gray, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}
