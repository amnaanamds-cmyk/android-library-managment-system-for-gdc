package com.college.library.ui.screens.issue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.college.library.data.model.Book
import com.college.library.data.model.Member
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import androidx.compose.ui.platform.LocalContext
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.DangerRed
import com.college.library.ui.theme.NavyBlue
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkIssueScreen(
    onNavigateBack: () -> Unit,
    viewModel: BulkIssueViewModel = hiltViewModel()
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val isSuccess by viewModel.isSuccess.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bulk Issue Wizard", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (isSuccess) {
                BulkSuccessReceipt(
                    selectedBooks = viewModel.selectedBooks.collectAsState().value,
                    member = viewModel.selectedMember.collectAsState().value!!,
                    onFinish = {
                        viewModel.reset()
                        onNavigateBack()
                    }
                )
            } else {
                // Wizard Progress
                LinearProgressIndicator(
                    progress = { currentStep / 3f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = CardGreen,
                    trackColor = Color.LightGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Step $currentStep of 3", color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                when (currentStep) {
                    1 -> BulkStep1SelectMember(viewModel = viewModel)
                    2 -> BulkStep2SelectBooks(viewModel = viewModel)
                    3 -> BulkStep3Confirm(viewModel = viewModel)
                }

                Spacer(modifier = Modifier.weight(1f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (currentStep > 1) {
                        OutlinedButton(onClick = { viewModel.prevStep() }) { Text("Back") }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (currentStep < 3) {
                        Button(
                            onClick = { viewModel.nextStep() },
                            enabled = (currentStep == 1 && viewModel.selectedMember.collectAsState().value != null) ||
                                      (currentStep == 2 && viewModel.selectedBooks.collectAsState().value.isNotEmpty()),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
                        ) { Text("Next") }
                    }
                }
            }
        }
    }
}

@Composable
fun BulkStep1SelectMember(viewModel: BulkIssueViewModel) {
    val members by viewModel.membersFlow.collectAsState()
    val searchQuery by viewModel.memberSearchQuery.collectAsState()
    val selectedMember by viewModel.selectedMember.collectAsState()
    val context = LocalContext.current
    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
            .build()
    }
    val scanner = remember { GmsBarcodeScanning.getClient(context, scannerOptions) }

    Column {
        Text("Select a Member", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NavyBlue)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.memberSearchQuery.value = it },
                modifier = Modifier.weight(1f),
                label = { Text("Search Name or ID") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Member") }
            )
            IconButton(
                onClick = {
                    scanner.startScan().addOnSuccessListener { barcode ->
                        barcode.rawValue?.let { id ->
                            viewModel.memberSearchQuery.value = id
                            members.find { it.memberId.equals(id, ignoreCase = true) }?.let { viewModel.selectMember(it) }
                        }
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(Icons.Default.CameraAlt, "Scan ID", tint = NavyBlue, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        if (selectedMember != null) {
            val limitReached = selectedMember!!.booksIssued >= 3
            Card(
                colors = CardDefaults.cardColors(containerColor = if (limitReached) DangerRed.copy(0.1f) else CardGreen.copy(0.1f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (limitReached) DangerRed else CardGreen)
            ) {
                Text(
                    text = "Selected: ${selectedMember!!.name} (${selectedMember!!.booksIssued}/3 issued)",
                    modifier = Modifier.padding(16.dp),
                    color = if (limitReached) DangerRed else CardGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            items(members, key = { it.id }) { member ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.selectMember(member) },
                    colors = CardDefaults.cardColors(containerColor = if (selectedMember == member) NavyBlue.copy(alpha=0.1f) else Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(member.name, fontWeight = FontWeight.Bold)
                        Text("ID: ${member.memberId} | Issued: ${member.booksIssued}/3", fontSize = 12.sp, color = if (member.booksIssued >= 3) DangerRed else Color.Gray)
                    }
                }
            }
        }


    }
}

@Composable
fun BulkStep2SelectBooks(viewModel: BulkIssueViewModel) {
    val books by viewModel.availableBooks.collectAsState()
    val searchQuery by viewModel.bookSearchQuery.collectAsState()
    val selectedBooks by viewModel.selectedBooks.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
            .build()
    }
    val scanner = remember { GmsBarcodeScanning.getClient(context, scannerOptions) }

    Column {
        Text("Select Books", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NavyBlue)
        Text("Selected: ${selectedBooks.size}", color = Color.Gray)

        if (errorMsg != null) {
            Text(errorMsg!!, color = DangerRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        // Selected Books Chips
        if (selectedBooks.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedBooks.forEach { book ->
                    AssistChip(
                        onClick = { viewModel.removeBook(book) },
                        label = { Text(book.title.take(15) + "...", fontSize = 12.sp) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove Book", modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.bookSearchQuery.value = it },
                modifier = Modifier.weight(1f),
                label = { Text("Search Title or ISBN") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Book") }
            )
            IconButton(
                onClick = {
                    scanner.startScan().addOnSuccessListener { barcode ->
                        barcode.rawValue?.let { isbn ->
                            viewModel.bookSearchQuery.value = isbn
                            books.find { it.isbn == isbn }?.let { viewModel.addBook(it) }
                        }
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(Icons.Default.CameraAlt, "Scan ISBN", tint = NavyBlue, modifier = Modifier.size(32.dp))
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            items(books, key = { it.id }) { book ->
                val isSelected = selectedBooks.any { it.id == book.id }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                        if (isSelected) viewModel.removeBook(book) else viewModel.addBook(book) 
                    },
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) NavyBlue.copy(alpha=0.1f) else Color.White)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(book.title, fontWeight = FontWeight.Bold)
                            Text("ISBN: ${book.isbn}", fontSize = 12.sp, color = Color.Gray)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = CardGreen)
                        }
                    }
                }
            }
        }


    }
}

@Composable
fun BulkStep3Confirm(viewModel: BulkIssueViewModel) {
    val selectedMember by viewModel.selectedMember.collectAsState()
    val selectedBooks by viewModel.selectedBooks.collectAsState()
    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    val dueDate = LocalDate.now().plusDays(14).format(DateTimeFormatter.ISO_LOCAL_DATE)
    val errorMsg by viewModel.errorMessage.collectAsState()

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Confirm Bulk Issue", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NavyBlue)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Issuing to:", color = Color.Gray, fontSize = 12.sp)
                Text(selectedMember?.name ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Books (${selectedBooks.size}):", color = Color.Gray, fontSize = 12.sp)
                selectedBooks.forEach { book ->
                    Text("• ${book.title}", fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 2.dp))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Issue: $today", color = Color.Gray, fontSize = 14.sp)
                    Text("Due: $dueDate", fontWeight = FontWeight.Bold, color = DangerRed, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (errorMsg != null) {
            Text(errorMsg!!, color = DangerRed, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Button(
            onClick = { viewModel.issueBooks() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CardGreen)
        ) {
            Text("Confirm & Issue All", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BulkSuccessReceipt(selectedBooks: List<Book>, member: Member, onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = CardGreen, modifier = Modifier.size(100.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("${selectedBooks.size} Books Issued!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CardGreen, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text("to ${member.name}", fontWeight = FontWeight.Medium, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onFinish, colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)) {
            Text("Done")
        }
    }
}
