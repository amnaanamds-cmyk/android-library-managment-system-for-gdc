package com.college.library.ui.screens.opac

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.DangerRed
import com.college.library.ui.theme.Gold
import com.college.library.ui.theme.NavyBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OpacState(
    val member: Member? = null,
    val allBooks: List<Book> = emptyList(),
    val myIssuedBooks: List<IssuedBook> = emptyList(),
    val myReturnedBooks: List<IssuedBook> = emptyList(),
    val myFine: Double = 0.0
)

@HiltViewModel
class OpacHomeViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val issuedBookDao: IssuedBookDao,
    private val memberDao: MemberDao
) : ViewModel() {

    private val _state = MutableStateFlow(OpacState())
    val state = _state.asStateFlow()

    var searchQuery by androidx.compose.runtime.mutableStateOf("")
    var selectedTab by androidx.compose.runtime.mutableStateOf(0)

    fun loadData(studentId: Long) {
        viewModelScope.launch {
            memberDao.getMemberByIdFlow(studentId).collect { member ->
                _state.value = _state.value.copy(member = member)
            }
        }
        viewModelScope.launch {
            bookDao.getAllBooks().collect { books ->
                _state.value = _state.value.copy(allBooks = books)
            }
        }
        viewModelScope.launch {
            issuedBookDao.getCurrentlyIssuedBooksByMember(studentId).collect { issued ->
                _state.value = _state.value.copy(myIssuedBooks = issued)
            }
        }
        viewModelScope.launch {
            issuedBookDao.getReturnedBooksByMember(studentId).collect { returned ->
                _state.value = _state.value.copy(myReturnedBooks = returned)
            }
        }
        viewModelScope.launch {
            issuedBookDao.getTotalFineByMember(studentId).collect { fine ->
                _state.value = _state.value.copy(myFine = fine)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpacHomeScreen(
    studentId: Long,
    onLogout: () -> Unit,
    viewModel: OpacHomeViewModel = hiltViewModel()
) {
    LaunchedEffect(studentId) { viewModel.loadData(studentId) }

    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val filteredBooks = state.allBooks.filter {
        viewModel.searchQuery.isBlank() ||
        it.title.contains(viewModel.searchQuery, ignoreCase = true) ||
        it.author.contains(viewModel.searchQuery, ignoreCase = true) ||
        it.isbn.contains(viewModel.searchQuery, ignoreCase = true)
    }

    val tabs = listOf("📚 Catalog", "📖 My Books", "📋 History", "🔖 E-Books")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("OPAC Portal", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(state.member?.name ?: "Student", color = Gold.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, "Logout", tint = Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Student Info Card
            state.member?.let { member ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(NavyBlue, Color(0xFF1A3A6B))))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape)
                                .background(Gold.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null, tint = Gold, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("ID: ${member.memberId}  •  ${member.memberType}", color = Color.White.copy(0.7f), fontSize = 12.sp)
                            Text("Dept: ${member.department}", color = Color.White.copy(0.6f), fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${state.myIssuedBooks.size}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text("Active", color = Color.White.copy(0.6f), fontSize = 10.sp)
                            if (state.myFine > 0) {
                                Text("Fine: Rs.${state.myFine.toInt()}", color = DangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = viewModel.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = viewModel.selectedTab == index,
                        onClick = { viewModel.selectedTab = index },
                        text = { Text(title, fontSize = 12.sp, fontWeight = if (viewModel.selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            when (viewModel.selectedTab) {
                0 -> OpacCatalogTab(filteredBooks, viewModel.searchQuery, { viewModel.searchQuery = it }, context)
                1 -> OpacMyBooksTab(state.myIssuedBooks, state.allBooks, context)
                2 -> OpacHistoryTab(state.myReturnedBooks, state.myFine)
                3 -> OpacEBooksTab(state.allBooks.filter { it.isDigital || !it.digitalUrl.isNullOrBlank() }, context)
            }
        }
    }
}

@Composable
fun OpacCatalogTab(
    books: List<Book>,
    searchQuery: String,
    onSearch: (String) -> Unit,
    context: android.content.Context
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearch,
            placeholder = { Text("Search by title, author, ISBN...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Close, null) }
            },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("${books.size} books found", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(modifier = Modifier.weight(1f))
            val available = books.count { it.status == "Available" }
            Surface(shape = RoundedCornerShape(8.dp), color = CardGreen.copy(alpha = 0.15f)) {
                Text(" $available Available ", color = CardGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
            }
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(books) { book ->
                OpacBookCard(book = book, context = context)
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun OpacBookCard(book: Book, context: android.content.Context) {
    var expanded by remember { mutableStateOf(false) }
    val isAvailable = book.status == "Available"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (isAvailable) CardGreen.copy(0.1f) else DangerRed.copy(0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Book,
                        null,
                        tint = if (isAvailable) CardGreen else DangerRed,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(book.author, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isAvailable) CardGreen.copy(0.15f) else DangerRed.copy(0.15f)
                ) {
                    Text(
                        book.status,
                        color = if (isAvailable) CardGreen else DangerRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (book.isbn.isNotBlank()) OpacInfoChip("ISBN", book.isbn)
                        if (book.accNo.isNotBlank()) OpacInfoChip("Acc.", book.accNo)
                    }
                    if (book.publisher.isNotBlank()) OpacInfoChip("Publisher", book.publisher)
                    if (book.edition.isNotBlank()) OpacInfoChip("Edition", book.edition)
                    if (book.publishDate.isNotBlank()) OpacInfoChip("Year", book.publishDate)

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!book.digitalUrl.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(book.digitalUrl))
                                    try { context.startActivity(intent) } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) { Text("📖 Read Online", fontSize = 12.sp) }
                        }
                        OutlinedButton(
                            onClick = {
                                val shareMsg = "📚 ${book.title}\nAuthor: ${book.author}\nISBN: ${book.isbn}\n${book.digitalUrl ?: ""}"
                                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareMsg) }
                                context.startActivity(Intent.createChooser(intent, "Share"))
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) { Text("📤 Share", fontSize = 12.sp) }
                    }
                }
            }

            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (expanded) "▲ Less Info" else "▼ More Info", fontSize = 12.sp, color = NavyBlue)
            }
        }
    }
}

@Composable
fun OpacInfoChip(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OpacMyBooksTab(issuedBooks: List<IssuedBook>, allBooks: List<Book>, context: android.content.Context) {
    if (issuedBooks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LibraryBooks, null, modifier = Modifier.size(70.dp), tint = NavyBlue.copy(0.3f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No books currently issued", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Visit the library to borrow books!", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(issuedBooks) { issued ->
            val book = allBooks.find { it.id == issued.bookId }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(issued.bookTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("By ${issued.memberName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IssuedInfoPill("Issued", issued.issueDate, NavyBlue)
                        IssuedInfoPill("Due", issued.dueDate, if (issued.dueDate < java.time.LocalDate.now().toString()) DangerRed else CardGreen)
                    }
                    if (issued.dueDate < java.time.LocalDate.now().toString()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = DangerRed.copy(0.1f)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = DangerRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("OVERDUE – Please return immediately", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (book != null && !book.digitalUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(book.digitalUrl))
                            try { context.startActivity(intent) } catch (e: Exception) {}
                        }, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Read E-Book", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IssuedInfoPill(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(0.1f)) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 10.sp, color = color)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun OpacHistoryTab(returnedBooks: List<IssuedBook>, totalFine: Double) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (totalFine > 0) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = DangerRed.copy(0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Receipt, null, tint = DangerRed, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Total Fine Paid", fontWeight = FontWeight.Bold, color = DangerRed)
                        Text("Rs. ${totalFine.toInt()}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                    }
                }
            }
        }

        if (returnedBooks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(60.dp), tint = NavyBlue.copy(0.3f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No borrowing history yet", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Borrowing History (${returnedBooks.size} books)", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                items(returnedBooks) { book ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(CardGreen.copy(0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CheckCircle, null, tint = CardGreen, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(book.bookTitle, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Returned: ${book.returnDate}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (book.fine > 0) {
                                Text("Fine: Rs.${book.fine.toInt()}", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun OpacEBooksTab(eBooks: List<Book>, context: android.content.Context) {
    if (eBooks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(70.dp), tint = NavyBlue.copy(0.3f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No E-Books available", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Ask your librarian to add digital resources", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Digital Resources (${eBooks.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        items(eBooks) { book ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = NavyBlue.copy(0.05f))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(NavyBlue.copy(0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MenuBook, null, tint = NavyBlue, modifier = Modifier.size(26.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(book.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(book.author.ifBlank { "Unknown" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = {
                                val url = book.digitalUrl ?: return@Button
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                try { context.startActivity(intent) } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) { Text("Read", fontSize = 11.sp) }
                        OutlinedButton(
                            onClick = {
                                val shareMsg = "📖 ${book.title}\n${book.author}\n${book.digitalUrl ?: ""}"
                                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareMsg) }
                                context.startActivity(Intent.createChooser(intent, "Share"))
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) { Text("Share", fontSize = 11.sp) }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
