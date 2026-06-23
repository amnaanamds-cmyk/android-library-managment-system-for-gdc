package com.college.library.utils

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.college.library.data.db.IssuedBookDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker that:
 *  1. Fetches all currently-issued books
 *  2. Separates them into overdue and due-within-2-days buckets
 *  3. Fires rich local notifications via OverdueNotificationHelper
 *
 * Schedule: runs once every 12 hours (constrained to network-not-required).
 */
@HiltWorker
class OverdueCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val issuedBookDao: IssuedBookDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val today    = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val inTwoDaysStr = today.plusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE)

        val allIssued = issuedBookDao.getCurrentlyIssuedBooks().first()

        val overdueBooks  = allIssued.filter { it.dueDate < todayStr }
        val dueSoonBooks  = allIssued.filter { it.dueDate in todayStr..inTwoDaysStr }

        OverdueNotificationHelper.notify(context, overdueBooks, dueSoonBooks)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "overdue_check_periodic"

        /** Call once from Application.onCreate() to schedule the periodic worker. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<OverdueCheckWorker>(
                repeatInterval = 12,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,   // don't restart if already scheduled
                request
            )
        }

        /** Run an immediate one-shot check (e.g. on app open). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<OverdueCheckWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
