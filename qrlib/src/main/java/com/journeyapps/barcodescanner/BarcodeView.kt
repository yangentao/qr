package com.journeyapps.barcodescanner

import android.content.Context
import android.os.Handler
import com.google.zxing.DecodeHintType
import com.google.zxing.ResultPoint
import dev.entao.qr.R
import dev.entao.qr.ScanConfig
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
class BarcodeView(context: Context) : CameraPreview(context) {

    private var decoding = false
    private var callback: BarcodeCallback? = null
    private var decoderThread: DecoderThread? = null
    var decoderFactory: DecoderFactory = DefaultDecoderFactory(ScanConfig.decodeSet, null, ScanConfig.charset)


    private val resultCallback = Handler.Callback { message ->
        if (message.what == R.id.zxing_decode_succeeded) {
            val result = message.obj as BarcodeResult

            if (result != null) {
                if (callback != null && decoding) {
                    callback?.barcodeResult(result)
                    stopDecoding()
                }
            }
            return@Callback true
        } else if (message.what == R.id.zxing_decode_failed) {
            // Failed. Next preview is automatically tried.
            return@Callback true
        } else if (message.what == R.id.zxing_possible_result_points) {
            val resultPoints = message.obj as List<ResultPoint>
            if (callback != null && decoding) {
                callback?.possibleResultPoints(resultPoints)
            }
            return@Callback true
        }
        false
    }
    private val resultHandler: Handler = Handler(resultCallback)


    private fun createDecoder(): Decoder {
        val callback = DecoderResultPointCallback()
        val hints = HashMap<DecodeHintType, Any>()
        hints[DecodeHintType.NEED_RESULT_POINT_CALLBACK] = callback
        val decoder = this.decoderFactory.createDecoder(hints)
        callback.decoder = decoder
        return decoder
    }


    fun decodeSingle(callback: BarcodeCallback) {
        this.decoding = true
        this.callback = callback
        startDecoderThread()
    }


    /**
     * Stop decoding, but do not stop the preview.
     */
    fun stopDecoding() {
        this.decoding = false
        this.callback = null
        stopDecoderThread()
    }


    private fun startDecoderThread() {
        stopDecoderThread() // To be safe
        if (this.decoding && isPreviewActive) {
            // We only start the thread if both:
            // 1. decoding was requested
            // 2. the preview is active
            decoderThread = DecoderThread(cameraInstance, createDecoder(), resultHandler)
            decoderThread!!.cropRect = previewFramingRect
            decoderThread!!.start()
        }
    }

    override fun previewStarted() {
        super.previewStarted()

        startDecoderThread()
    }

    private fun stopDecoderThread() {
        if (decoderThread != null) {
            decoderThread!!.stop()
            decoderThread = null
        }
    }

    /**
     * Stops the live preview and decoding.
     *
     *
     * Call from the Activity's onPause() method.
     */
    override fun pause() {
        stopDecoderThread()

        super.pause()
    }
}
