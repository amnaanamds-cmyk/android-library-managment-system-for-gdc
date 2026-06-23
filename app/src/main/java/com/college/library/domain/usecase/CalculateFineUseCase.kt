package com.college.library.domain.usecase

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class CalculateFineUseCase @Inject constructor(
    private val application: android.app.Application
) {
    
    operator fun invoke(dueDateString: String): Double {
        return calculateFine(dueDateString)
    }

    fun calculateFine(dueDateString: String): Double {
        return try {
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE
            val dueDate = LocalDate.parse(dueDateString, formatter)
            val today = LocalDate.now()

            val daysOverdue = ChronoUnit.DAYS.between(dueDate, today)
            
            if (daysOverdue > 0) {
                val prefs = application.getSharedPreferences("library_settings", android.content.Context.MODE_PRIVATE)
                val finePerDay = prefs.getFloat("fine_per_day", 1.0f).toDouble()
                daysOverdue * finePerDay 
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
}
