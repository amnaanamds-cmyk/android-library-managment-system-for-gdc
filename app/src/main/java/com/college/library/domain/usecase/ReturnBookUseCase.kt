package com.college.library.domain.usecase

import com.college.library.data.db.LibraryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ReturnBookUseCase @Inject constructor(
    private val database: LibraryDatabase,
    private val calculateFineUseCase: CalculateFineUseCase
) {
    suspend operator fun invoke(issueId: Long): Result<Double> = withContext(Dispatchers.IO) {
        try {
            database.transactionWithResult {
                val bookQueries = database.bookQueriesQueries
                val memberQueries = database.memberQueriesQueries
                val issuedBookQueries = database.issuedBookQueriesQueries

                val issuedBookRow = issuedBookQueries.getIssuedBookById(issueId).executeAsOneOrNull()
                    ?: return@transactionWithResult Result.failure(Exception("Issued book record not found"))

                if (issuedBookRow.status == "Returned") {
                    return@transactionWithResult Result.failure(Exception("Book is already returned"))
                }

                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                // 1. Calculate fine
                val fine = calculateFineUseCase.calculateFine(issuedBookRow.dueDate)

                // 2. Set IssuedBook status to "Returned", record return date + fine
                issuedBookQueries.returnBook(
                    id = issueId,
                    returnDate = today,
                    fine = fine,
                    lastUpdated = System.currentTimeMillis()
                )

                // 3. Set book status back to "Available"
                bookQueries.updateBookStatus(status = "Available", id = issuedBookRow.bookId, lastUpdated = System.currentTimeMillis())

                // 4. Decrement member's booksIssued count
                val memberRow = memberQueries.getMemberById(issuedBookRow.memberId).executeAsOneOrNull()
                if (memberRow != null && memberRow.booksIssued > 0) {
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
                        booksIssued = memberRow.booksIssued - 1,
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
                }

                // Return the fine amount
                Result.success(fine)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
