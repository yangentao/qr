package com.journeyapps.barcodescanner

import android.content.Context
import android.graphics.Rect
import com.google.zxing.DecodeHintType
import com.google.zxing.ResultPoint
import com.google.zxing.ResultPointCallback
import com.journeyapps.barcodescanner.camera.PreviewCallback
import dev.entao.qr.ScanConfig
import dev.entao.qr.TaskHandler
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
class BarcodeView(context: Context) : CameraPreview(context), ResultPointCallback {

    private var decoding = false
    private var callback: BarcodeCallback? = null


    private var taskHandler: TaskHandler? = null
    val decoder: Decoder = createDecoder()
    var cropRect: Rect? = null
    private var running = false

    private fun createDecoder(): Decoder {
        val hints = HashMap<DecodeHintType, Any>()
        hints[DecodeHintType.NEED_RESULT_POINT_CALLBACK] = this
        val decoderFactory: DecoderFactory = DefaultDecoderFactory(ScanConfig.decodeSet, null, ScanConfig.charset)
        return decoderFactory.createDecoder(hints)
    }

    override fun foundPossibleResultPoint(point: ResultPoint) {
        decoder.foundPossibleResultPoint(point)
    }

    fun decodeSingle(callback: BarcodeCallback) {
        this.decoding = true
        this.callback = callback
        startDecoderThread()
    }


    fun stopDecoding() {
        this.decoding = false
        this.callback = null
        stopDecoderThread()
    }


    private fun startDecoderThread() {
        stopDecoderThread() // To be safe
        if (this.decoding && isPreviewActive) {
            cropRect = previewFramingRect
            taskHandler = TaskHandler("decoder")
            running = true
            requestNextPreview()
        }
    }

    override fun previewStarted() {
        super.previewStarted()
        startDecoderThread()
    }

    private fun stopDecoderThread() {
        running = false
        taskHandler?.quit()
        taskHandler = null
    }


    private fun requestNextPreview() {
        val ci = cameraInstance ?: return
        if (ci.isOpen) {
            ci.requestPreview(PreviewCallback { sourceData ->
                if (running) {
                    taskHandler?.post {
                        decode(sourceData)
                    }
                }
            })
        }
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
            val r = BarcodeResult(rawResult, sourceData)
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
