package com.college.library.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Color
import java.awt.image.BufferedImage

/**
 * Renders a QR code from an arbitrary string payload (e.g. the college
 * link JSON) using ZXing and converts it into a Compose [ImageBitmap]
 * so it can be displayed inside the desktop UI.
 */
object QrImageGenerator {

    fun generate(content: String, size: Int = 256): ImageBitmap {
        val matrix: BitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 1)
        )

        val image = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, image.width, image.height)

        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    image.setRGB(x, y, Color.BLACK.rgb)
                }
            }
        }

        return image.toComposeImageBitmap()
    }
}
