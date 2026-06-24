package com.college.library.profile

import android.app.Application
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CollegeProfileViewModel @Inject constructor(
    application: Application
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
    }

    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }

    fun isSetupComplete(): Boolean {
        return profileManager.isSetupComplete()
    }
}
