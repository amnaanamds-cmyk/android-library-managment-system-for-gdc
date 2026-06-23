package com.college.library.ui.screens.return_

import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.MemberDao
import com.college.library.data.model.IssuedBook
import com.college.library.data.model.Member
import com.college.library.domain.usecase.CalculateFineUseCase
import com.college.library.domain.usecase.ReturnBookUseCase
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.DangerRed
import com.college.library.ui.theme.NavyBlue
import com.college.library.utils.ReceiptGenerator
import com.college.library.utils.rememberStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReturnBookViewModel @Inject constructor(
    private val issuedBookDao: IssuedBookDao,
    private val memberDao: MemberDao,
    private val returnBookUseCase: ReturnBookUseCase,
    private val calculateFineUseCase: CalculateFineUseCase
) : ViewModel() {
    var memberSearchQuery by mutableStateOf("")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val membersFlow = snapshotFlow { memberSearchQuery }.flatMapLatest { q ->
        if (q.isBlank()) memberDao.getAllMembers() else memberDao.searchMembers(q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedMember by mutableStateOf<Member?>(null)
        private set
    var selectedBookToReturn by mutableStateOf<IssuedBook?>(null)
        private set
    var computedFine by mutableStateOf(0.0)
        private set

    private val _issuedBooks = MutableStateFlow<List<IssuedBook>>(emptyList())
    val issuedBooks = _issuedBooks.asStateFlow()

    var isSuccess by mutableStateOf(false)
        private set
    var finalFinePaid by mutableStateOf(0.0)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun selectMember(member: Member) {
        selectedMember = member
        selectedBookToReturn = null
        viewModelScope.launch {
            issuedBookDao.getCurrentlyIssuedBooksByMember(member.id).collect {
                _issuedBooks.value = it
            }
        }
    }

    fun selectBookToReturn(book: IssuedBook) {
        selectedBookToReturn = book
        computedFine = calculateFineUseCase(book.dueDate)
    }

    fun returnBook() {
        val b = selectedBookToReturn ?: return
        viewModelScope.launch {
            val result = returnBookUseCase(b.id)
            if (result.isSuccess) {
                finalFinePaid = result.getOrNull() ?: 0.0
                isSuccess = true
            } else {
                errorMessage = result.exceptionOrNull()?.message
            }
        }
    }

    fun reset() {
        selectedMember = null
        selectedBookToReturn = null
        isSuccess = false
        memberSearchQuery = ""
        errorMessage = null
        computedFine = 0.0
        finalFinePaid = 0.0
        _issuedBooks.value = emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnBookScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReturnBookViewModel = hiltViewModel()
) {
    val members by viewModel.membersFlow.collectAsState()
    val issuedBooks by viewModel.issuedBooks.collectAsState()
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
                title = { Text("Return Book", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
            )
        }
    ) { padding ->
        val strings = rememberStrings()
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (viewModel.isSuccess) {
                ReturnReceipt(
                    returnedBook = viewModel.selectedBookToReturn,
                    fine = viewModel.finalFinePaid,
                    strings = strings,
                    onFinish = {
                        viewModel.reset()
                        onNavigateBack()
                    }
                )
            } else {
                if (viewModel.selectedMember == null) {
                    Text("Search Member", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NavyBlue)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = viewModel.memberSearchQuery,
                            onValueChange = { viewModel.memberSearchQuery = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Member Name or ID") },
                            leadingIcon = { Icon(Icons.Default.Search, null) }
                        )
                        IconButton(
                            onClick = {
                                scanner.startScan()
                                    .addOnSuccessListener { barcode ->
                                        barcode.rawValue?.let { memberId ->
                                            viewModel.memberSearchQuery = memberId
                                            val match = members.firstOrNull { it.memberId.equals(memberId, ignoreCase = true) }
                                            if (match != null) {
                                                viewModel.selectMember(match)
                                            }
                                        }
                                    }
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, "Scan ID Card", tint = NavyBlue, modifier = Modifier.size(32.dp))
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        items(members, key = { it.id }) { member ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.selectMember(member) },
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(member.name, fontWeight = FontWeight.Bold)
                                    Text("ID: ${member.memberId}", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Member: ${viewModel.selectedMember!!.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.reset() }) { Text("Change", color = NavyBlue) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (issuedBooks.isEmpty()) {
                        Text("No books currently issued to this member.", color = Color.Gray)
                    } else {
                        Text("Select book to return:", fontWeight = FontWeight.Medium)
                        LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                            items(issuedBooks, key = { it.id }) { book ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.selectBookToReturn(book) },
                                    colors = CardDefaults.cardColors(containerColor = if (viewModel.selectedBookToReturn == book) NavyBlue.copy(0.1f) else Color.White),
                                    border = if (viewModel.selectedBookToReturn == book) androidx.compose.foundation.BorderStroke(1.dp, NavyBlue) else null
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(book.bookTitle, fontWeight = FontWeight.Bold)
                                        Text("Due: ${book.dueDate}", fontSize = 12.sp, color = DangerRed)
                                    }
                                }
                            }
                        }
                    }

                    if (viewModel.selectedBookToReturn != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = DangerRed.copy(0.1f)), border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Return today:", fontWeight = FontWeight.Bold)
                                Text("Fine = Rs. ${viewModel.computedFine}", color = DangerRed, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (viewModel.errorMessage != null) {
                            Text(viewModel.errorMessage!!, color = DangerRed, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewModel.returnBook() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                        ) {
                            Text("Confirm Return", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }


    }
}

@Composable
fun ReturnReceipt(
    returnedBook: IssuedBook?,
    fine: Double,
    strings: com.college.library.utils.AppStrings,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val bookTitle = returnedBook?.bookTitle ?: ""
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = CardGreen, modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(strings.bookReturnedSuccess, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CardGreen, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(bookTitle, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                if (fine > 0) {
                    Text(strings.fineAmount, color = Color.Gray, fontSize = 12.sp)
                    Text("${strings.rupees} ${"%.2f".format(fine)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                } else {
                    Text(strings.noFine, color = CardGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        // Receipt buttons
        if (returnedBook != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { ReceiptGenerator.shareReturnSlip(context, returnedBook, fine, strings) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.printReceipt, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { ReceiptGenerator.shareReturnSlip(context, returnedBook, fine, strings) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.shareReceipt, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Button(onClick = onFinish, colors = ButtonDefaults.buttonColors(containerColor = NavyBlue), modifier = Modifier.fillMaxWidth()) {
            Text(strings.done)
        }
    }
}
