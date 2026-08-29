package com.college.library.profile

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollegeProfileViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    private val profileManager = CollegeProfileManager.getInstance(application)

    private val _profile = MutableStateFlow(profileManager.getProfile())
    val profile = _profile.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess = _saveSuccess.asStateFlow()

    fun updateProfile(profile: CollegeProfile) {
        _profile.value = profile
    }

    fun saveProfile() {
        val current = _profile.value.copy(isSetupComplete = true)
        profileManager.saveProfile(current)
        _profile.value = current
        _saveSuccess.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = application.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                val instId = prefs.getString("institution_id", "gdc11") ?: "gdc11"
                profileManager.syncToCloud(instId, current)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }

    fun isSetupComplete(): Boolean {
        return profileManager.isSetupComplete()
    }
}

