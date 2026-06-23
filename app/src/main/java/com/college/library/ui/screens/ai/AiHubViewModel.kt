package com.college.library.ui.screens.ai

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Book
import com.college.library.data.model.IssuedBook
import com.college.library.data.model.Member
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.random.Random

data class ChatMessage(
    val id: String,
    val sender: SenderType,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SenderType {
    USER,
    LIBBY_AI
}

data class LeaderboardEntry(
    val memberId: String,
    val name: String,
    val department: String,
    val memberType: String,
    val borrowCount: Int
)

@HiltViewModel
class AiHubViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val memberDao: MemberDao,
    private val issuedBookDao: IssuedBookDao
) : ViewModel() {

    val chatMessages = mutableStateListOf<ChatMessage>()
    var isTyping = androidx.compose.runtime.mutableStateOf(false)
        private set

    init {
        // Libby welcome message
        chatMessages.add(
            ChatMessage(
                id = "welcome",
                sender = SenderType.LIBBY_AI,
                text = "Hello! I'm Libby, your GDC Library AI Assistant. 🌟\n\nHow can I help you today? You can ask me to recommend books, check availability, view overdue alerts, or check library policies!"
            )
        )
    }

    // Leaderboard flow: aggregates borrow counts from all transactions
    val leaderboardState: StateFlow<List<LeaderboardEntry>> = issuedBookDao.getAllTransactions()
        .map { transactions ->
            val allMembers = memberDao.getAllMembers().first()
            val memberMap = allMembers.associateBy { it.id }

            // Group transactions by memberId
            val grouped = transactions.groupBy { it.memberId }
            grouped.mapNotNull { (memberId, transList) ->
                val member = memberMap[memberId] ?: return@mapNotNull null
                LeaderboardEntry(
                    memberId = member.memberId,
                    name = member.name,
                    department = member.department,
                    memberType = member.memberType,
                    borrowCount = transList.size
                )
            }.sortedByDescending { it.borrowCount }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val userMsgId = System.currentTimeMillis().toString()
        chatMessages.add(ChatMessage(id = userMsgId, sender = SenderType.USER, text = text))

        viewModelScope.launch {
            isTyping.value = true
            delay(1200) // Simulate natural thinking typing delay
            val replyText = processAiQuery(text)
            val aiMsgId = (System.currentTimeMillis() + 1).toString()
            chatMessages.add(ChatMessage(id = aiMsgId, sender = SenderType.LIBBY_AI, text = replyText))
            isTyping.value = false
        }
    }

    private suspend fun processAiQuery(query: String): String {
        val q = query.lowercase().trim()
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        return when {
            // Personalized Member Recommendation
            q.contains("recommend for member") || q.contains("suggest for member") -> {
                val memberKeyword = q.substringAfter("member").trim()
                if (memberKeyword.length < 2) {
                     "Please provide a member name or ID. Example: 'Recommend for member John' or 'Suggest for member S-101'"
                } else {
                    val memberResult = memberDao.searchMembers(memberKeyword).first()
                    val targetMember = memberResult.firstOrNull()
                    
                    if (targetMember == null) {
                        "I couldn't find a member matching '$memberKeyword'. Please check the name or ID."
                    } else {
                         // Find history
                         val returnedBooks = issuedBookDao.getReturnedBooksByMember(targetMember.id).first()
                         val currentlyIssued = issuedBookDao.getCurrentlyIssuedBooksByMember(targetMember.id).first()
                         
                         val allHistoryIds = (returnedBooks + currentlyIssued).map { it.bookId }.distinct()
                         
                         if (allHistoryIds.isEmpty()) {
                             val availableBooks = bookDao.getAvailableBooks().first()
                             val sampled = availableBooks.shuffled().take(3)
                             val bookListStr = sampled.joinToString("\n") { "• *${it.title}* by ${it.author}" }
                             "${targetMember.name} hasn't borrowed any books yet! I recommend starting with these top available picks:\n\n$bookListStr"
                         } else {
                             // Fetch actual book details for history
                             val allBooks = bookDao.getAllBooks().first()
                             val historyBooks = allBooks.filter { it.id in allHistoryIds }
                             
                             // Find most common author
                             val topAuthor = historyBooks.filter { it.author.isNotBlank() }
                                  .groupingBy { it.author }
                                  .eachCount()
                                  .maxByOrNull { it.value }?.key
                                  
                             if (topAuthor == null) {
                                  val availableBooks = bookDao.getAvailableBooks().first().filter { it.id !in allHistoryIds }
                                  val sampled = availableBooks.shuffled().take(3)
                                  val bookListStr = sampled.joinToString("\n") { "• *${it.title}* by ${it.author}" }
                                  "${targetMember.name} reads a variety of topics! Here are some fresh recommendations they haven't read yet:\n\n$bookListStr"
                             } else {
                                  // Recommend available books by topAuthor not already borrowed
                                  val recommendations = allBooks.filter { 
                                      it.author.equals(topAuthor, ignoreCase = true) && it.status == "Available" && it.id !in allHistoryIds 
                                  }.shuffled().take(3)
                                  
                                  if (recommendations.isEmpty()) {
                                       "${targetMember.name} seems to love books by *$topAuthor*! Unfortunately, we don't have any new available books by them right now. Keep an eye out for returns!"
                                  } else {
                                       val recList = recommendations.joinToString("\n") { "• *${it.title}* by ${it.author}" }
                                       "Based on ${targetMember.name}'s interest in *$topAuthor*, here are my top personalized recommendations:\n\n$recList"
                                  }
                             }
                         }
                    }
                }
            }

            // General Recommendation request
            q.contains("recommend") || q.contains("suggest") || q.contains("popular") || q.contains("interesting") -> {
                val availableBooks = bookDao.getAvailableBooks().first()
                if (availableBooks.isEmpty()) {
                    "Hmm, I searched our shelves and it looks like all books are currently issued. Keep an eye on returns!"
                } else {
                    val count = minOf(3, availableBooks.size)
                    val sampled = availableBooks.shuffled().take(count)
                    val bookListStr = sampled.joinToString("\n") { "• *${it.title}* by ${it.author} (ISBN: ${it.isbn})" }
                    "Here are some top available book recommendations for you today: 📚\n\n$bookListStr\n\nHope you find something great to read! (You can also ask me 'Recommend for member [Name]' for personalized picks!)"
                }
            }

            // Overdue warning lookup
            q.contains("overdue") || q.contains("late") || q.contains("warning") || q.contains("delay") -> {
                val overdue = issuedBookDao.getOverdueBooks(today).first()
                if (overdue.isEmpty()) {
                    "Great news! 🎉 There are currently no overdue books in the library system."
                } else {
                    val overdueListStr = overdue.take(5).joinToString("\n") {
                        "• *${it.bookTitle}* (Issued to: ${it.memberName}, Due: ${it.dueDate})"
                    }
                    val suffix = if (overdue.size > 5) "\n...and ${overdue.size - 5} more." else ""
                    "I found *${overdue.size}* overdue books currently:\n\n$overdueListStr$suffix"
                }
            }

            // Search / Availability checks
            q.contains("available") || q.contains("search") || q.contains("have") || q.contains("find") || q.contains("is there") || q.contains("look up") -> {
                // Extract possible title keyword
                val keywords = listOf("available", "search", "have", "find", "is there", "look up")
                var bookKeyword = q
                for (key in keywords) {
                    bookKeyword = bookKeyword.replace(key, "")
                }
                bookKeyword = bookKeyword.replace("?", "").replace("book", "").replace("title", "").trim()

                if (bookKeyword.length < 2) {
                    "What book title are you looking for? Try asking me: 'Is Python available?' or 'Search algorithms book'."
                } else {
                    val searchResult = bookDao.searchBooks(bookKeyword).first()
                    if (searchResult.isEmpty()) {
                        "I couldn't find any books matching '*$bookKeyword*' in our catalog. Double-check the title or try another keyword!"
                    } else {
                        val reply = StringBuilder("Here are the search results for '*$bookKeyword*':\n\n")
                        searchResult.take(3).forEach { book ->
                            val statusEmoji = if (book.status == "Available") "✅ Available" else "❌ Issued (Due: ${getDueDateForBook(book.id) ?: "N/A"})"
                            reply.append("• *${book.title}* by ${book.author} — $statusEmoji\n")
                        }
                        if (searchResult.size > 3) reply.append("...and ${searchResult.size - 3} other matches.")
                        reply.toString()
                    }
                }
            }

            // Policy details
            q.contains("policy") || q.contains("rule") || q.contains("fine") || q.contains("cost") || q.contains("limit") || q.contains("due") -> {
                "Here is a summary of the GDC Library guidelines: 📋\n\n" +
                        "1. **Borrow Limit:** A member can borrow up to *3 books* at a time.\n" +
                        "2. **Period:** Books can be retained for *14 days* from issue.\n" +
                        "3. **Fines:** Overdue books incur a fine of *Rs. 10.00 per day*.\n" +
                        "4. **Renewals:** You can renew books at the counter before the due date if there are no pending holds."
            }

            // Member statistics
            q.contains("member") || q.contains("student") || q.contains("faculty") || q.contains("department") -> {
                val allMembers = memberDao.getAllMembers().first()
                val totalM = allMembers.size
                val deptCounts = allMembers.groupingBy { it.department }.eachCount()
                val topDeptStr = deptCounts.entries.sortedByDescending { it.value }.take(3).joinToString("\n") { "• ${it.key}: ${it.value} members" }

                "Currently, GDC Library has **$totalM registered members**! 👥\n\n" +
                        "Top Departments by registration:\n$topDeptStr"
            }

            // Greeting
            q.contains("hi") || q.contains("hello") || q.contains("hey") || q.contains("greetings") -> {
                "Hello there! How can I assist you with GDC Library operations today? 😊"
            }

            // Default fallback
            else -> {
                "I'm not sure I fully understood that. 😅\n\n" +
                        "I can help you with:\n" +
                        "• Recommend books (e.g., 'Recommend some books')\n" +
                        "• Search and check availability (e.g., 'Is Java book available?')\n" +
                        "• Alerts (e.g., 'Show overdue books list')\n" +
                        "• Guidelines (e.g., 'What is the fine policy?')"
            }
        }
    }

    private suspend fun getDueDateForBook(bookId: Long): String? {
        val transactions = issuedBookDao.getAllTransactions().first()
        return transactions.firstOrNull { it.bookId == bookId && it.status == "Issued" }?.dueDate
    }
}
