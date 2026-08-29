package com.college.library.ui.screens.books

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.db.BookReviewDao
import com.college.library.data.model.Book
import com.college.library.data.model.BookReview
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.DangerRed
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.college.library.BuildConfig

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val bookReviewDao: BookReviewDao
) : ViewModel() {
    private val _book = MutableStateFlow<Book?>(null)
    val book = _book.asStateFlow()

    private val _reviews = MutableStateFlow<List<BookReview>>(emptyList())
    val reviews = _reviews.asStateFlow()

    private val _aiSummary = MutableStateFlow<String?>(null)
    val aiSummary = _aiSummary.asStateFlow()

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    fun loadBook(id: Long) {
        viewModelScope.launch {
            val bookData = bookDao.getBookById(id)
            _book.value = bookData
            bookReviewDao.getReviewsForBook(id).collect {
                _reviews.value = it
            }
            
            // Generate AI Summary if not already there
            if (bookData != null && _aiSummary.value == null && BuildConfig.GEMINI_API_KEY.isNotBlank()) {
                generateAiSummary(bookData)
            }
        }
    }

    private fun generateAiSummary(book: Book) {
        viewModelScope.launch {
            try {
                val prompt = "Provide a 2-sentence fascinating summary and one 'Did you know?' fact about the book '${book.title}' by ${book.author}."
                val response = generativeModel.generateContent(prompt)
                _aiSummary.value = response.text
            } catch (e: Exception) {
                _aiSummary.value = "AI Insights currently unavailable."
            }
        }
    }

    fun markAsLost(id: Long) {
        viewModelScope.launch {
            bookDao.updateBookStatus(id, "Lost")
            loadBook(id) // Reload after updating
        }
    }

    fun submitReview(bookId: Long, rating: Int, reviewText: String) {
        viewModelScope.launch {
            val review = BookReview(
                syncId = java.util.UUID.randomUUID().toString(),
                bookId = bookId,
                memberId = 1L, // Mocking memberId for now
                memberName = "Current User",
                rating = rating,
                reviewText = reviewText,
                reviewDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            )
            bookReviewDao.insertBookReview(review)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToCopy: (Long) -> Unit,
    onNavigateToIssue: (String) -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val book by viewModel.book.collectAsState()
    val reviews by viewModel.reviews.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var showMarcDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    DisposableEffect(context) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }
        tts = textToSpeech
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Details", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (isTtsReady && book != null) {
                        IconButton(onClick = {
                            val b = book!!
                            val textToRead = "Book Title: ${b.title}. Author: ${b.author}. Edition: ${b.edition}. Published by ${b.publisher}. Current Status is ${b.status}."
                            tts?.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null, null)
                        }) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Read Aloud", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { showMarcDialog = true }) {
                        Icon(Icons.Default.Description, contentDescription = "MARC View", tint = Color.White)
                    }
                    IconButton(onClick = { book?.let { onNavigateToCopy(it.id) } }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Book", tint = Color.White)
                    }
                    IconButton(onClick = { book?.let { onNavigateToEdit(it.id) } }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        if (book == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("General") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("MARC 21") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Reviews") })
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> GeneralTab(book!!, viewModel, onNavigateToCopy, onNavigateToEdit, onNavigateToIssue)
                        1 -> MarcTab(book!!)
                        2 -> ReviewsTab(reviews, { showReviewDialog = true })
                    }
                }
            }
        }
    }

    if (showMarcDialog && book != null) {
        val b = book!!
        val marcData = buildString {
            appendLine("000  00000nam a2200000 a 4500")
            appendLine("001  ${b.id.toString().padStart(8, '0')}")
            if (b.isbn.isNotBlank()) appendLine("020  \$a ${b.isbn}")
            if (b.author.isNotBlank()) appendLine("100  1# \$a ${b.author}")
            if (b.title.isNotBlank()) appendLine("245  10 \$a ${b.title}")
            if (b.edition.isNotBlank()) appendLine("250  \$a ${b.edition}")
            val pubPlace = b.publisherPlace.ifBlank { "S.l." }
            val pub = b.publisher.ifBlank { "s.n." }
            val pubDate = b.publishDate.ifBlank { "n.d." }
            appendLine("260  \$a $pubPlace : \$b $pub, \$c $pubDate.")
            if (b.pages > 0) appendLine("300  \$a ${b.pages} p.")
            if (b.volume.isNotBlank()) appendLine("490  1# \$a ${b.volume}")
            if (b.category.isNotBlank()) appendLine("650  #0 \$a ${b.category}")
            if (b.accNo.isNotBlank()) appendLine("900  \$a ${b.accNo}")
        }
        AlertDialog(
            onDismissRequest = { showMarcDialog = false },
            title = { Text("MARC Record View") },
            text = {
                OutlinedTextField(
                    value = marcData,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
                )
            },
            confirmButton = {
                TextButton(onClick = { showMarcDialog = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, marcData)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share MARC Record"))
                    }
                ) { Text("Share") }
            }
        )
    }

    if (showReviewDialog && book != null) {
        var ratingStr by remember { mutableStateOf("5") }
        var reviewText by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showReviewDialog = false },
            title = { Text("Write a Review") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ratingStr,
                        onValueChange = { if (it.length <= 1 && it.all { char -> char.isDigit() }) ratingStr = it },
                        label = { Text("Rating (1-5)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = reviewText,
                        onValueChange = { reviewText = it },
                        label = { Text("Review Comments") },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = ratingStr.toIntOrNull() ?: 5
                    val clamped = r.coerceIn(1, 5)
                    viewModel.submitReview(book!!.id, clamped, reviewText)
                    showReviewDialog = false
                }) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showReviewDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(0.4f), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun GeneralTab(
    b: Book,
    viewModel: BookDetailViewModel,
    onNavigateToCopy: (Long) -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToIssue: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val aiSummary by viewModel.aiSummary.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(b.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(b.author, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                DetailRow("Accession Number", b.accNo)
                DetailRow("ISBN", b.isbn)
                DetailRow("Publisher", b.publisher)
                DetailRow("Status", b.status)
            }
        }

        if (aiSummary != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI Insights", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    Text(aiSummary!!, fontSize = 14.sp)
                }
            }
        }

        if (!b.digitalUrl.isNullOrBlank()) {
            Button(
                onClick = { /* Handle reading */ },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("📖 Read E-Book")
            }
        }

        if (b.status == "Available") {
            Button(
                onClick = { onNavigateToIssue(b.isbn) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardGreen)
            ) {
                Text("Issue Book")
            }
        }
    }
}

@Composable
fun MarcTab(b: Book) {
    val marcData = b.marcData ?: "No MARC data available for this record."
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Standardized Bibliographic Record", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = marcData,
                modifier = Modifier.padding(16.dp),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun ReviewsTab(reviews: List<BookReview>, onAddReview: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("User Reviews", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onAddReview) { Text("Write Review") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (reviews.isEmpty()) {
            Text("No reviews yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(reviews) { rev ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(rev.memberName, fontWeight = FontWeight.Bold)
                            Text("⭐ ${rev.rating}/5")
                            Text(rev.reviewText)
                        }
                    }
                }
            }
        }
    }
}
