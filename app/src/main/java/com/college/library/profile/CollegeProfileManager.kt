package com.college.library.profile

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class CollegeProfileManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "college_profile_prefs"
        private const val KEY_PROFILE_JSON = "profile_json"

        @Volatile
        private var instance: CollegeProfileManager? = null

        fun getInstance(context: Context): CollegeProfileManager {
            return instance ?: synchronized(this) {
                instance ?: CollegeProfileManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getProfile(): CollegeProfile {
        val json = prefs.getString(KEY_PROFILE_JSON, null) ?: return CollegeProfile()
        return try {
            val obj = JSONObject(json)
            CollegeProfile(
                collegeName = obj.optString("collegeName", "GDC Library"),
                collegeFullName = obj.optString("collegeFullName", "Government Degree College"),
                tagline = obj.optString("tagline", "Knowledge is Power"),
                libraryName = obj.optString("libraryName", "College Library"),
                address = obj.optString("address", ""),
                phone = obj.optString("phone", ""),
                email = obj.optString("email", ""),
                website = obj.optString("website", ""),
                logoUri = obj.optString("logoUri", "").ifEmpty { null },
                principalName = obj.optString("principalName", ""),
                librarianName = obj.optString("librarianName", ""),
                establishedYear = obj.optString("establishedYear", ""),
                currency = obj.optString("currency", "Rs."),
                fineUnit = obj.optString("fineUnit", "per day"),
                memberIdPrefix = obj.optString("memberIdPrefix", "STU"),
                isSetupComplete = obj.optBoolean("isSetupComplete", false)
            )
        } catch (e: Exception) {
            CollegeProfile()
        }
    }

    fun saveProfile(profile: CollegeProfile) {
        val obj = JSONObject().apply {
            put("collegeName", profile.collegeName)
            put("collegeFullName", profile.collegeFullName)
            put("tagline", profile.tagline)
            put("libraryName", profile.libraryName)
            put("address", profile.address)
            put("phone", profile.phone)
            put("email", profile.email)
            put("website", profile.website)
            put("logoUri", profile.logoUri ?: "")
            put("principalName", profile.principalName)
            put("librarianName", profile.librarianName)
            put("establishedYear", profile.establishedYear)
            put("currency", profile.currency)
            put("fineUnit", profile.fineUnit)
            put("memberIdPrefix", profile.memberIdPrefix)
            put("isSetupComplete", profile.isSetupComplete)
        }
        prefs.edit().putString(KEY_PROFILE_JSON, obj.toString()).apply()
    }

    fun isSetupComplete(): Boolean {
        return getProfile().isSetupComplete
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
