package com.college.library.domain.usecase

import androidx.room.withTransaction
import com.college.library.data.db.LibraryDatabase
import com.college.library.data.model.IssuedBook
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class IssueBookUseCase @Inject constructor(
    private val database: LibraryDatabase,
    private val application: android.app.Application
) {
    suspend operator fun invoke(bookId: Long, memberId: Long, customIssueDate: String? = null, customDueDate: String? = null): Result<Unit> {
        return try {
            database.withTransaction {
                val bookDao = database.bookDao()
                val memberDao = database.memberDao()
                val issuedBookDao = database.issuedBookDao()

                val book = bookDao.getBookById(bookId)
                    ?: return@withTransaction Result.failure(Exception("Book not found"))
                
                val member = memberDao.getMemberById(memberId)
                    ?: return@withTransaction Result.failure(Exception("Member not found"))

                if (book.status != "Available") {
                    return@withTransaction Result.failure(Exception("Book is not available for issue"))
                }

                val prefs = application.getSharedPreferences("library_settings", android.content.Context.MODE_PRIVATE)
                val maxBooks = prefs.getInt("max_books", 3)
                val borrowDuration = prefs.getInt("borrow_duration", 14).toLong()

                if (member.booksIssued >= maxBooks) {
                    return@withTransaction Result.failure(Exception("Member has already issued maximum allowed books ($maxBooks)"))
                }

                // 1. Set book status to "Issued"
                bookDao.updateBook(book.copy(status = "Issued"))

                // 2. Increment member's booksIssued count
                memberDao.updateMember(member.copy(booksIssued = member.booksIssued + 1))

                // 3. Create IssuedBook record with configured due date
                val today = LocalDate.now()
                val dueDate = today.plusDays(borrowDuration)
                val formatter = DateTimeFormatter.ISO_LOCAL_DATE
                val issueDateStr = customIssueDate ?: today.format(formatter)
                val dueDateStr = customDueDate ?: dueDate.format(formatter)

                val issuedBook = IssuedBook(
                    bookId = book.id,
                    bookTitle = book.title,
                    bookIsbn = book.isbn,
                    memberId = member.id,
                    memberName = member.name,
                    memberMemberId = member.memberId,
                    issueDate = issueDateStr,
                    dueDate = dueDateStr,
                    status = "Issued"
                )
                issuedBookDao.insertIssuedBook(issuedBook)

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
