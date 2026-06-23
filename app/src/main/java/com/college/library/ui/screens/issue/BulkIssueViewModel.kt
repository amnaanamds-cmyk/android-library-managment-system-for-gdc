package com.college.library.ui.screens.issue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Book
import com.college.library.data.model.Member
import com.college.library.domain.usecase.IssueBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BulkIssueViewModel @Inject constructor(
    private val memberDao: MemberDao,
    private val bookDao: BookDao,
    private val issueBookUseCase: IssueBookUseCase
) : ViewModel() {

    var currentStep = MutableStateFlow(1)
        private set
        
    var memberSearchQuery = MutableStateFlow("")
    var bookSearchQuery = MutableStateFlow("")

    var selectedMember = MutableStateFlow<Member?>(null)
        private set
        
    private val _selectedBooks = MutableStateFlow<List<Book>>(emptyList())
    val selectedBooks = _selectedBooks.asStateFlow()

    var isSuccess = MutableStateFlow(false)
        private set
        
    var errorMessage = MutableStateFlow<String?>(null)
        private set

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val membersFlow = memberSearchQuery.flatMapLatest { q ->
        if (q.isBlank()) memberDao.getAllMembers() else memberDao.searchMembers(q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val availableBooks = bookSearchQuery.flatMapLatest { q ->
        if (q.isBlank()) bookDao.getAvailableBooks() else bookDao.searchBooks(q).map { list -> list.filter { it.status == "Available" } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectMember(member: Member) {
        selectedMember.value = member
    }

    fun addBook(book: Book) {
        val current = _selectedBooks.value.toMutableList()
        if (current.none { it.id == book.id } && (selectedMember.value?.booksIssued ?: 0) + current.size < 3) {
            current.add(book)
            _selectedBooks.value = current
        } else if ((selectedMember.value?.booksIssued ?: 0) + current.size >= 3) {
            errorMessage.value = "Member cannot issue more than 3 books total."
        }
    }

    fun removeBook(book: Book) {
        val current = _selectedBooks.value.toMutableList()
        current.removeAll { it.id == book.id }
        _selectedBooks.value = current
        errorMessage.value = null
    }

    fun nextStep() {
        if (currentStep.value < 3) currentStep.value++
    }

    fun prevStep() {
        if (currentStep.value > 1) currentStep.value--
    }

    fun issueBooks() {
        val m = selectedMember.value ?: return
        val books = _selectedBooks.value
        if (books.isEmpty()) return

        viewModelScope.launch {
            var successCount = 0
            var errorAcc = ""
            for (book in books) {
                val result = issueBookUseCase(book.id, m.id)
                if (result.isSuccess) {
                    successCount++
                } else {
                    errorAcc += "Failed to issue ${book.title}: ${result.exceptionOrNull()?.message}\n"
                }
            }
            if (successCount == books.size) {
                isSuccess.value = true
            } else {
                errorMessage.value = errorAcc.ifBlank { "Failed to issue some books." }
                isSuccess.value = successCount > 0 // partial success
            }
        }
    }

    fun reset() {
        currentStep.value = 1
        selectedMember.value = null
        _selectedBooks.value = emptyList()
        isSuccess.value = false
        memberSearchQuery.value = ""
        bookSearchQuery.value = ""
        errorMessage.value = null
    }
}
