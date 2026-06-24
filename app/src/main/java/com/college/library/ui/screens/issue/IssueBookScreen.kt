package com.college.library.ui.screens.issue

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Book
import com.college.library.data.model.IssuedBook
import com.college.library.data.model.Member
import com.college.library.domain.usecase.IssueBookUseCase
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.DangerRed
import com.college.library.utils.ReceiptGenerator
import com.college.library.utils.rememberStrings
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class IssueBookViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val memberDao: MemberDao,
    private val issuedBookDao: IssuedBookDao,
    private val issueBookUseCase: IssueBookUseCase
) : ViewModel() {
    var currentStep by mutableStateOf(1)
        private set
    var bookSearchQuery by mutableStateOf("")
    var memberSearchQuery by mutableStateOf("")
    var selectedBook by mutableStateOf<Book?>(null)
    var selectedMember by mutableStateOf<Member?>(null)

    var issueDateInput by mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
    var dueDateInput by mutableStateOf(LocalDate.now().plusDays(14).format(DateTimeFormatter.ISO_LOCAL_DATE))

    var isSuccess by mutableStateOf(false)
        private set
    var issuedBookRecord by mutableStateOf<IssuedBook?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val availableBooks = snapshotFlow { bookSearchQuery }.flatMapLatest { q ->
        if (q.isBlank()) bookDao.getAvailableBooks() else bookDao.searchBooks(q).map { list -> list.filter { it.status == "Available" } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val membersFlow = snapshotFlow { memberSearchQuery }.flatMapLatest { q ->
        if (q.isBlank()) memberDao.getAllMembers() else memberDao.searchMembers(q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun nextStep() { if (currentStep < 3) currentStep++ }
    fun prevStep() { if (currentStep > 1) currentStep-- }

    fun issueBook() {
        val b = selectedBook ?: return
        val m = selectedMember ?: return
        viewModelScope.launch {
            val result = issueBookUseCase(b.id, m.id, issueDateInput, dueDateInput)
            if (result.isSuccess) {
                // Fetch the issued record for receipt generation
                val issued = issuedBookDao.getCurrentlyIssuedBooksByMember(m.id)
                    .firstOrNull()?.firstOrNull { it.bookId == b.id }
                issuedBookRecord = issued
                isSuccess = true
            } else {
                errorMessage = result.exceptionOrNull()?.message
            }
        }
    }

    fun reset() {
        currentStep = 1
        selectedBook = null
        selectedMember = null
        issueDateInput = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        dueDateInput = LocalDate.now().plusDays(14).format(DateTimeFormatter.ISO_LOCAL_DATE)
        isSuccess = false
        issuedBookRecord = null
        bookSearchQuery = ""
        memberSearchQuery = ""
        errorMessage = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueBookScreen(
    isbn: String = "",
    onNavigateBack: () -> Unit,
    viewModel: IssueBookViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
            .build()
    }
    val scanner = remember { GmsBarcodeScanning.getClient(context, scannerOptions) }

    LaunchedEffect(isbn) {
        if (isbn.isNotBlank()) {
            viewModel.bookSearchQuery = isbn
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val image = com.google.mlkit.vision.common.InputImage.fromFilePath(context, it)
                val barcodeScanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
                barcodeScanner.process(image).addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull()?.rawValue?.let { scannedIsbn ->
                        viewModel.bookSearchQuery = scannedIsbn
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
                title = { Text("Issue Book", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        val strings = rememberStrings()
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (viewModel.isSuccess) {
                SuccessReceipt(
                    bookTitle = viewModel.selectedBook?.title ?: "",
                    memberName = viewModel.selectedMember?.name ?: "",
                    issuedBook = viewModel.issuedBookRecord,
                    strings = strings,
                    onFinish = {
                        viewModel.reset()
                        onNavigateBack()
                    }
                )
            } else {
                Text("${strings.step} ${viewModel.currentStep} ${strings.of} 3", color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                when (viewModel.currentStep) {
                    1 -> Step1SelectBook(
                        viewModel = viewModel,
                        onScanClick = {
                            scanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    barcode.rawValue?.let { viewModel.bookSearchQuery = it }
                                }
                        },
                        onGalleryClick = { galleryLauncher.launch("image/*") }
                    )
                    2 -> Step2SelectMember(viewModel = viewModel)
                    3 -> Step3Confirm(viewModel = viewModel)
                }

                Spacer(modifier = Modifier.weight(1f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (viewModel.currentStep > 1) {
                        OutlinedButton(onClick = { viewModel.prevStep() }) { Text("Back") }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (viewModel.currentStep < 3) {
                        Button(
                            onClick = { viewModel.nextStep() },
                            enabled = (viewModel.currentStep == 1 && viewModel.selectedBook != null) ||
                                      (viewModel.currentStep == 2 && viewModel.selectedMember != null && viewModel.selectedMember!!.booksIssued < 3),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Next") }
                    }
                }
            }
        }


    }
}

@Composable
fun Step1SelectBook(viewModel: IssueBookViewModel, onScanClick: () -> Unit, onGalleryClick: () -> Unit) {
    val books by viewModel.availableBooks.collectAsState()
    
    LaunchedEffect(books) {
        if (books.size == 1 && viewModel.bookSearchQuery.isNotBlank() && viewModel.selectedBook != books.first()) {
            viewModel.selectedBook = books.first()
            viewModel.nextStep()
        }
    }

    Column {
        Text("Select a Book", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = viewModel.bookSearchQuery,
                onValueChange = { viewModel.bookSearchQuery = it },
                modifier = Modifier.weight(1f),
                label = { Text("Search Title or ISBN") },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
            IconButton(onClick = onScanClick, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Default.CameraAlt, "Scan ISBN", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = onGalleryClick, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Default.Image, "Scan from Gallery", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (viewModel.selectedBook != null) {
            Card(colors = CardDefaults.cardColors(containerColor = CardGreen.copy(alpha = 0.1f)), border = androidx.compose.foundation.BorderStroke(1.dp, CardGreen)) {
                Text("Selected: ${viewModel.selectedBook!!.title}", modifier = Modifier.padding(16.dp), color = CardGreen, fontWeight = FontWeight.Bold)
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            items(books, key = { it.id }) { book ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.selectedBook = book },
                    colors = CardDefaults.cardColors(containerColor = if (viewModel.selectedBook == book) MaterialTheme.colorScheme.primary.copy(alpha=0.1f) else Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(book.title, fontWeight = FontWeight.Bold)
                        Text("ISBN: ${book.isbn}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun Step2SelectMember(viewModel: IssueBookViewModel) {
    val members by viewModel.membersFlow.collectAsState()
    Column {
        Text("Select a Member", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = viewModel.memberSearchQuery,
            onValueChange = { viewModel.memberSearchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("Search Name or ID") },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (viewModel.selectedMember != null) {
            val limitReached = viewModel.selectedMember!!.booksIssued >= 3
            Card(
                colors = CardDefaults.cardColors(containerColor = if (limitReached) DangerRed.copy(0.1f) else CardGreen.copy(0.1f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (limitReached) DangerRed else CardGreen)
            ) {
                Text(
                    text = "Selected: ${viewModel.selectedMember!!.name} (${viewModel.selectedMember!!.booksIssued}/3 issued)",
                    modifier = Modifier.padding(16.dp),
                    color = if (limitReached) DangerRed else CardGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            items(members, key = { it.id }) { member ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.selectedMember = member },
                    colors = CardDefaults.cardColors(containerColor = if (viewModel.selectedMember == member) MaterialTheme.colorScheme.primary.copy(alpha=0.1f) else Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(member.name, fontWeight = FontWeight.Bold)
                        Text("ID: ${member.memberId} | Issued: ${member.booksIssued}", fontSize = 12.sp, color = if (member.booksIssued >= 3) DangerRed else Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun Step3Confirm(viewModel: IssueBookViewModel) {
    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val dueDate = LocalDate.now().plusDays(14).format(DateTimeFormatter.ISO_LOCAL_DATE)
    
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Confirm Issue", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Book: ${viewModel.selectedBook?.title}", fontWeight = FontWeight.Bold)
                Text("Member: ${viewModel.selectedMember?.name} (${viewModel.selectedMember?.memberId})")
                HorizontalDivider()
                OutlinedTextField(
                    value = viewModel.issueDateInput,
                    onValueChange = { viewModel.issueDateInput = it },
                    label = { Text("Issue Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewModel.dueDateInput,
                    onValueChange = { viewModel.dueDateInput = it },
                    label = { Text("Due/Expiry Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (viewModel.errorMessage != null) {
            Text(viewModel.errorMessage!!, color = DangerRed, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = { viewModel.issueBook() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CardGreen)
        ) {
            Text("Issue Book", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SuccessReceipt(
    bookTitle: String,
    memberName: String,
    issuedBook: IssuedBook?,
    strings: com.college.library.utils.AppStrings,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = CardGreen, modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(strings.bookIssuedSuccess, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CardGreen, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(bookTitle, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(strings.issuedTo, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                Text(memberName, fontWeight = FontWeight.Bold)
                if (issuedBook != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${strings.dueDate}:", color = Color.Gray, fontSize = 12.sp)
                        Text(issuedBook.dueDate, fontWeight = FontWeight.Bold, color = DangerRed, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        // Receipt buttons
        if (issuedBook != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { ReceiptGenerator.shareIssueSlip(context, issuedBook, strings) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.printReceipt, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { ReceiptGenerator.shareIssueSlip(context, issuedBook, strings) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.shareReceipt, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Button(onClick = onFinish, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth()) {
            Text(strings.done)
        }
    }
}
