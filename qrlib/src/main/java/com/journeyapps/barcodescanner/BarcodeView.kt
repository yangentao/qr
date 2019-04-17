package com.journeyapps.barcodescanner

import android.content.Context
import android.graphics.Rect
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.ResultPoint
import com.google.zxing.ResultPointCallback
import dev.entao.log.logd
import dev.entao.qr.QRConfig
import dev.entao.qr.TaskHandler
import dev.entao.qr.camera.BarcodeCallback
import dev.entao.qr.camera.BarcodeResult
import dev.entao.qr.camera.Decoder
import dev.entao.qr.camera.PreviewDataCallback
import java.util.*

/**
 * A view for scanning barcodes.
 *
 *
 * Two methods MUST be called to manage the state:
 * 1. resume() - initialize the camera and start the preview. Call from the Activity's onResume().
 * 2. pause() - stop the preview and release any resources. Call from the Activity's onPause().
 *
 *
 * Start decoding with decodeSingle() or decodeContinuous(). Stop decoding with stopDecoding().
 *
 * @see CameraPreview for more details on the preview lifecycle.
 */
class BarcodeView(context: Context) : CameraPreview(context), ResultPointCallback, PreviewDataCallback {

    private var callback: BarcodeCallback? = null
    private val decoding: Boolean get() = callback != null

    private var taskHandler: TaskHandler? = null
    private var cropRect: Rect? = null
    private val decoder: Decoder

    init {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
        hints[DecodeHintType.NEED_RESULT_POINT_CALLBACK] = this
        hints[DecodeHintType.POSSIBLE_FORMATS] = QRConfig.decodeSet
        hints[DecodeHintType.CHARACTER_SET] = QRConfig.charset
        val reader = MultiFormatReader()
        reader.setHints(hints)
        decoder = Decoder(reader)
    }


    override fun foundPossibleResultPoint(point: ResultPoint) {
        decoder.foundPossibleResultPoint(point)
    }

    fun decodeSingle(callback: BarcodeCallback) {
        this.callback = callback
        startDecoderThread()
    }


    fun stopDecoding() {
        this.callback = null
        stopDecoderThread()
    }


    private fun startDecoderThread() {
        stopDecoderThread() // To be safe
        logd("startDecoderThread...")
        if (this.decoding && isPreviewActive) {
            logd("startDecoderThread... YES")
            cropRect = previewFramingRect
            taskHandler = TaskHandler("decoder")
            requestNextPreview()
        }
    }

    override fun previewStarted() {
        super.previewStarted()
        startDecoderThread()
    }

    private fun stopDecoderThread() {
        taskHandler?.quit()
        taskHandler = null
    }

    override fun onPreview(sourceData: SourceData) {
        taskHandler?.post {
            decode(sourceData)
        }
    }

    private fun requestNextPreview() {
        cameraInstance?.requestPreview(this)
    }


    override fun pause() {
        stopDecoderThread()
        super.pause()
    }

    private fun decode(sourceData: SourceData) {
        val rect = this.cropRect ?: return requestNextPreview()

        sourceData.cropRect = rect
        val source = sourceData.createSource()
        val rawResult = decoder.decode(source)
        if (rawResult != null) {
            val r = BarcodeResult(rawResult)
            callback?.barcodeResult(r)
            stopDecoding()
            return
        }
        val ps = decoder.possibleResultPoints
        if (ps.isNotEmpty()) {
            callback?.possibleResultPoints(ps)
        }
        requestNextPreview()
    }

}
