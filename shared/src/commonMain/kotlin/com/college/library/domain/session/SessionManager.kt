package com.college.library.domain.session

import com.russhwolf.settings.Settings

class SessionManager(private val settings: Settings) {
    
    var token: String?
        get() = settings.getStringOrNull("auth_token")
        set(value) {
            if (value != null) settings.putString("auth_token", value)
            else settings.remove("auth_token")
        }

    var institutionId: String?
        get() = settings.getStringOrNull("institution_id")
        set(value) {
            if (value != null) settings.putString("institution_id", value)
            else settings.remove("institution_id")
        }

    var role: String?
        get() = settings.getStringOrNull("user_role")
        set(value) {
            if (value != null) settings.putString("user_role", value)
            else settings.remove("user_role")
        }

    var librarianName: String?
        get() = settings.getStringOrNull("librarian_name")
        set(value) {
            if (value != null) settings.putString("librarian_name", value)
            else settings.remove("librarian_name")
        }

    var isRememberMe: Boolean
        get() = settings.getBoolean("remember_me", false)
        set(value) = settings.putBoolean("remember_me", value)

    var isDarkMode: Boolean
        get() = settings.getBoolean("desktop_dark_mode", false)
        set(value) = settings.putBoolean("desktop_dark_mode", value)

    fun isLoggedIn(): Boolean {
        return !token.isNullOrEmpty() && !institutionId.isNullOrEmpty()
    }

    fun clearSession() {
        token = null
        institutionId = null
        role = null
        librarianName = null
        isRememberMe = false
    }
}
