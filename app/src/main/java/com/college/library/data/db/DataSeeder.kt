package com.college.library.data.db

import androidx.room.withTransaction
import com.college.library.data.model.Book
import com.college.library.data.model.Member

object DataSeeder {

    suspend fun seedBooks(database: LibraryDatabase) {
        val bookDao = database.bookDao()
        val memberDao = database.memberDao()

        val sampleBooks = listOf(
            Book(isbn = "9780132350884", accNo = "B001", title = "Clean Code", author = "Robert C. Martin", publisher = "Prentice Hall", publisherPlace = "USA", publishDate = "2008", edition = "1st", pages = 464, procurement = "Purchased", volume = "1", price = 450.0),
            Book(isbn = "9780134494166", accNo = "B002", title = "Clean Architecture", author = "Robert C. Martin", publisher = "Prentice Hall", publisherPlace = "USA", publishDate = "2017", edition = "1st", pages = 432, procurement = "Purchased", volume = "1", price = 500.0),
            Book(isbn = "9780596009205", accNo = "B003", title = "Head First Design Patterns", author = "Eric Freeman", publisher = "O'Reilly", publisherPlace = "USA", publishDate = "2004", edition = "1st", pages = 694, procurement = "Donated", volume = "1", price = 800.0),
            Book(isbn = "9780201633610", accNo = "B004", title = "Design Patterns", author = "Gamma, Helm, Johnson, Vlissides", publisher = "Addison-Wesley", publisherPlace = "USA", publishDate = "1994", edition = "1st", pages = 395, procurement = "Purchased", volume = "1", price = 600.0)
        )

        val sampleMembers = listOf(
            Member(memberId = "S101", name = "Amit Kumar", email = "amit@college.edu", phone = "9876543210", department = "Computer Science", memberType = "Student", joinDate = "2026-01-10", expiryDate = "2029-01-10", booksIssued = 0),
            Member(memberId = "F201", name = "Dr. Sharma", email = "sharma@college.edu", phone = "9123456789", department = "Physics", memberType = "Faculty", joinDate = "2025-06-15", expiryDate = "2030-06-15", booksIssued = 0)
        )

        database.withTransaction {
            sampleBooks.forEach { bookDao.insertBook(it) }
            sampleMembers.forEach { memberDao.insertMember(it) }
        }
    }
}
