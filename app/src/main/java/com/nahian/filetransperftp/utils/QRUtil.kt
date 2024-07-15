package com.nahian.filetransperftp.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QRUtil {
    suspend fun generateQRCode(text: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            val size = 512 // Size of the QR code
            val qrCodeWriter = QRCodeWriter()
            try {
                val bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size, size)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }
                bitmap
            } catch (e: WriterException) {
                e.printStackTrace()
                null
            }
        }
    }
}