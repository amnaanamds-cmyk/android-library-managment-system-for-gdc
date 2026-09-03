package com.college.library.domain.usecase

import com.college.library.data.db.LibraryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class IssueBookUseCase @Inject constructor(
    private val database: LibraryDatabase,
    private val application: android.app.Application
) {
    suspend operator fun invoke(bookId: Long, memberId: Long, customIssueDate: String? = null, customDueDate: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            database.transactionWithResult {
                val bookQueries = database.bookQueriesQueries
                val memberQueries = database.memberQueriesQueries
                val issuedBookQueries = database.issuedBookQueriesQueries

                val bookRow = bookQueries.getBookById(bookId).executeAsOneOrNull()
                    ?: return@transactionWithResult Result.failure(Exception("Book not found"))
                
                val memberRow = memberQueries.getMemberById(memberId).executeAsOneOrNull()
                    ?: return@transactionWithResult Result.failure(Exception("Member not found"))

                if (bookRow.status != "Available") {
                    return@transactionWithResult Result.failure(Exception("Book is not available for issue"))
                }

                val prefs = application.getSharedPreferences("library_settings", android.content.Context.MODE_PRIVATE)
                val maxBooks = prefs.getInt("max_books", 3)
                val borrowDuration = prefs.getInt("borrow_duration", 14).toLong()

                if (memberRow.booksIssued >= maxBooks.toLong()) {
                    return@transactionWithResult Result.failure(Exception("Member has already issued maximum allowed books ($maxBooks)"))
                }

                // 1. Set book status to "Issued"
                bookQueries.updateBookStatus(status = "Issued", id = bookRow.id, lastUpdated = System.currentTimeMillis())

                // 2. Increment member's booksIssued count
                memberQueries.updateMember(
                    syncId = memberRow.syncId,
                    memberId = memberRow.memberId,
                    name = memberRow.name,
                    email = memberRow.email,
                    phone = memberRow.phone,
                    department = memberRow.department,
                    memberType = memberRow.memberType,
                    joinDate = memberRow.joinDate,
                    expiryDate = memberRow.expiryDate,
                    booksIssued = memberRow.booksIssued + 1,
                    fatherName = memberRow.fatherName,
                    className = memberRow.className,
                    classNo = memberRow.classNo,
                    address = memberRow.address,
                    photoUri = memberRow.photoUri,
                    designation = memberRow.designation,
                    bps = memberRow.bps,
                    pin = memberRow.pin,
                    biometricHash = memberRow.biometricHash,
                    biometricEnrolDate = memberRow.biometricEnrolDate,
                    biometricLastVerified = memberRow.biometricLastVerified,
                    collegeId = memberRow.collegeId,
                    syncStatus = memberRow.syncStatus,
                    lastUpdated = System.currentTimeMillis(),
                    deleted = memberRow.deleted,
                    id = memberRow.id
                )

                // 3. Create IssuedBook record
                val today = LocalDate.now()
                val dueDate = today.plusDays(borrowDuration)
                val formatter = DateTimeFormatter.ISO_LOCAL_DATE
                val issueDateStr = customIssueDate ?: today.format(formatter)
                val dueDateStr = customDueDate ?: dueDate.format(formatter)

                issuedBookQueries.insertIssuedBook(
                    syncId = UUID.randomUUID().toString(),
                    bookId = bookRow.id,
                    bookTitle = bookRow.title,
                    bookIsbn = bookRow.isbn,
                    memberId = memberRow.id,
                    memberName = memberRow.name,
                    memberMemberId = memberRow.memberId,
                    issueDate = issueDateStr,
                    dueDate = dueDateStr,
                    returnDate = null,
                    fine = 0.0,
                    status = "Issued",
                    lastUpdated = System.currentTimeMillis(),
                    deleted = false,
                    syncStatus = "pending",
                    collegeId = memberRow.collegeId
                )

                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
