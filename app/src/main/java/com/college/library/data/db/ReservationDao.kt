package com.college.library.data.db

import com.college.library.data.model.Reservation
import kotlinx.coroutines.flow.Flow

interface ReservationDao {
    suspend fun insert(reservation: Reservation)
    fun getByBook(bookId: Long): Flow<List<Reservation>>
    fun getByMember(memberId: Long): Flow<List<Reservation>>
    fun getAllPending(): Flow<List<Reservation>>
    suspend fun updateStatus(id: Long, status: String)
    suspend fun updateNotifiedDate(id: Long, date: String)
    suspend fun delete(reservation: Reservation)
    fun getReservationCount(bookId: Long): Flow<Int>
    suspend fun getFirstPendingForBook(bookId: Long): Reservation?
}
