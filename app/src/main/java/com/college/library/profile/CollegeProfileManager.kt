package com.college.library.profile

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class CollegeProfileManager(context: Context) {

    companion object {
        private const val TAG = "CollegeProfileManager"
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

    suspend fun syncFromCloud(collegeId: String) {
        if (collegeId.isBlank()) return
        try {
            val db = FirebaseFirestore.getInstance()
            val doc = db.collection("institutions").document(collegeId).get().await()
            if (doc.exists()) {
                val data = doc.data ?: return
                val current = getProfile()
                val fullName = (data["collegeFullName"] as? String)
                    ?: (data["name"] as? String)
                    ?: current.collegeFullName
                val shortName = (data["collegeName"] as? String)
                    ?: (data["name"] as? String)
                    ?: current.collegeName
                val tagline = (data["tagline"] as? String) ?: current.tagline
                val email = (data["email"] as? String) ?: (data["contactEmail"] as? String) ?: current.email
                val phone = (data["phone"] as? String) ?: current.phone
                val address = (data["address"] as? String) ?: (data["location"] as? String) ?: current.address

                val updated = current.copy(
                    collegeName = shortName,
                    collegeFullName = fullName,
                    tagline = tagline,
                    email = email,
                    phone = phone,
                    address = address,
                    isSetupComplete = true
                )
                saveProfile(updated)
                Log.d(TAG, "Synced college profile from cloud for $collegeId: $fullName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching college profile from cloud", e)
        }
    }

    suspend fun syncToCloud(collegeId: String, profile: CollegeProfile) {
        if (collegeId.isBlank()) return
        try {
            val db = FirebaseFirestore.getInstance()
            val map = hashMapOf(
                "name" to profile.collegeFullName,
                "collegeFullName" to profile.collegeFullName,
                "collegeName" to profile.collegeName,
                "tagline" to profile.tagline,
                "libraryName" to profile.libraryName,
                "address" to profile.address,
                "location" to profile.address,
                "phone" to profile.phone,
                "email" to profile.email,
                "contactEmail" to profile.email,
                "website" to profile.website,
                "establishedYear" to profile.establishedYear,
                "lastUpdated" to System.currentTimeMillis(),
                "isSetupComplete" to true
            )
            db.collection("institutions").document(collegeId).set(map, SetOptions.merge()).await()
            db.collection("directorate_index").document(collegeId).set(
                hashMapOf(
                    "institutionId" to collegeId,
                    "name" to profile.collegeFullName,
                    "location" to profile.address,
                    "lastSeen" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
            db.collection("colleges").document(collegeId).set(
                hashMapOf(
                    "collegeId" to collegeId,
                    "collegeName" to profile.collegeFullName,
                    "location" to profile.address,
                    "lastSyncAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
            Log.d(TAG, "Uploaded college profile to cloud for $collegeId")
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing college profile to cloud", e)
        }
    }

    fun isSetupComplete(): Boolean {
        return getProfile().isSetupComplete
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

