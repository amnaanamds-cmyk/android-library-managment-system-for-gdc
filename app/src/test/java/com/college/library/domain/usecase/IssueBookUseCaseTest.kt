package com.college.library.domain.usecase

import androidx.room.withTransaction
import com.college.library.data.db.BookDao
import com.college.library.data.db.IssuedBookDao
import com.college.library.data.db.LibraryDatabase
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Book
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

class IssueBookUseCaseTest {

    private lateinit var useCase: IssueBookUseCase
    private val database: LibraryDatabase = mockk()
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

        useCase = IssueBookUseCase(database)
        
        mockkStatic(LocalDate::class)
        every { LocalDate.now() } returns LocalDate.of(2026, 5, 15)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `issueBook success`() = runTest {
        val book = Book(id = 1L, title = "Test", isbn = "123", status = "Available", accNo = "1", author = "A", publisher = "P", publisherPlace = "P", publishDate = "D", edition = "1", pages = 100, procurement = "P", volume = "1", price = 10.0)
        val member = Member(id = 1L, memberId = "M1", name = "John", email = "j@j.com", phone = "123", department = "CS", memberType = "Student", joinDate = "2026-01-01", expiryDate = "2027-01-01", booksIssued = 0)
        
        coEvery { bookDao.getBookById(1L) } returns book
        coEvery { memberDao.getMemberById(1L) } returns member

        val result = useCase(1L, 1L)
        
        assertTrue(result.isSuccess)
        coVerify { bookDao.updateBook(match { it.status == "Issued" }) }
        coVerify { memberDao.updateMember(match { it.booksIssued == 1 }) }
        coVerify { issuedBookDao.insertIssuedBook(any()) }
    }

    @Test
    fun `issueBook book already issued`() = runTest {
        val book = Book(id = 1L, title = "Test", isbn = "123", status = "Issued", accNo = "1", author = "A", publisher = "P", publisherPlace = "P", publishDate = "D", edition = "1", pages = 100, procurement = "P", volume = "1", price = 10.0)
        val member = Member(id = 1L, memberId = "M1", name = "John", email = "j@j.com", phone = "123", department = "CS", memberType = "Student", joinDate = "2026-01-01", expiryDate = "2027-01-01", booksIssued = 0)
        
        coEvery { bookDao.getBookById(1L) } returns book
        coEvery { memberDao.getMemberById(1L) } returns member

        val result = useCase(1L, 1L)
        
        assertTrue(result.isFailure)
        assertEquals("Book is not available for issue", result.exceptionOrNull()?.message)
    }

    @Test
    fun `issueBook member at limit`() = runTest {
        val book = Book(id = 1L, title = "Test", isbn = "123", status = "Available", accNo = "1", author = "A", publisher = "P", publisherPlace = "P", publishDate = "D", edition = "1", pages = 100, procurement = "P", volume = "1", price = 10.0)
        val member = Member(id = 1L, memberId = "M1", name = "John", email = "j@j.com", phone = "123", department = "CS", memberType = "Student", joinDate = "2026-01-01", expiryDate = "2027-01-01", booksIssued = 3)
        
        coEvery { bookDao.getBookById(1L) } returns book
        coEvery { memberDao.getMemberById(1L) } returns member

        val result = useCase(1L, 1L)
        
        assertTrue(result.isFailure)
        assertEquals("Member has already issued maximum allowed books (3)", result.exceptionOrNull()?.message)
    }
}
