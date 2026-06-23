package com.college.library.utils

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    ENGLISH("en", "English", "English"),
    HINDI("hi", "Hindi", "हिन्दी")
}

@Singleton
class LanguageManager @Inject constructor(application: Application) {
    private val prefs = application.getSharedPreferences("library_settings", Context.MODE_PRIVATE)

    private val _currentLanguage = MutableStateFlow(
        if (prefs.getString("app_language", "en") == "hi") AppLanguage.HINDI else AppLanguage.ENGLISH
    )
    val currentLanguage = _currentLanguage.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString("app_language", language.code).apply()
        _currentLanguage.value = language
    }

    fun isHindi(): Boolean = _currentLanguage.value == AppLanguage.HINDI
}
