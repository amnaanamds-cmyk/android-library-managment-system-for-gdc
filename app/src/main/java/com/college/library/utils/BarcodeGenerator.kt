package com.college.library.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

object BarcodeGenerator {
    fun generateBarcode(content: String, width: Int = 400, height: Int = 150, format: BarcodeFormat = BarcodeFormat.CODE_128): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val bitMatrix = MultiFormatWriter().encode(content, format, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
