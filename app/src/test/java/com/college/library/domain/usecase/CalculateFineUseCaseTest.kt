package com.college.library.domain.usecase

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CalculateFineUseCaseTest {

    private lateinit var calculateFineUseCase: CalculateFineUseCase

    @Before
    fun setUp() {
        calculateFineUseCase = CalculateFineUseCase()
        mockkStatic(LocalDate::class)
        every { LocalDate.now() } returns LocalDate.of(2026, 5, 15)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test 0 days overdue`() {
        val fine = calculateFineUseCase("2026-05-15")
        assertEquals(0.0, fine, 0.0)
        
        val fineFuture = calculateFineUseCase("2026-05-20")
        assertEquals(0.0, fineFuture, 0.0)
    }

    @Test
    fun `test 5 days overdue`() {
        val fine = calculateFineUseCase("2026-05-10")
        assertEquals(5.0, fine, 0.0)
    }

    @Test
    fun `test 14 days overdue`() {
        val fine = calculateFineUseCase("2026-05-01")
        assertEquals(14.0, fine, 0.0)
    }

    @Test
    fun `test 30 days overdue`() {
        val fine = calculateFineUseCase("2026-04-15")
        assertEquals(30.0, fine, 0.0)
    }
}
