package com.college.library.security

import com.sun.jna.platform.win32.Crypt32
import com.sun.jna.platform.win32.WinCrypt
import com.sun.jna.ptr.PointerByReference
import java.util.Base64

object SecurityUtils {

    /**
     * Encrypts data using Windows DPAPI (User-specific)
     */
    fun encryptWithDPAPI(data: String): String? {
        try {
            val input = data.toByteArray(Charsets.UTF_8)
            val dataIn = WinCrypt.DATA_BLOB()
            dataIn.cbData = input.size
            dataIn.pbData = com.sun.jna.Memory(input.size.toLong()).apply {
                write(0, input, 0, input.size)
            }

            val dataOut = WinCrypt.DATA_BLOB()
            val success = Crypt32.INSTANCE.CryptProtectData(
                dataIn,
                "GDC_Library_License",
                null,
                null,
                null,
                0,
                dataOut
            )

            if (success) {
                val encrypted = dataOut.pbData.getByteArray(0, dataOut.cbData)
                // Free memory if necessary, but JNA might handle this if we're careful
                return Base64.getEncoder().encodeToString(encrypted)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Decrypts data using Windows DPAPI
     */
    fun decryptWithDPAPI(encryptedData: String): String? {
        try {
            val input = Base64.getDecoder().decode(encryptedData)
            val dataIn = WinCrypt.DATA_BLOB()
            dataIn.cbData = input.size
            dataIn.pbData = com.sun.jna.Memory(input.size.toLong()).apply {
                write(0, input, 0, input.size)
            }

            val dataOut = WinCrypt.DATA_BLOB()
            val success = Crypt32.INSTANCE.CryptUnprotectData(
                dataIn,
                null,
                null,
                null,
                null,
                0,
                dataOut
            )

            if (success) {
                val decrypted = dataOut.pbData.getByteArray(0, dataOut.cbData)
                return String(decrypted, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
