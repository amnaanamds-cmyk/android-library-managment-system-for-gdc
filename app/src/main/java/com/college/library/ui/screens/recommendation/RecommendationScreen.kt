package com.college.library.ui.screens.recommendation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.model.Book
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.college.library.ui.theme.*

data class BookRecommendation(
    val book: Book,
    val score: Float,
    val reason: String
)

@HiltViewModel
class RecommendationViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val issuedBookDao: IssuedBookDao
) : ViewModel() {

    private val _recommendations = MutableStateFlow<List<BookRecommendation>>(emptyList())
    val recommendations = _recommendations.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(listOf("All"))
    val categories = _categories.asStateFlow()

    init { loadRecommendations() }

    fun selectCategory(cat: String) {
        _selectedCategory.value = cat
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val allBooks = bookDao.getAllBooks().first()
                val allIssues = issuedBookDao.getAllTransactions().first()

                // Compute borrow frequency per book
                val borrowCount = allIssues.groupBy { it.bookTitle }
                    .mapValues { it.value.size }

                // Compute category popularity
                val catPop = allIssues.mapNotNull { issue ->
                    allBooks.find { it.title == issue.bookTitle }?.category
                }.groupBy { it }.mapValues { it.value.size }

                // Build recommendations for available books
                val recs = allBooks
                    .filter { it.status == "Available" }
                    .map { book ->
                        val frequency = borrowCount[book.title] ?: 0
                        val catScore = catPop[book.category] ?: 0
                        val score = (frequency * 2 + catScore).toFloat()
                        val reason = when {
                            frequency > 5 -> "🔥 Highly popular — borrowed ${frequency}x"
                            catScore > 10 -> "📈 Top category: ${book.category}"
                            frequency > 0 -> "👍 Borrowed ${frequency}x — steady demand"
                            else -> "✨ New or underexplored — great for discovery"
                        }
                        BookRecommendation(book, score, reason)
                    }
                    .sortedByDescending { it.score }

                // Collect unique categories
                val cats = listOf("All") + allBooks.map { it.category }
                    .filter { it.isNotBlank() }.distinct().sorted()
                _categories.value = cats

                _recommendations.value = recs
            } catch (e: Exception) {
                _recommendations.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    val filteredRecommendations: StateFlow<List<BookRecommendation>> =
        combine(_recommendations, _selectedCategory) { recs, cat ->
            if (cat == "All") recs else recs.filter { it.book.category == cat }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecommendationViewModel = hiltViewModel()
) {
    val filtered by viewModel.filteredRecommendations.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🤖 Smart Recommendations", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadRecommendations() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Hero Banner
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI-Powered Book Engine", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                    }
                    Text(
                        "Based on circulation patterns & category popularity",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Category filter chips
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 8.dp
            ) {
                categories.forEachIndexed { _, cat ->
                    Tab(
                        selected = selectedCategory == cat,
                        onClick = { viewModel.selectCategory(cat) },
                        text = { Text(cat, fontSize = 12.sp) }
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Analyzing circulation patterns...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No recommendations found for this category.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(filtered) { rec ->
                        RecommendationCard(rec)
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendationCard(rec: BookRecommendation) {
    val maxScore = 30f
    val stars = ((rec.score / maxScore) * 5).coerceIn(1f, 5f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            // Score indicator
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("📚", fontSize = 28.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(rec.book.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(rec.book.author, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(6.dp))

                // Star rating row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(stars.toInt()) {
                        Icon(Icons.Default.Star, null, tint = Warning, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${String.format("%.1f", stars)}", fontSize = 12.sp, color = Warning, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Category and reason chip
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (rec.book.category.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                rec.book.category,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(rec.reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
