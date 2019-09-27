package dev.entao.kan.qr.camerax

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import dev.entao.kan.log.loge

class QRImageAnalysis : ImageAnalysis.Analyzer {
    private val reader: MultiFormatReader = MultiFormatReader().apply {
        setHints(
            mapOf<DecodeHintType, Collection<BarcodeFormat>>(
                Pair(DecodeHintType.POSSIBLE_FORMATS, arrayListOf(BarcodeFormat.QR_CODE))
            )
        )
    }

    private var processing = false
    var onResult: (String) -> Unit = {}


    override fun analyze(image: ImageProxy, rotationDegrees: Int) {
        if (processing) {
            return
        }
        processing = true
        try {
//            logd("Analyze...", rotationDegrees, image.width, image.height)
            if (image.format != ImageFormat.YUV_420_888) {
                loge("Expect ImageFormat.YUV_420_888")
                return
            }
            val buffer = image.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            val w = image.width
            val h = image.height
            buffer.get(data)
            val src = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
            val bmp = BinaryBitmap(HybridBinarizer(src))
            try {
                val r = reader.decode(bmp)
                val a = this.onResult
                this.onResult = {}
                a(r.text)
            } catch (ex: NotFoundException) {
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        processing = false

    }

}