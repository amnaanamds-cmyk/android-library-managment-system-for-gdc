package com.college.library.utils

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The languages this app ships in.
 *
 * Urdu, not Hindi: this is a system for the Government Degree Colleges of
 * Khyber Pakhtunkhwa, where the national language is Urdu written in
 * Nastaliq/Arabic script and read right-to-left.
 *
 * `isRtl` drives the layout direction — an Urdu UI laid out left-to-right is
 * readable but wrong, with labels and their values on the wrong sides.
 */
enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val isRtl: Boolean,
) {
    ENGLISH("en", "English", "English", false),
    URDU("ur", "Urdu", "اردو", true),
}

@Singleton
class LanguageManager @Inject constructor(application: Application) {
    private val prefs = application.getSharedPreferences("library_settings", Context.MODE_PRIVATE)

    private val _currentLanguage = MutableStateFlow(resolveStored())
    val currentLanguage = _currentLanguage.asStateFlow()

    /**
     * Read the stored preference.
     *
     * "hi" is accepted and treated as Urdu: earlier builds shipped Hindi, and
     * anyone who had selected it would otherwise be silently reset to English
     * on upgrade. The value is rewritten to "ur" so the migration happens once.
     */
    private fun resolveStored(): AppLanguage {
        val stored = prefs.getString("app_language", AppLanguage.ENGLISH.code)
        return when (stored) {
            AppLanguage.URDU.code -> AppLanguage.URDU
            "hi" -> {
                prefs.edit().putString("app_language", AppLanguage.URDU.code).apply()
                AppLanguage.URDU
            }
            else -> AppLanguage.ENGLISH
        }
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString("app_language", language.code).apply()
        _currentLanguage.value = language
    }

    fun isUrdu(): Boolean = _currentLanguage.value == AppLanguage.URDU

    /** True when the active language should lay out right-to-left. */
    fun isRtl(): Boolean = _currentLanguage.value.isRtl
}
