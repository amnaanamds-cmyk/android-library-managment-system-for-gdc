package com.college.library.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookDao
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.MemberDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class ExportState(
    val isExporting: Boolean = false,
    val currentExport: ExportType? = null,
    val error: String? = null,
    val completedFile: File? = null
)

enum class ExportType { BOOKS, MEMBERS, TRANSACTIONS, OVERDUE }

@HiltViewModel
class ExportViewModel @Inject constructor(
    application: Application,
    private val bookDao: BookDao,
    private val memberDao: MemberDao,
    private val issuedBookDao: IssuedBookDao
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private val pdfManager = PdfExportManager(application)

    fun exportBooks() = export(ExportType.BOOKS) {
        val books = bookDao.getAllBooks().first()
        pdfManager.exportBooksCatalog(books)
    }

    fun exportMembers() = export(ExportType.MEMBERS) {
        val members = memberDao.getAllMembers().first()
        pdfManager.exportMembersDirectory(members)
    }

    fun exportTransactions() = export(ExportType.TRANSACTIONS) {
        val transactions = issuedBookDao.getAllTransactions().first()
        pdfManager.exportTransactionHistory(transactions)
    }

    fun exportOverdue() = export(ExportType.OVERDUE) {
        val today = LocalDate.now().toString()
        val overdue = issuedBookDao.getOverdueBooks(today).first()
        pdfManager.exportOverdueReport(overdue)
    }

    fun shareFile(file: File) {
        pdfManager.shareFile(file)
    }

    fun clearState() {
        _state.value = ExportState()
    }

    private fun export(type: ExportType, block: suspend () -> File) {
        if (_state.value.isExporting) return

        viewModelScope.launch {
            _state.value = ExportState(isExporting = true, currentExport = type)
            try {
                val file = withContext(Dispatchers.IO) { block() }
                _state.value = ExportState(completedFile = file)
            } catch (e: Exception) {
                _state.value = ExportState(error = e.message ?: "Export failed")
            }
        }
    }
}
