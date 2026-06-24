package com.college.library.notifications

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class NotificationStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "library_notifications"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val MAX_NOTIFICATIONS = 50
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun addNotification(item: NotificationItem) {
        val current = getAll().toMutableList()
        current.add(0, item)
        if (current.size > MAX_NOTIFICATIONS) {
            val pruned = current.take(MAX_NOTIFICATIONS)
            saveAll(pruned)
        } else {
            saveAll(current)
        }
    }

    fun getAll(): List<NotificationItem> {
        val json = prefs.getString(KEY_NOTIFICATIONS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                NotificationItem(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    message = obj.getString("message"),
                    timestamp = obj.getLong("timestamp"),
                    type = NotificationType.valueOf(obj.getString("type")),
                    isRead = obj.getBoolean("isRead")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun markRead(id: String) {
        val updated = getAll().map { if (it.id == id) it.copy(isRead = true) else it }
        saveAll(updated)
    }

    fun markAllRead() {
        val updated = getAll().map { it.copy(isRead = true) }
        saveAll(updated)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_NOTIFICATIONS).apply()
    }

    fun getUnreadCount(): Int {
        return getAll().count { !it.isRead }
    }

    fun removeNotification(id: String) {
        val updated = getAll().filter { it.id != id }
        saveAll(updated)
    }

    private fun saveAll(items: List<NotificationItem>) {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("message", item.message)
                put("timestamp", item.timestamp)
                put("type", item.type.name)
                put("isRead", item.isRead)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_NOTIFICATIONS, array.toString()).apply()
    }
}
