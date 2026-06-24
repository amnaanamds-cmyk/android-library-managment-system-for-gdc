package com.college.library.license

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

class LicenseManager(
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "license_prefs"
        private const val KEY_LICENSE = "license_key"
        private const val KEY_ACTIVATED_AT = "activated_at"
        private const val KEY_EXPIRY = "expiry_date"
        private const val KEY_DEVICE_ID = "bound_device_id"
        private const val LICENSE_PREFIX = "GDCLIB"
        private const val LICENSE_DURATION_DAYS = 365L
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val raw = "GDC-LIB-$androidId"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }.take(32)
    }

    fun getFormattedDeviceId(): String {
        return getDeviceId().chunked(4).joinToString("-")
    }

    fun isLicensed(): Boolean {
        val storedKey = prefs.getString(KEY_LICENSE, null) ?: return false
        val storedDeviceId = prefs.getString(KEY_DEVICE_ID, null) ?: return false
        val expiry = prefs.getLong(KEY_EXPIRY, 0L)

        if (storedDeviceId != getDeviceId()) return false
        if (System.currentTimeMillis() > expiry) return false
        if (!isValidKeyFormat(storedKey)) return false

        return verifyKey(storedKey, storedDeviceId)
    }

    fun getLicenseStatus(): LicenseStatus {
        val storedKey = prefs.getString(KEY_LICENSE, null)
            ?: return LicenseStatus.NotActivated
        val storedDeviceId = prefs.getString(KEY_DEVICE_ID, null)
            ?: return LicenseStatus.NotActivated
        val expiry = prefs.getLong(KEY_EXPIRY, 0L)

        if (storedDeviceId != getDeviceId()) return LicenseStatus.WrongDevice
        if (System.currentTimeMillis() > expiry) return LicenseStatus.Expired(expiry)

        return if (verifyKey(storedKey, storedDeviceId)) {
            LicenseStatus.Valid(expiry)
        } else {
            LicenseStatus.Tampered
        }
    }

    fun activateLicense(key: String): ActivationResult {
        val cleanKey = key.trim().uppercase()

        if (!isValidKeyFormat(cleanKey)) {
            return ActivationResult.Error("Invalid format. Expected: $LICENSE_PREFIX-XXXX-XXXX-XXXX")
        }

        val deviceId = getDeviceId()
        if (!verifyKey(cleanKey, deviceId)) {
            return ActivationResult.Error("This license key is not valid for this device.")
        }

        val now = System.currentTimeMillis()
        val expiry = now + (LICENSE_DURATION_DAYS * 24 * 60 * 60 * 1000)

        prefs.edit()
            .putString(KEY_LICENSE, cleanKey)
            .putString(KEY_DEVICE_ID, deviceId)
            .putLong(KEY_ACTIVATED_AT, now)
            .putLong(KEY_EXPIRY, expiry)
            .apply()

        return ActivationResult.Success(expiry)
    }

    fun deactivate() {
        prefs.edit().clear().apply()
    }

    fun getExpiryDate(): Long = prefs.getLong(KEY_EXPIRY, 0L)

    private fun isValidKeyFormat(key: String): Boolean {
        return Regex("^$LICENSE_PREFIX-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$").matches(key)
    }

    private fun verifyKey(key: String, deviceId: String): Boolean {
        val parts = key.removePrefix("$LICENSE_PREFIX-").split("-")
        if (parts.size != 3) return false

        val keyBody = parts.joinToString("")
        val expectedHash = generateKeyHash(deviceId)
        return keyBody == expectedHash
    }

    private fun generateKeyHash(deviceId: String): String {
        val secret = "GDC_LIBRARY_2024_SECRET"
        val input = "$deviceId:$secret"
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }.take(12)
    }

    fun generateLicenseKey(deviceId: String): String {
        val hash = generateKeyHash(deviceId)
        val formatted = hash.chunked(4).joinToString("-")
        return "$LICENSE_PREFIX-$formatted"
    }
}

sealed class LicenseStatus {
    data object NotActivated : LicenseStatus()
    data class Valid(val expiry: Long) : LicenseStatus()
    data class Expired(val expiry: Long) : LicenseStatus()
    data object WrongDevice : LicenseStatus()
    data object Tampered : LicenseStatus()
}

sealed class ActivationResult {
    data class Success(val expiry: Long) : ActivationResult()
    data class Error(val message: String) : ActivationResult()
}
