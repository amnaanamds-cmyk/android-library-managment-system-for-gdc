package com.college.library.notifications

enum class NotificationType {
    OVERDUE,
    RESERVATION,
    SYSTEM,
    BACKUP
}

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val type: NotificationType,
    val isRead: Boolean = false
)
