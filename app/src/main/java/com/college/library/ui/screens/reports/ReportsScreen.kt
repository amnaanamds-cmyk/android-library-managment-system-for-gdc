package com.college.library.ui.screens.reports

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.MemberDao
import com.college.library.data.model.IssuedBook
import com.college.library.data.model.Member
import com.college.library.domain.usecase.CalculateFineUseCase
import com.college.library.ui.theme.*
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ReportsState(
    val totalCollectionValue: Double = 0.0,
    val issuedThisMonth: Int = 0,
    val issuedLastMonth: Int = 0,
    val fineCollectedThisMonth: Double = 0.0,
    val top10Publishers: Map<String, Int> = emptyMap(),
    val availableCount: Int = 0,
    val issuedCount: Int = 0,
    val mostIssuedBooks: List<Pair<String, Int>> = emptyList(),
    val mostActiveBorrowers: List<Member> = emptyList(),
    val overdueBooksWithPhone: List<Pair<IssuedBook, String>> = emptyList(),
    val allBooks: List<com.college.library.data.model.Book> = emptyList(),
    val allMembers: List<Member> = emptyList()
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    bookDao: BookDao,
    memberDao: MemberDao,
    issuedBookDao: IssuedBookDao,
    val calculateFineUseCase: CalculateFineUseCase
) : ViewModel() {

    private val today = LocalDate.now()
    private val thisMonthPrefix = today.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    private val lastMonthPrefix = today.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"))

    val state: StateFlow<ReportsState> = combine(
        bookDao.getAllBooks(),
        memberDao.getAllMembers(),
        issuedBookDao.getAllTransactions(),
        issuedBookDao.getOverdueBooks(today.format(DateTimeFormatter.ISO_LOCAL_DATE))
    ) { books, members, transactions, overdue ->

        val totalVal = books.sumOf { it.price }
        val issuedThisM = transactions.count { it.issueDate.startsWith(thisMonthPrefix) }
        val issuedLastM = transactions.count { it.issueDate.startsWith(lastMonthPrefix) }
        val fineThisM = transactions.filter { it.returnDate?.startsWith(thisMonthPrefix) == true }.sumOf { it.fine }

        val publishers = books.filter { it.publisher.isNotBlank() }
            .groupingBy { it.publisher }.eachCount()
            .entries.sortedByDescending { it.value }.take(10).associate { it.key to it.value }

        val availableCount = books.count { it.status == "Available" }
        val issuedCount = books.count { it.status == "Issued" }

        val mostIssued = transactions.groupingBy { it.bookTitle }.eachCount()
            .entries.sortedByDescending { it.value }.take(10).map { it.key to it.value }

        val mostActive = members.sortedByDescending { it.booksIssued }.take(10)

        val overdueWithPhones = overdue.map { book ->
            val phone = members.find { it.id == book.memberId }?.phone ?: ""
            book to phone
        }

        ReportsState(
            totalCollectionValue = totalVal,
            issuedThisMonth = issuedThisM,
            issuedLastMonth = issuedLastM,
            fineCollectedThisMonth = fineThisM,
            top10Publishers = publishers,
            availableCount = availableCount,
            issuedCount = issuedCount,
            mostIssuedBooks = mostIssued,
            mostActiveBorrowers = mostActive,
            overdueBooksWithPhone = overdueWithPhones,
            allBooks = books,
            allMembers = members
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportsState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Books", "Members")
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue),
                actions = {
                    IconButton(onClick = { exportPdf(context, state) }) {
                        Icon(Icons.Default.Share, "Export PDF", tint = Gold)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = NavyBlue, contentColor = Gold) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold, color = if(selectedTab == index) Gold else MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (selectedTab) {
                    0 -> OverviewTab(state)
                    1 -> BooksTab(state)
                    2 -> MembersTab(state, viewModel.calculateFineUseCase)
                }
            }
        }
    }
}

@Composable
fun OverviewTab(state: ReportsState) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBlue)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Total Collection Value", color = Color.White)
                Text("Rs. ${state.totalCollectionValue}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = DangerLight)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Fine Collected (This Month)", color = DangerRed)
                    Text("Rs. ${state.fineCollectedThisMonth}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                }
            }
        }
        
        Text("Issues: Last Month vs This Month", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Card(modifier = Modifier.fillMaxWidth().height(200.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Box(modifier = Modifier.padding(16.dp)) {
                Chart(
                    chart = columnChart(),
                    model = entryModelOf(state.issuedLastMonth.toFloat(), state.issuedThisMonth.toFloat()),
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(valueFormatter = { value, _ -> if (value.toInt() == 0) "Last Month" else "This Month" })
                )
            }
        }
    }
}

