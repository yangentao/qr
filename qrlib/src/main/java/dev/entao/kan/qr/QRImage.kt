package dev.entao.kan.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.*
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.entao.kan.appbase.ex.saveJpg
import dev.entao.kan.appbase.ex.savePng
import java.io.File


@Suppress("unused")
class QRImage(val content: String) {
    var format = "png"    //图片的格式
    var qrSize = 400      //二维码图片的大小
    var iconPercent = 0.20  // 图标的高宽是二维码高宽的1/5
    var level: ErrorCorrectionLevel = ErrorCorrectionLevel.M


    fun makeToFile(saveToFile: File): Boolean {
        try {
            val bmp = make()
            if (format == "png") {
                return bmp.savePng(saveToFile)
            } else {
                return bmp.saveJpg(saveToFile)
            }
        } catch (ex: Exception) {
        }
        return false
    }

    fun make(): Bitmap {
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "utf-8"    //指定字符编码为“utf-8”
        hints[EncodeHintType.ERROR_CORRECTION] = level  //指定二维码的纠错等级为中级
        hints[EncodeHintType.MARGIN] = 2    //设置图片的边距
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, qrSize, qrSize, hints)
        return matrixToBitmap(bitMatrix)
    }

    private fun matrixToBitmap(matrix: BitMatrix): Bitmap {
        val qrW = matrix.width
        val qrH = matrix.height
        val pixels = IntArray(qrW * qrH)
        for (y in 0 until qrH) {
            for (x in 0 until qrW) {
                if (matrix.get(x, y)) {
                    pixels[y * qrW + x] = Color.BLACK
                } else {
                    pixels[y * qrW + x] = Color.WHITE
                }
            }
        }
        val bitmap = Bitmap.createBitmap(qrW, qrH, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, qrW, 0, 0, qrW, qrH)
        return bitmap
    }

    companion object {

        fun scan(bufImage: Bitmap): String? {
            val bmp = BinaryBitmap(HybridBinarizer(bmpToSource(bufImage)))
            val hints = HashMap<DecodeHintType, Any>()
            hints[DecodeHintType.CHARACTER_SET] = "UTF-8"
            hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
            val r = MultiFormatReader().decode(bmp, hints)
            return r?.text
        }

        private fun bmpToSource(bmp: Bitmap): RGBLuminanceSource {
            val w = bmp.width
            val h = bmp.height
            val buf = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    buf[y * w + x] = bmp.getPixel(x, y)
                }
            }
            return RGBLuminanceSource(w, h, buf)
        }
    }
}