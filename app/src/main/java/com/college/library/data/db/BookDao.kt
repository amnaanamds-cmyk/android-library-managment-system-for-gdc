package com.college.library.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.college.library.data.model.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY id DESC")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books")
    suspend fun getAllBooksStatic(): List<Book>

    @Query("SELECT * FROM books WHERE status = 'Available' ORDER BY id DESC")
    fun getAvailableBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' OR isbn LIKE '%' || :query || '%' OR publisher LIKE '%' || :query || '%' ORDER BY id DESC")
    fun searchBooks(query: String): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: Long): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book)

    @Update
    suspend fun updateBook(book: Book)

    @Delete
    suspend fun deleteBook(book: Book)

    @Query("UPDATE books SET status = :status WHERE id = :id")
    suspend fun updateBookStatus(id: Long, status: String)

    @Query("SELECT COUNT(*) FROM books")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM books WHERE status = 'Available'")
    fun getAvailableCount(): Flow<Int>
}
