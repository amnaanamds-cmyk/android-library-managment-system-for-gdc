package com.college.library.notifications

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationCenterState(
    val notifications: List<NotificationItem> = emptyList(),
    val unreadCount: Int = 0
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    application: Application
) : ViewModel() {

    private val store = NotificationStore(application)

    private val _state = MutableStateFlow(NotificationCenterState())
    val state: StateFlow<NotificationCenterState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val all = store.getAll()
            _state.value = NotificationCenterState(
                notifications = all,
                unreadCount = all.count { !it.isRead }
            )
        }
    }

    fun markRead(id: String) {
        store.markRead(id)
        refresh()
    }

    fun markAllRead() {
        store.markAllRead()
        refresh()
    }

    fun clearAll() {
        store.clearAll()
        refresh()
    }

    fun removeNotification(id: String) {
        store.removeNotification(id)
        refresh()
    }

    fun addNotification(item: NotificationItem) {
        store.addNotification(item)
        refresh()
    }
}