@Composable
fun BooksTab(state: ReportsState) {
    val context = LocalContext.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Button(
                onClick = { exportFullLibraryCatalogPdf(context, state.allBooks) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Share, "Export Full Catalog")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Full Library Catalog (PDF)")
            }
        }
        item {
            Text("Top 10 Publishers", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Card(modifier = Modifier.fillMaxWidth().height(250.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Box(modifier = Modifier.padding(16.dp)) {
                    val entries = state.top10Publishers.values.map { it.toFloat() }.toTypedArray()
                    if (entries.isNotEmpty()) {
                        Chart(
                            chart = columnChart(),
                            model = entryModelOf(*entries),
                            startAxis = rememberStartAxis(),
                            bottomAxis = rememberBottomAxis(valueFormatter = { value, _ -> 
                                val keys = state.top10Publishers.keys.toList()
                                val idx = value.toInt()
                                if (idx in keys.indices) keys[idx].take(5) else ""
                            })
                        )
                    }
                }
            }
        }
        item {
            Text("Availability Ratio", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(100.dp)) {
                        val total = (state.availableCount + state.issuedCount).toFloat()
                        if (total > 0) {
                            val availableAngle = (state.availableCount / total) * 360f
                            drawArc(color = CardGreen, startAngle = 0f, sweepAngle = availableAngle, useCenter = true, style = Fill)
                            drawArc(color = DangerRed, startAngle = availableAngle, sweepAngle = 360f - availableAngle, useCenter = true, style = Fill)
                        } else {
                            drawCircle(color = Color.LightGray)
                        }
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).background(CardGreen))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Available (${state.availableCount})")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).background(DangerRed))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Issued (${state.issuedCount})", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        item {
            Text("Most Issued Books (All Time)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        items(state.mostIssuedBooks) { (title, count) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, modifier = Modifier.weight(1f), maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                Text(count.toString(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun MembersTab(state: ReportsState, calculateFineUseCase: CalculateFineUseCase) {
    val context = LocalContext.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { exportFullMembersPdf(context, state.allMembers.filter { it.memberType == "Student" || it.memberType == "Faculty" }) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Students & Faculty PDF", fontSize = 11.sp)
                }
                Button(
                    onClick = { exportFullStaffPdf(context, state.allMembers.filter { it.memberType == "Staff" }) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Staff Report PDF", fontSize = 11.sp)
                }
            }
        }
        if (state.overdueBooksWithPhone.isNotEmpty()) {
            item {
                Text("Overdue Books", fontWeight = FontWeight.Bold, color = DangerRed)
            }
            items(state.overdueBooksWithPhone) { (book, phone) ->
                val fine = calculateFineUseCase.calculateFine(book.dueDate)
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DangerLight)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(book.bookTitle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text("Member: ${book.memberName}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Due: ${book.dueDate}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    Text("Fine: Rs. $fine", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (phone.isNotBlank()) {
                                IconButton(onClick = {
                                    val message = "Hello ${book.memberName},\nThis is a reminder from GDC Library. The book '${book.bookTitle}' was due on ${book.dueDate}. Your current fine is Rs. $fine. Please return it as soon as possible."
                                    val url = "https://api.whatsapp.com/send?phone=+91$phone&text=${android.net.Uri.encode(message)}"
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse(url)
                                    }
                                    context.startActivity(intent)
                                }) {
                                    Text("💬", fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Text("Most Active Borrowers", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        items(state.mostActiveBorrowers) { member ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(member.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(member.memberType, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${member.booksIssued} books", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
        }
    }
}

fun exportPdf(context: Context, state: ReportsState) {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { textSize = 24f; isFakeBoldText = true }
        
        canvas.drawText("Library Analytics Report", 50f, 50f, paint)
        
        paint.textSize = 14f
        paint.isFakeBoldText = false
        var y = 100f
        
        canvas.drawText("Total Collection Value: Rs. ${state.totalCollectionValue}", 50f, y, paint); y += 30f
        canvas.drawText("Available Books: ${state.availableCount}", 50f, y, paint); y += 30f
        canvas.drawText("Issued Books: ${state.issuedCount}", 50f, y, paint); y += 30f
        canvas.drawText("Fines Collected This Month: Rs. ${state.fineCollectedThisMonth}", 50f, y, paint); y += 50f
        
        paint.isFakeBoldText = true
        canvas.drawText("Top Publishers", 50f, y, paint); y += 30f
        paint.isFakeBoldText = false
        state.top10Publishers.forEach { (pub, count) ->
            canvas.drawText("$pub - $count books", 50f, y, paint); y += 20f
        }
        y += 30f
        
        paint.isFakeBoldText = true
        canvas.drawText("Most Active Borrowers", 50f, y, paint); y += 30f
        paint.isFakeBoldText = false
        state.mostActiveBorrowers.take(5).forEach { member ->
            canvas.drawText("${member.name} - ${member.booksIssued} currently issued", 50f, y, paint); y += 20f
        }

        pdfDocument.finishPage(page)
        
        val file = File(context.cacheDir, "library_report.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF Report"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun exportFullLibraryCatalogPdf(context: Context, allBooks: List<com.college.library.data.model.Book>) {
    try {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint().apply { textSize = 24f; isFakeBoldText = true }

        canvas.drawText("Full Library Catalog", 50f, 50f, paint)

        paint.textSize = 12f
        paint.isFakeBoldText = false
        var y = 100f

        allBooks.forEach { book ->
            val text = "${book.isbn} - ${book.title} by ${book.author} (${book.status})"
            canvas.drawText(text, 50f, y, paint)
            y += 20f

            if (y > 800f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
        }

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "full_library_catalog.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Full Catalog PDF"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun exportFullMembersPdf(context: Context, members: List<Member>) {
    try {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint().apply { textSize = 22f; isFakeBoldText = true }

        canvas.drawText("Library Members Report (Students & Faculty)", 50f, 50f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        var y = 100f

        members.forEach { m ->
            val text = "${m.memberId} - ${m.name} (${m.memberType}) - Dept: ${m.department} - Active Issues: ${m.booksIssued}"
            canvas.drawText(text, 50f, y, paint)
            y += 20f

            if (y > 800f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
        }

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "library_members_report.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Members Report PDF"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun exportFullStaffPdf(context: Context, staff: List<Member>) {
    try {
        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint().apply { textSize = 22f; isFakeBoldText = true }

        canvas.drawText("Library Staff Report", 50f, 50f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        var y = 100f

        staff.forEach { s ->
            val text = "${s.memberId} - ${s.name} - Dept: ${s.department} - Active Issues: ${s.booksIssued}"
            canvas.drawText(text, 50f, y, paint)
            y += 20f

            if (y > 800f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
        }

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "library_staff_report.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Staff Report PDF"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

