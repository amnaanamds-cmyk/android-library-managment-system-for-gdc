package com.college.library.domain.usecase

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.college.library.data.model.LibrarySettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CalculateFineUseCaseTest {

    private lateinit var calculateFineUseCase: CalculateFineUseCase
    private lateinit var application: Application
    private lateinit var sharedPreferences: SharedPreferences

    /** Fixed "today" so the tests do not depend on the wall clock. */
    private val today = LocalDate.of(2026, 5, 15)

    @Before
    fun setUp() {
        application = mockk()
        sharedPreferences = mockk()

        every {
            application.getSharedPreferences("library_settings", Context.MODE_PRIVATE)
        } returns sharedPreferences

        // Policy mirrored from the institution's LibrarySettings document.
        stubSettings(finePerDay = 1.0f)

        calculateFineUseCase = CalculateFineUseCase(application)
    }

    /**
     * Stub the cached policy. The keys and defaults must match
     * CalculateFineUseCase.currentSettings() exactly — MockK fails the test if
     * the production code reads a key or default this does not stub, which is
     * what keeps the two in step.
     */
    private fun stubSettings(
        finePerDay: Float,
        graceDays: Int = 0,
        maxFinePerLoan: Float = 0f,
    ) {
        every {
            sharedPreferences.getFloat("fine_per_day", LibrarySettings.DEFAULT_FINE_RATE.toFloat())
        } returns finePerDay
        every {
            sharedPreferences.getInt("borrow_duration", LibrarySettings.DEFAULT_BORROW_DAYS)
        } returns LibrarySettings.DEFAULT_BORROW_DAYS
        every {
            sharedPreferences.getInt("max_books", LibrarySettings.DEFAULT_MAX_BOOKS)
        } returns LibrarySettings.DEFAULT_MAX_BOOKS
        every { sharedPreferences.getInt("fine_grace_days", 0) } returns graceDays
        every { sharedPreferences.getFloat("max_fine_per_loan", 0f) } returns maxFinePerLoan
        every { sharedPreferences.getString("currency_symbol", "Rs") } returns "Rs"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `not overdue yields no fine`() {
        assertEquals(0.0, calculateFineUseCase.calculateFine("2026-05-15", today), 0.0)
        assertEquals(0.0, calculateFineUseCase.calculateFine("2026-05-20", today), 0.0)
    }

    @Test
    fun `fine accrues per day overdue`() {
        assertEquals(5.0, calculateFineUseCase.calculateFine("2026-05-10", today), 0.0)
        assertEquals(14.0, calculateFineUseCase.calculateFine("2026-05-01", today), 0.0)
        assertEquals(30.0, calculateFineUseCase.calculateFine("2026-04-15", today), 0.0)
    }

    @Test
    fun `fine uses the configured rate rather than a hardcoded one`() {
        stubSettings(finePerDay = 7.5f)
        // 5 days overdue at 7.50 per day. Before settings synced, Android used
        // its own local rate and disagreed with the desktop app on this number.
        assertEquals(37.5, calculateFineUseCase.calculateFine("2026-05-10", today), 0.0001)
    }

    @Test
    fun `grace days are not charged`() {
        stubSettings(finePerDay = 2.0f, graceDays = 3)
        // 3 days overdue, all within grace.
        assertEquals(0.0, calculateFineUseCase.calculateFine("2026-05-12", today), 0.0)
        // 5 days overdue, 2 chargeable.
        assertEquals(4.0, calculateFineUseCase.calculateFine("2026-05-10", today), 0.0)
    }

    @Test
    fun `fine is capped when a per-loan maximum is set`() {
        stubSettings(finePerDay = 10.0f, maxFinePerLoan = 50f)
        // 30 days would be 300 uncapped.
        assertEquals(50.0, calculateFineUseCase.calculateFine("2026-04-15", today), 0.0)
    }

    @Test
    fun `a zero cap means no cap`() {
        stubSettings(finePerDay = 10.0f, maxFinePerLoan = 0f)
        assertEquals(300.0, calculateFineUseCase.calculateFine("2026-04-15", today), 0.0)
    }

    @Test
    fun `an unparseable due date does not block a return`() {
        assertEquals(0.0, calculateFineUseCase.calculateFine("not-a-date", today), 0.0)
        assertEquals(0.0, calculateFineUseCase.calculateFine("", today), 0.0)
    }
}
