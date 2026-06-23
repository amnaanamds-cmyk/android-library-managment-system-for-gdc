package com.college.library.domain.usecase

import androidx.room.withTransaction
import com.college.library.data.db.BookDao
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.LibraryDatabase
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Book
import com.college.library.data.model.IssuedBook
import com.college.library.data.model.Member
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ReturnBookUseCaseTest {

    private lateinit var useCase: ReturnBookUseCase
    private val database: LibraryDatabase = mockk()
    private val calculateFineUseCase: CalculateFineUseCase = mockk()
    private val bookDao: BookDao = mockk(relaxed = true)
    private val memberDao: MemberDao = mockk(relaxed = true)
    private val issuedBookDao: IssuedBookDao = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any>()) } coAnswers {
            val block = secondArg<suspend () -> Any>()
            block.invoke()
        }
        every { database.bookDao() } returns bookDao
        every { database.memberDao() } returns memberDao
        every { database.issuedBookDao() } returns issuedBookDao

        useCase = ReturnBookUseCase(database, calculateFineUseCase)
        
        mockkStatic(LocalDate::class)
        every { LocalDate.now() } returns LocalDate.of(2026, 5, 15)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `returnBook successful return with no fine`() = runTest {
        val issuedBook = IssuedBook(id = 1L, bookId = 1L, bookTitle = "T", bookIsbn = "1", memberId = 1L, memberName = "M", memberMemberId = "M1", issueDate = "2026-05-01", dueDate = "2026-05-20", status = "Issued")
        val book = Book(id = 1L, title = "Test", isbn = "123", status = "Issued", accNo = "1", author = "A", publisher = "P", publisherPlace = "P", publishDate = "D", edition = "1", pages = 100, procurement = "P", volume = "1", price = 10.0)
        val member = Member(id = 1L, memberId = "M1", name = "John", email = "j@j.com", phone = "123", department = "CS", memberType = "Student", joinDate = "2026-01-01", expiryDate = "2027-01-01", booksIssued = 1)
        
        coEvery { issuedBookDao.getIssuedBookById(1L) } returns issuedBook
        coEvery { bookDao.getBookById(1L) } returns book
        coEvery { memberDao.getMemberById(1L) } returns member
        every { calculateFineUseCase.calculateFine(any()) } returns 0.0

        val result = useCase(1L)
        
        assertTrue(result.isSuccess)
        assertEquals(0.0, result.getOrNull())
        
        coVerify { issuedBookDao.returnBook(1L, "2026-05-15", 0.0) }
        coVerify { bookDao.updateBookStatus(1L, "Available") }
        coVerify { memberDao.updateMember(match { it.booksIssued == 0 }) }
    }

    @Test
    fun `returnBook successful return with fine`() = runTest {
        val issuedBook = IssuedBook(id = 1L, bookId = 1L, bookTitle = "T", bookIsbn = "1", memberId = 1L, memberName = "M", memberMemberId = "M1", issueDate = "2026-04-01", dueDate = "2026-04-15", status = "Issued")
        val book = Book(id = 1L, title = "Test", isbn = "123", status = "Issued", accNo = "1", author = "A", publisher = "P", publisherPlace = "P", publishDate = "D", edition = "1", pages = 100, procurement = "P", volume = "1", price = 10.0)
        val member = Member(id = 1L, memberId = "M1", name = "John", email = "j@j.com", phone = "123", department = "CS", memberType = "Student", joinDate = "2026-01-01", expiryDate = "2027-01-01", booksIssued = 1)
        
        coEvery { issuedBookDao.getIssuedBookById(1L) } returns issuedBook
        coEvery { bookDao.getBookById(1L) } returns book
        coEvery { memberDao.getMemberById(1L) } returns member
        every { calculateFineUseCase.calculateFine(any()) } returns 30.0

        val result = useCase(1L)
        
        assertTrue(result.isSuccess)
        assertEquals(30.0, result.getOrNull())
        
        coVerify { issuedBookDao.returnBook(1L, "2026-05-15", 30.0) }
        coVerify { bookDao.updateBookStatus(1L, "Available") }
        coVerify { memberDao.updateMember(match { it.booksIssued == 0 }) }
    }
}
