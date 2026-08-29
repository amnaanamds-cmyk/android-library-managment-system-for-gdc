package com.college.library.ui.screens.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.BookRequestDao
import com.college.library.data.model.BookRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WishlistViewModel @Inject constructor(
    private val bookRequestDao: BookRequestDao
) : ViewModel() {

    val requests: StateFlow<List<BookRequest>> = bookRequestDao.getAllBookRequests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun submitRequest(title: String, author: String, memberName: String, memberId: Long) {
        viewModelScope.launch {
            val req = BookRequest(
                syncId = UUID.randomUUID().toString(),
                title = title,
                author = author,
                memberId = memberId,
                memberName = memberName,
                requestDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
            bookRequestDao.insertBookRequest(req)
        }
    }

    fun updateStatus(id: Long, newStatus: String) {
        viewModelScope.launch {
            bookRequestDao.updateBookRequestStatus(id, newStatus)
        }
    }
}
