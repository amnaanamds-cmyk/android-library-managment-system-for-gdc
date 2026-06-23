package com.college.library.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.college.library.MainActivity
import com.college.library.R
import com.college.library.data.model.IssuedBook

/**
 * Handles all local notification logic for the library app.
 * Two channels:
 *   • OVERDUE_CHANNEL  – books already past due date (high importance / red)
 *   • DUE_SOON_CHANNEL – books due within the next 2 days (default importance / amber)
 */
object OverdueNotificationHelper {

    private const val OVERDUE_CHANNEL_ID   = "overdue_books_alerts"
    private const val DUE_SOON_CHANNEL_ID  = "due_soon_alerts"
    private const val OVERDUE_CHANNEL_NAME = "Overdue Books"
    private const val DUE_SOON_CHANNEL_NAME = "Due Soon"

    private const val OVERDUE_SUMMARY_ID = 1001
    private const val DUE_SOON_SUMMARY_ID = 1002
    private const val PER_BOOK_BASE_ID   = 2000   // per-book IDs start here

    // ── Channel setup ─────────────────────────────────────────────────────────
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(OVERDUE_CHANNEL_ID, OVERDUE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Books that have already passed their due return date"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(DUE_SOON_CHANNEL_ID, DUE_SOON_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Books due within the next 2 days"
            }
        )
    }

    // ── Main entry point called by the Worker ─────────────────────────────────
    fun notify(context: Context, overdueBooks: List<IssuedBook>, dueSoonBooks: List<IssuedBook>) {
        createChannels(context)

        if (overdueBooks.isNotEmpty())  showOverdueNotifications(context, overdueBooks)
        if (dueSoonBooks.isNotEmpty())  showDueSoonNotifications(context, dueSoonBooks)
    }

    // ── Legacy compat method (called from DashboardViewModel) ─────────────────
    fun showNotification(context: Context, overdueBooks: List<IssuedBook>) {
        if (overdueBooks.isEmpty()) return
        notify(context, overdueBooks, emptyList())
    }

    // ── Overdue notifications (one summary + one per book) ────────────────────
    private fun showOverdueNotifications(context: Context, overdueBooks: List<IssuedBook>) {
        val nm = NotificationManagerCompat.from(context)
        val pendingIntent = mainActivityIntent(context)

        // Per-book notifications (inbox style)
        overdueBooks.forEachIndexed { index, book ->
            val notif = NotificationCompat.Builder(context, OVERDUE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("⚠ Overdue: ${book.bookTitle}")
                .setContentText("Issued to ${book.memberName} • due ${book.dueDate}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setGroup(OVERDUE_CHANNEL_ID)
                .build()
            try { nm.notify(PER_BOOK_BASE_ID + index, notif) } catch (_: SecurityException) {}
        }

        // Group summary
        val summary = NotificationCompat.Builder(context, OVERDUE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("${overdueBooks.size} Overdue Books")
            .setContentText("Tap to view overdue transactions")
            .setStyle(NotificationCompat.InboxStyle().also { inbox ->
                overdueBooks.take(5).forEach { inbox.addLine("• ${it.bookTitle} (${it.memberName})") }
                if (overdueBooks.size > 5) inbox.setSummaryText("+${overdueBooks.size - 5} more")
            })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(OVERDUE_CHANNEL_ID)
            .setGroupSummary(true)
            .build()
        try { nm.notify(OVERDUE_SUMMARY_ID, summary) } catch (_: SecurityException) {}
    }

    // ── Due-soon notifications (within 2 days) ────────────────────────────────
    private fun showDueSoonNotifications(context: Context, dueSoonBooks: List<IssuedBook>) {
        if (dueSoonBooks.isEmpty()) return
        val nm = NotificationManagerCompat.from(context)
        val pendingIntent = mainActivityIntent(context)

        val summary = NotificationCompat.Builder(context, DUE_SOON_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📚 ${dueSoonBooks.size} Book${if (dueSoonBooks.size > 1) "s" else ""} Due Soon")
            .setContentText("Books due in the next 2 days")
            .setStyle(NotificationCompat.InboxStyle().also { inbox ->
                dueSoonBooks.take(5).forEach { inbox.addLine("• ${it.bookTitle} — due ${it.dueDate}") }
            })
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try { nm.notify(DUE_SOON_SUMMARY_ID, summary) } catch (_: SecurityException) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun mainActivityIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun resetSession() { /* no-op: session guard removed – Worker handles scheduling */ }
}
