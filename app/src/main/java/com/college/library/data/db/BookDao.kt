package com.college.library.data.db

import com.college.library.data.model.Book
import kotlinx.coroutines.flow.Flow

interface BookDao {
    fun getAllBooks(): Flow<List<Book>>
    suspend fun getAllBooksStatic(): List<Book>
    fun getAvailableBooks(): Flow<List<Book>>
    fun searchBooks(query: String): Flow<List<Book>>
    suspend fun getBookById(id: Long): Book?
    suspend fun insertBook(book: Book)
    suspend fun updateBook(book: Book)
    suspend fun deleteBook(book: Book)
    suspend fun updateBookStatus(id: Long, status: String)
    fun getTotalCount(): Flow<Int>
    fun getAvailableCount(): Flow<Int>
    fun getCategories(): Flow<List<String>>
    fun getBooksByCategory(category: String): Flow<List<Book>>
}
