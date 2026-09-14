package com.college.library.ui.screens.ai

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.BuildConfig
import com.college.library.data.db.BookDao
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Book
import com.college.library.data.model.IssuedBook
import com.college.library.data.model.Member
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.defineFunction
import com.google.ai.client.generativeai.type.Schema
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

    private val searchBooksTool = defineFunction(
        name = "searchBooks",
        description = "Search for books by title, author, or category.",
        parameters = listOf(
            Schema.str("query", "The search string for title, author or ISBN")
        )
    )

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = BuildConfig.GEMINI_MODEL,
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = content { text("You are Libby, the AI assistant for GDC Library. Use the provided tools to fetch real-time data from the database. Summarize results concisely.") },
            tools = listOf(Tool(listOf(
                defineFunction(
                    name = "getLibraryStats",
                    description = "Get total books, members and issued book counts."
                ),
                defineFunction(
                    name = "searchBooks",
                    description = "Search for books in the library.",
                    parameters = listOf(Schema.str("query", "search text"))
                )
            )))
        )
    }

    private val chat by lazy {
        generativeModel.startChat()
    }

    init {
        // Libby welcome message
        chatMessages.add(
            ChatMessage(
                id = "welcome",
                sender = SenderType.LIBBY_AI,
                text = "Hello! I'm Libby, your GDC Library AI Assistant powered by Gemini. 🌟\n\nHow can I help you today? You can ask me to recommend books, check availability, view overdue alerts, or check library policies!"
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
            
            val replyText = try {
                if (BuildConfig.GEMINI_API_KEY.isBlank()) {
                    "I need a Gemini API key before I can answer.\n\n" +
                        "1. Get a free key at aistudio.google.com/apikey\n" +
                        "2. Open local.properties in the project root\n" +
                        "3. Add this line:\n" +
                        "     GEMINI_API_KEY=your_key_here\n" +
                        "4. Rebuild the app\n\n" +
                        "Everything else in the library works without this — only I do."
                } else {
                    val context = buildLibraryContext()
                    val response = chat.sendMessage("Library Context:\n$context\n\nUser Query: $text")
                    response.text ?: "I'm sorry, I couldn't generate a response."
                }
            } catch (e: Exception) {
                val hint = if (e.message?.contains("not found", ignoreCase = true) == true ||
                    e.message?.contains("404") == true
                ) {
                    "\n\nThat usually means Google no longer serves the model " +
                        "\"${BuildConfig.GEMINI_MODEL}\". Set GEMINI_MODEL in local.properties " +
                        "to a current one and rebuild."
                } else ""
                "I couldn't reach my AI service: ${e.message}$hint"
            }
            
            val aiMsgId = (System.currentTimeMillis() + 1).toString()
            chatMessages.add(ChatMessage(id = aiMsgId, sender = SenderType.LIBBY_AI, text = replyText))
            isTyping.value = false
        }
    }

    private suspend fun buildLibraryContext(): String {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val availableBooks = bookDao.getAvailableBooks().first().take(10)
        val overdueBooks = issuedBookDao.getOverdueBooks(today).first().take(5)
        val membersCount = memberDao.getAllMembers().first().size

        val contextBuilder = StringBuilder()
        contextBuilder.append("Total Members: $membersCount\n")
        
        if (availableBooks.isNotEmpty()) {
            contextBuilder.append("Sample Available Books:\n")
            availableBooks.forEach { book ->
                contextBuilder.append("- ${book.title} by ${book.author} (ISBN: ${book.isbn})\n")
            }
        }
        
        if (overdueBooks.isNotEmpty()) {
            contextBuilder.append("Current Overdue Books:\n")
            overdueBooks.forEach { overdue ->
                contextBuilder.append("- ${overdue.bookTitle} borrowed by ${overdue.memberName}, due on ${overdue.dueDate}\n")
            }
        }

        contextBuilder.append("Library Policies: 1. Max 3 books per member. 2. 14 days issue period. 3. Fine is Rs. 10/day for overdue books.")
        return contextBuilder.toString()
    }
}
