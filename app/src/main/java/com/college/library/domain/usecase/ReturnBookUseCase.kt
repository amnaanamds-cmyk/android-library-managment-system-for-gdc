package com.college.library.domain.usecase

import androidx.room.withTransaction
import com.college.library.data.db.LibraryDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ReturnBookUseCase @Inject constructor(
    private val database: LibraryDatabase,
    private val calculateFineUseCase: CalculateFineUseCase
) {
    suspend operator fun invoke(issueId: Long): Result<Double> {
        return try {
            database.withTransaction {
                val bookDao = database.bookDao()
                val memberDao = database.memberDao()
                val issuedBookDao = database.issuedBookDao()

                val issuedBook = issuedBookDao.getIssuedBookById(issueId)
                    ?: return@withTransaction Result.failure(Exception("Issued book record not found"))

                if (issuedBook.status == "Returned") {
                    return@withTransaction Result.failure(Exception("Book is already returned"))
                }

                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                // 1. Calculate fine
                val fine = calculateFineUseCase.calculateFine(issuedBook.dueDate)

                // 2. Set IssuedBook status to "Returned", record return date + fine
                issuedBookDao.returnBook(
                    id = issueId,
                    returnDate = today,
                    fine = fine
                )

                // 3. Set book status back to "Available"
                val book = bookDao.getBookById(issuedBook.bookId)
                if (book != null) {
                    bookDao.updateBookStatus(book.id, "Available")
                }

                // 4. Decrement member's booksIssued count
                val member = memberDao.getMemberById(issuedBook.memberId)
                if (member != null && member.booksIssued > 0) {
                    memberDao.updateMember(member.copy(booksIssued = member.booksIssued - 1))
                }

                // Return the fine amount
                Result.success(fine)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
