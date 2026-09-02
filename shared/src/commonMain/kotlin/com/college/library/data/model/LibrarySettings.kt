package com.college.library.data.model

import kotlinx.serialization.Serializable

/**
 * Per-institution library policy, stored at
 * `/institutions/{collegeId}/settings/library_settings`.
 *
 * These values decide real money and real due dates, so all three platforms
 * must agree on them. Before this existed each platform kept its own copy and
 * they disagreed:
 *
 *   Android   SharedPreferences only, fine rate defaulting to 1.0, never
 *             uploaded or downloaded
 *   Desktop   wrote only `fineRatePerDay` to Firestore, defaulting to 5.0
 *   Web       a local useState with a hardcoded 5.0 and no save at all
 *
 * The visible symptom was that the same overdue book produced a different fine
 * depending on which device processed the return.
 *
 * Keep the field names in step with:
 *   gdc_desktop/services/settings_service.py
 *   web-app/src/lib/settings.ts
 */
@Serializable
data class LibrarySettings(
    /** Currency units charged per day a loan is overdue. */
    val fineRatePerDay: Double = DEFAULT_FINE_RATE,

    /** Days a book may be borrowed for, used to compute the due date. */
    val borrowDurationDays: Int = DEFAULT_BORROW_DAYS,

    /** Maximum concurrent loans allowed per member. */
    val maxBooksPerMember: Int = DEFAULT_MAX_BOOKS,

    /** Days a fulfilled reservation is held before it expires. */
    val reservationHoldDays: Int = DEFAULT_RESERVATION_HOLD_DAYS,

    /** Grace days after the due date before a fine starts accruing. */
    val fineGraceDays: Int = 0,

    /** Upper bound on the fine for a single loan; 0 means no cap. */
    val maxFinePerLoan: Double = 0.0,

    /** Currency symbol shown in the UI and on receipts. */
    val currencySymbol: String = "Rs",

    // Sync envelope, so the settings document participates in the same
    // last-write-wins resolution as every other record.
    val lastUpdated: Long = 0L,
    val updatedBy: String = "",
    val updatedByPlatform: String = "",
) {
    companion object {
        const val DEFAULT_FINE_RATE = 5.0
        const val DEFAULT_BORROW_DAYS = 14
        const val DEFAULT_MAX_BOOKS = 3
        const val DEFAULT_RESERVATION_HOLD_DAYS = 3

        /** Firestore document id under the tenant's `settings` collection. */
        const val DOCUMENT_ID = "library_settings"
        const val COLLECTION = "settings"
    }

    /**
     * Fine owed for a loan [daysOverdue] days late, applying the grace period
     * and the per-loan cap.
     *
     * Every platform routes fine calculation through an equivalent function so
     * that a return processed on the desktop and the same return processed on
     * Android produce the same number.
     */
    fun fineFor(daysOverdue: Long): Double {
        val chargeableDays = daysOverdue - fineGraceDays
        if (chargeableDays <= 0) return 0.0
        val fine = chargeableDays * fineRatePerDay
        return if (maxFinePerLoan > 0.0) minOf(fine, maxFinePerLoan) else fine
    }

    /** Guard against values that would break circulation if mis-entered. */
    fun sanitised(): LibrarySettings = copy(
        fineRatePerDay = fineRatePerDay.coerceAtLeast(0.0),
        borrowDurationDays = borrowDurationDays.coerceIn(1, 365),
        maxBooksPerMember = maxBooksPerMember.coerceIn(1, 100),
        reservationHoldDays = reservationHoldDays.coerceIn(0, 90),
        fineGraceDays = fineGraceDays.coerceIn(0, 90),
        maxFinePerLoan = maxFinePerLoan.coerceAtLeast(0.0),
        currencySymbol = currencySymbol.ifBlank { "Rs" },
    )
}
