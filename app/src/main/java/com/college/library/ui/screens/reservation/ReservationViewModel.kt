package com.college.library.ui.screens.reservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.ReservationDao
import com.college.library.data.model.Reservation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReservationUiState(
    val pendingReservations: List<Reservation> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class ReservationViewModel @Inject constructor(
    private val reservationDao: ReservationDao
) : ViewModel() {

    private val _state = MutableStateFlow(ReservationUiState())
    val state: StateFlow<ReservationUiState> = _state.asStateFlow()

    init {
        loadPendingReservations()
    }

    private fun loadPendingReservations() {
        viewModelScope.launch {
            reservationDao.getAllPending().collect { reservations ->
                _state.value = _state.value.copy(
                    pendingReservations = reservations,
                    isLoading = false
                )
            }
        }
    }

    fun fulfillReservation(reservation: Reservation) {
        viewModelScope.launch {
            reservationDao.updateStatus(reservation.id, "Fulfilled")
            _state.value = _state.value.copy(
                message = "Reservation fulfilled for \"${reservation.bookTitle}\""
            )
        }
    }

    fun cancelReservation(reservation: Reservation) {
        viewModelScope.launch {
            reservationDao.updateStatus(reservation.id, "Cancelled")
            _state.value = _state.value.copy(
                message = "Reservation cancelled for \"${reservation.bookTitle}\""
            )
        }
    }

    fun notifyReservation(reservation: Reservation) {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            reservationDao.updateNotifiedDate(reservation.id, today)
            _state.value = _state.value.copy(
                message = "Notified ${reservation.memberName} that \"${reservation.bookTitle}\" is available"
            )
        }
    }

    fun addReservation(
        bookId: Long,
        bookTitle: String,
        memberId: Long,
        memberName: String
    ) {
        viewModelScope.launch {
            val reservation = Reservation(
                bookId = bookId,
                bookTitle = bookTitle,
                memberId = memberId,
                memberName = memberName,
                reservedDate = LocalDate.now().toString()
            )
            reservationDao.insert(reservation)
            _state.value = _state.value.copy(
                message = "Reservation placed for \"$bookTitle\""
            )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
