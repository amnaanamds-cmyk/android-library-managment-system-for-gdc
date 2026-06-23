package com.college.library.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class UserRole {
    ADMIN, LIBRARIAN, GUEST
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val ROLE_KEY = "current_role"

    private val _currentRole = MutableStateFlow(loadRole())
    val currentRole = _currentRole.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(loadRole() != UserRole.GUEST)
    val isAuthenticated = _isAuthenticated.asStateFlow()

    private fun loadRole(): UserRole {
        val roleStr = prefs.getString(ROLE_KEY, UserRole.GUEST.name) ?: UserRole.GUEST.name
        return try { UserRole.valueOf(roleStr) } catch (e: Exception) { UserRole.GUEST }
    }

    fun login(pin: String): Boolean {
        val role = when (pin) {
            "1234" -> UserRole.ADMIN // Admin PIN
            "0000" -> UserRole.LIBRARIAN // Librarian PIN
            else -> null
        }
        
        if (role != null) {
            prefs.edit().putString(ROLE_KEY, role.name).apply()
            _currentRole.value = role
            _isAuthenticated.value = true
            return true
        }
        return false
    }

    fun logout() {
        prefs.edit().putString(ROLE_KEY, UserRole.GUEST.name).apply()
        _currentRole.value = UserRole.GUEST
        _isAuthenticated.value = false
    }
    
    // Authorization Helpers
    fun canEditBooks() = _currentRole.value == UserRole.ADMIN || _currentRole.value == UserRole.LIBRARIAN
    fun canEditMembers() = _currentRole.value == UserRole.ADMIN || _currentRole.value == UserRole.LIBRARIAN
    fun canAccessSettings() = _currentRole.value == UserRole.ADMIN
}
