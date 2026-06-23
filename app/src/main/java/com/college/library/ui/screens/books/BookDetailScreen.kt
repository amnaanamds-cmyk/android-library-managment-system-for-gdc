package com.college.library.ui.screens.books

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.college.library.data.model.Book
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.DangerRed
import com.college.library.ui.theme.NavyBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val bookDao: BookDao
) : ViewModel() {
    private val _book = MutableStateFlow<Book?>(null)
    val book = _book.asStateFlow()

    fun loadBook(id: Long) {
        viewModelScope.launch {
            _book.value = bookDao.getBookById(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToIssue: (String) -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val book by viewModel.book.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { book?.let { onNavigateToEdit(it.id) } }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
            )
        }
    ) { padding ->
        if (book == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val b = book!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                        DetailRow("Publisher Place", b.publisherPlace)
                        DetailRow("Publish Date", b.publishDate)
                        DetailRow("Edition", b.edition)
                        DetailRow("Pages", b.pages.toString())
                        DetailRow("Volume", b.volume)
                        DetailRow("Procurement", b.procurement)
                        DetailRow("Price", "Rs. ${b.price}")
                        DetailRow("Status", b.status)
                    }
                }

                if (!b.digitalUrl.isNullOrBlank()) {
                    Button(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(b.digitalUrl))
                            try { context.startActivity(intent) } catch (e: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("📖 Read E-Book", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        val shareMsg = "📖 ${b.title}\n✍️ Author: ${b.author}\n🔗 ${b.digitalUrl ?: ""}".trim()
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, shareMsg)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Book via..."))
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("📤 Share via WhatsApp / Email", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                if (b.status == "Available") {
                    Button(
                        onClick = { onNavigateToIssue(b.isbn) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardGreen)
                    ) {
                        Text("Issue This Book", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.1f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("This book is currently Issued", color = DangerRed, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("To view who has it, check the Issued Books list.", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(0.4f), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
