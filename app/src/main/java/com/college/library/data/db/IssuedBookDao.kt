package com.college.library.data.db

import com.college.library.data.model.IssuedBook
import kotlinx.coroutines.flow.Flow

interface IssuedBookDao {
    fun getCurrentlyIssuedBooks(): Flow<List<IssuedBook>>
    fun getOverdueBooks(today: String): Flow<List<IssuedBook>>
    fun getAllTransactions(): Flow<List<IssuedBook>>
    suspend fun getIssuedBookById(id: Long): IssuedBook?
    suspend fun insertIssuedBook(issuedBook: IssuedBook)
    suspend fun returnBook(id: Long, returnDate: String, fine: Double)
    fun getIssuedCount(): Flow<Int>
    fun getTotalFineCollected(): Flow<Double>
    fun getCurrentlyIssuedBooksByMember(memberId: Long): Flow<List<IssuedBook>>
    fun getReturnedBooksByMember(memberId: Long): Flow<List<IssuedBook>>
    fun getTotalFineByMember(memberId: Long): Flow<Double>
}
