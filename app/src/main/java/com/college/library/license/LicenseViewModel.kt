package com.college.library.license

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class LicenseViewModel @Inject constructor(
    private val licenseManager: LicenseManager
) : ViewModel() {

    private val _isLicensed = MutableStateFlow(licenseManager.isLicensed())
    val isLicensed = _isLicensed.asStateFlow()

    private val _activationState = MutableStateFlow<ActivationState>(ActivationState.Idle)
    val activationState = _activationState.asStateFlow()

    fun getDeviceId(): String = licenseManager.getFormattedDeviceId()

    fun getRawDeviceId(): String = licenseManager.getDeviceId()

    fun getLicenseStatus(): LicenseStatus = licenseManager.getLicenseStatus()

    fun activate(key: String) {
        _activationState.value = ActivationState.Loading

        val result = licenseManager.activateLicense(key)
        when (result) {
            is ActivationResult.Success -> {
                _isLicensed.value = true
                _activationState.value = ActivationState.Success(result.expiry)
            }
            is ActivationResult.Error -> {
                _activationState.value = ActivationState.Error(result.message)
            }
        }
    }

    fun resetState() {
        _activationState.value = ActivationState.Idle
    }
}

sealed class ActivationState {
    data object Idle : ActivationState()
    data object Loading : ActivationState()
    data class Success(val expiry: Long) : ActivationState()
    data class Error(val message: String) : ActivationState()
}
