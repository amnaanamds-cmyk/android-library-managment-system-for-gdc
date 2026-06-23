package com.college.library.ui.screens.books

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.model.Book
import com.college.library.ui.theme.NavyBlue
import com.college.library.utils.BarcodeGenerator
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditBookViewModel @Inject constructor(
    private val bookDao: BookDao
) : ViewModel() {
    private val _bookState = MutableStateFlow(Book(0, "", "", "", "", "", "", "", "", 0, "", "", 0.0, "Available", false, null))
    val bookState = _bookState.asStateFlow()

    fun loadBook(id: Long) {
        if (id == 0L) return
        viewModelScope.launch {
            bookDao.getBookById(id)?.let { _bookState.value = it }
        }
    }

    fun updateField(updater: (Book) -> Book) {
        _bookState.value = updater(_bookState.value)
    }

    fun saveBook(onComplete: () -> Unit) {
        viewModelScope.launch {
            if (_bookState.value.id == 0L) {
                bookDao.insertBook(_bookState.value.copy(status = "Available"))
            } else {
                bookDao.updateBook(_bookState.value)
            }
            onComplete()
        }
    }

    fun fetchBookDetailsFromIsbn(isbn: String) {
        if (isbn.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL("https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val stream = connection.inputStream
                    val response = stream.bufferedReader().use { it.readText() }
                    val jsonObject = org.json.JSONObject(response)
                    if (jsonObject.has("items")) {
                        val items = jsonObject.getJSONArray("items")
                        if (items.length() > 0) {
                            val volumeInfo = items.getJSONObject(0).getJSONObject("volumeInfo")
                            val fetchedTitle = volumeInfo.optString("title", "")
                            val fetchedAuthor = if (volumeInfo.has("authors")) {
                                val authors = volumeInfo.getJSONArray("authors")
                                if (authors.length() > 0) authors.getString(0) else ""
                            } else ""
                            val fetchedPublisher = volumeInfo.optString("publisher", "")
                            val fetchedDate = volumeInfo.optString("publishedDate", "")
                            val fetchedPages = volumeInfo.optInt("pageCount", 0)

                            launch(kotlinx.coroutines.Dispatchers.Main) {
                                updateField { b ->
                                    b.copy(
                                        title = if (b.title.isEmpty()) fetchedTitle else b.title,
                                        author = if (b.author.isEmpty()) fetchedAuthor else b.author,
                                        publisher = if (b.publisher.isEmpty()) fetchedPublisher else b.publisher,
                                        publishDate = if (b.publishDate.isEmpty()) fetchedDate else b.publishDate,
                                        pages = if (b.pages == 0) fetchedPages else b.pages
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBookScreen(
    bookId: Long = 0L,
    onNavigateBack: () -> Unit,
    viewModel: AddEditBookViewModel = hiltViewModel()
) {
    val book by viewModel.bookState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(bookId) { viewModel.loadBook(bookId) }

    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
            .build()
    }
    val scanner = remember { GmsBarcodeScanning.getClient(context, scannerOptions) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val image = com.google.mlkit.vision.common.InputImage.fromFilePath(context, it)
                val barcodeScanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
                barcodeScanner.process(image).addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull()?.rawValue?.let { scannedIsbn ->
                        viewModel.updateField { b -> b.copy(isbn = scannedIsbn) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (bookId == 0L) "Add Book" else "Edit Book", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = book.title,
                onValueChange = { viewModel.updateField { b -> b.copy(title = it) } },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = book.author,
                onValueChange = { viewModel.updateField { b -> b.copy(author = it) } },
                label = { Text("Author") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = book.isbn,
                    onValueChange = { viewModel.updateField { b -> b.copy(isbn = it) } },
                    label = { Text("ISBN") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { viewModel.fetchBookDetailsFromIsbn(book.isbn) },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("Fetch") }
                Button(
                    onClick = {
                        scanner.startScan()
                            .addOnSuccessListener { barcode ->
                                barcode.rawValue?.let { scannedIsbn ->
                                    viewModel.updateField { b -> b.copy(isbn = scannedIsbn) }
                                }
                            }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("Scan") }
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("Gallery") }
            }
            if (book.isbn.isNotBlank()) {
                val barcodeBitmap = remember(book.isbn) { BarcodeGenerator.generateBarcode(book.isbn) }
                barcodeBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Barcode",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(vertical = 8.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
            }
            OutlinedTextField(
                value = book.accNo,
                onValueChange = { viewModel.updateField { b -> b.copy(accNo = it) } },
                label = { Text("Accession No") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = book.publisher,
                onValueChange = { viewModel.updateField { b -> b.copy(publisher = it) } },
                label = { Text("Publisher") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = book.publisherPlace,
                onValueChange = { viewModel.updateField { b -> b.copy(publisherPlace = it) } },
                label = { Text("Publisher Place") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = book.publishDate,
                onValueChange = { viewModel.updateField { b -> b.copy(publishDate = it) } },
                label = { Text("Publish Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = book.edition,
                    onValueChange = { viewModel.updateField { b -> b.copy(edition = it) } },
                    label = { Text("Edition") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = if (book.pages == 0) "" else book.pages.toString(),
                    onValueChange = { viewModel.updateField { b -> b.copy(pages = it.toIntOrNull() ?: 0) } },
                    label = { Text("Pages") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = book.volume,
                    onValueChange = { viewModel.updateField { b -> b.copy(volume = it) } },
                    label = { Text("Volume") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = if (book.price == 0.0) "" else book.price.toString(),
                    onValueChange = { viewModel.updateField { b -> b.copy(price = it.toDoubleOrNull() ?: 0.0) } },
                    label = { Text("Price (Rs.)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            // Digital Book Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Digital Book", modifier = Modifier.weight(1f))
                Switch(
                    checked = book.isDigital,
                    onCheckedChange = { viewModel.updateField { b -> b.copy(isDigital = it) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = NavyBlue)
                )
            }
            if (book.isDigital) {
                OutlinedTextField(
                    value = book.digitalUrl ?: "",
                    onValueChange = { viewModel.updateField { b -> b.copy(digitalUrl = it.ifBlank { null }) } },
                    label = { Text("E-Book Link (Google Drive URL)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.saveBook(onNavigateBack) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
            ) {
                Text(if (bookId == 0L) "Save Book" else "Update Book", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
