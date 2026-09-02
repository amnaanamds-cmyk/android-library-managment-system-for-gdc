package com.college.library.domain.usecase

import com.college.library.data.model.LibrarySettings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Fine owed on a loan that is past its due date.
 *
 * Policy comes from the institution's LibrarySettings document, mirrored into
 * SharedPreferences by SettingsViewModel so this stays synchronous and works
 * offline. The arithmetic itself lives in LibrarySettings.fineFor(), shared
 * with the desktop and web implementations, so the same late return produces
 * the same fine on every platform — it previously did not, because each
 * platform kept its own unsynced rate.
 */
class CalculateFineUseCase @Inject constructor(
    private val application: android.app.Application
) {

    operator fun invoke(dueDateString: String): Double {
        return calculateFine(dueDateString)
    }

    fun calculateFine(dueDateString: String, today: LocalDate = LocalDate.now()): Double {
        return try {
            val dueDate = LocalDate.parse(dueDateString, DateTimeFormatter.ISO_LOCAL_DATE)
            val daysOverdue = ChronoUnit.DAYS.between(dueDate, today)
            if (daysOverdue <= 0) return 0.0
            currentSettings().fineFor(daysOverdue)
        } catch (e: Exception) {
            // An unparseable due date must not block a return.
            0.0
        }
    }

    /** Policy as last synced from the institution's settings document. */
    fun currentSettings(): LibrarySettings {
        val prefs = application.getSharedPreferences(
            "library_settings",
            android.content.Context.MODE_PRIVATE,
        )
        return LibrarySettings(
            fineRatePerDay = prefs.getFloat(
                "fine_per_day",
                LibrarySettings.DEFAULT_FINE_RATE.toFloat(),
            ).toDouble(),
            borrowDurationDays = prefs.getInt(
                "borrow_duration",
                LibrarySettings.DEFAULT_BORROW_DAYS,
            ),
            maxBooksPerMember = prefs.getInt(
                "max_books",
                LibrarySettings.DEFAULT_MAX_BOOKS,
            ),
            fineGraceDays = prefs.getInt("fine_grace_days", 0),
            maxFinePerLoan = prefs.getFloat("max_fine_per_loan", 0f).toDouble(),
            currencySymbol = prefs.getString("currency_symbol", "Rs") ?: "Rs",
        )
    }
}
