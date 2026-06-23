package com.college.library.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.college.library.data.model.IssuedBook
import kotlinx.coroutines.flow.Flow

@Dao
interface IssuedBookDao {
    @Query("SELECT * FROM issued_books WHERE status = 'Issued' ORDER BY issueDate DESC")
    fun getCurrentlyIssuedBooks(): Flow<List<IssuedBook>>

    @Query("SELECT * FROM issued_books WHERE status = 'Issued' AND dueDate < :today ORDER BY dueDate ASC")
    fun getOverdueBooks(today: String): Flow<List<IssuedBook>>

    @Query("SELECT * FROM issued_books ORDER BY issueDate DESC")
    fun getAllTransactions(): Flow<List<IssuedBook>>

    @Query("SELECT * FROM issued_books WHERE id = :id LIMIT 1")
    suspend fun getIssuedBookById(id: Long): IssuedBook?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssuedBook(issuedBook: IssuedBook)

    @Query("UPDATE issued_books SET returnDate = :returnDate, fine = :fine, status = 'Returned' WHERE id = :id")
    suspend fun returnBook(id: Long, returnDate: String, fine: Double)

    @Query("SELECT COUNT(*) FROM issued_books WHERE status = 'Issued'")
    fun getIssuedCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(fine), 0.0) FROM issued_books")
    fun getTotalFineCollected(): Flow<Double>

    @Query("SELECT * FROM issued_books WHERE memberId = :memberId AND status = 'Issued' ORDER BY issueDate DESC")
    fun getCurrentlyIssuedBooksByMember(memberId: Long): Flow<List<IssuedBook>>

    @Query("SELECT * FROM issued_books WHERE memberId = :memberId AND status = 'Returned' ORDER BY returnDate DESC")
    fun getReturnedBooksByMember(memberId: Long): Flow<List<IssuedBook>>

    @Query("SELECT COALESCE(SUM(fine), 0.0) FROM issued_books WHERE memberId = :memberId AND status = 'Returned'")
    fun getTotalFineByMember(memberId: Long): Flow<Double>
}
