package com.college.library.ui.screens.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.MemberDao
import com.college.library.data.model.IssuedBook
import com.college.library.data.model.Member
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.CardOrange
import com.college.library.ui.theme.DangerRed
import com.college.library.ui.theme.NavyBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberDetailViewModel @Inject constructor(
    private val memberDao: MemberDao,
    private val issuedBookDao: IssuedBookDao
) : ViewModel() {
    private val _member = MutableStateFlow<Member?>(null)
    val member = _member.asStateFlow()

    private val _currentBooks = MutableStateFlow<List<IssuedBook>>(emptyList())
    val currentBooks = _currentBooks.asStateFlow()

    private val _pastTransactions = MutableStateFlow<List<IssuedBook>>(emptyList())
    val pastTransactions = _pastTransactions.asStateFlow()

    private val _totalFines = MutableStateFlow(0.0)
    val totalFines = _totalFines.asStateFlow()

    fun loadMember(id: Long) {
        viewModelScope.launch {
            _member.value = memberDao.getMemberById(id)
            issuedBookDao.getCurrentlyIssuedBooksByMember(id).collect { _currentBooks.value = it }
        }
        viewModelScope.launch {
            issuedBookDao.getReturnedBooksByMember(id).collect { _pastTransactions.value = it }
        }
        viewModelScope.launch {
            issuedBookDao.getTotalFineByMember(id).collect { _totalFines.value = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    memberId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: MemberDetailViewModel = hiltViewModel(),
    authViewModel: com.college.library.ui.screens.auth.AuthViewModel = hiltViewModel()
) {
    val member by viewModel.member.collectAsState()
    val currentBooks by viewModel.currentBooks.collectAsState()
    val pastTransactions by viewModel.pastTransactions.collectAsState()
    val totalFines by viewModel.totalFines.collectAsState()
    val canEdit = authViewModel.canEditMembers()
    var showCardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(memberId) { viewModel.loadMember(memberId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Member Profile", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { showCardDialog = true }) {
                        Icon(Icons.Default.ContactPage, "Membership Card", tint = Color.White)
                    }
                    if (canEdit) {
                        IconButton(onClick = { member?.let { onNavigateToEdit(it.id) } }) {
                            Icon(Icons.Default.Edit, "Edit", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
            )
        }
    ) { padding ->
        if (member == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val m = member!!
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                if (m.photoUri != null) {
                                    coil.compose.AsyncImage(
                                        model = m.photoUri,
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier.size(80.dp).clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Profile Picture Placeholder",
                                        modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(m.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("${m.memberType} • ${m.department}", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            DetailRow("Member ID", m.memberId)
                            if (m.memberType == "Student") {
                                if (m.fatherName.isNotBlank()) DetailRow("Father's Name", m.fatherName)
                                if (m.className.isNotBlank()) DetailRow("Class/Semester", m.className)
                                if (m.classNo.isNotBlank()) DetailRow("Roll No", m.classNo)
                                if (m.address.isNotBlank()) DetailRow("Address", m.address)
                            } else if (m.memberType == "Faculty") {
                                if (m.designation.isNotBlank()) DetailRow("Designation", m.designation)
                                if (m.bps.isNotBlank()) DetailRow("BPS", m.bps)
                            }
                            DetailRow("Email", m.email)
                            DetailRow("Phone", m.phone)
                            DetailRow("Joined", m.joinDate)
                            DetailRow("Expiry", m.expiryDate)
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBox("Currently Issued", currentBooks.size.toString(), CardOrange, Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(16.dp))
                        StatBox("Total Fines Paid", "Rs. $totalFines", DangerRed, Modifier.weight(1f))
                    }
                }

                if (currentBooks.isNotEmpty()) {
                    item { Text("Currently Issued Books", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    items(currentBooks) { book ->
                        TransactionCard(book)
                    }
                }

                if (pastTransactions.isNotEmpty()) {
                    item { Text("Transaction History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                    items(pastTransactions) { book ->
                        TransactionCard(book)
                    }
                }
            }

            if (showCardDialog) {
                DigitalCardDialog(
                    member = m,
                    onDismiss = { showCardDialog = false },
                    context = androidx.compose.ui.platform.LocalContext.current
                )
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(0.4f), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)), border = androidx.compose.foundation.BorderStroke(1.dp, color)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun TransactionCard(book: IssuedBook) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(book.bookTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Issued: ${book.issueDate}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (book.status == "Returned") {
                    Text("Returned: ${book.returnDate}", fontSize = 12.sp, color = CardGreen)
                } else {
                    Text("Due: ${book.dueDate}", fontSize = 12.sp, color = DangerRed)
                }
            }
            if (book.fine > 0) {
                Text("Fine Paid: Rs. ${book.fine}", fontSize = 12.sp, color = DangerRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

