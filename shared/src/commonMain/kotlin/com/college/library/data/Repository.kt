package com.college.library.data

import com.college.library.models.Book
import com.college.library.models.Member
import com.college.library.models.Transaction
import com.college.library.models.Reservation
import kotlinx.coroutines.flow.Flow

interface Repository {
    fun getBooks(): Flow<List<Book>>
    fun getMembers(): Flow<List<Member>>
    fun getTransactions(): Flow<List<Transaction>>
    fun getReservations(): Flow<List<Reservation>>
    
    suspend fun upsertBook(book: Book)
    suspend fun upsertMember(member: Member)
    suspend fun upsertTransaction(transaction: Transaction)
    suspend fun upsertReservation(reservation: Reservation)
}
