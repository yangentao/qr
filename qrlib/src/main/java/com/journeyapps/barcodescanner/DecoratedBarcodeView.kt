package com.journeyapps.barcodescanner

import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.google.zxing.ResultPoint
import dev.entao.qr.ScanConfig
import dev.entao.ui.creator.createTextViewB
import dev.entao.ui.ext.*


/**
 * Encapsulates BarcodeView, ViewfinderView and status text.

 * To customize the UI, use BarcodeView and ViewfinderView directly.
 */
class DecoratedBarcodeView(context: Context) : FrameLayout(context) {
    val barcodeView: BarcodeView = BarcodeView(context).genId()
    val viewFinder: ViewfinderView = ViewfinderView(context, null).genId()
    private val statusView: TextView = context.createTextViewB().textColorWhite().backColor(Color.TRANSPARENT)


    init {
        genId()
        addView(barcodeView, FParam.fill())
        barcodeView.initializeAttributes(null)
        addView(viewFinder, FParam.fill())
        viewFinder.setCameraPreview(barcodeView)
        addView(statusView, FParam.Wrap.GravityTopCenter.margins(20))
        setStatusText(ScanConfig.tips)
        barcodeView.decoderFactory = DefaultDecoderFactory(ScanConfig.decodeSet, null, ScanConfig.charset)
    }


    fun setStatusText(text: String) {
        statusView.text = text
    }


    fun pause() {
        barcodeView.pause()
    }


    fun resume() {
        barcodeView.resume()
    }


    fun decodeSingle(callback: BarcodeCallback) {
        barcodeView.decodeSingle(WrappedCallback(callback))
    }


    fun setTorchOn() {
        barcodeView.setTorch(true)
    }


    fun setTorchOff() {
        barcodeView.setTorch(false)
    }

    /**
     * Handles focus, camera, volume up and volume down keys.

     * Note that this view is not usually focused, so the Activity should call this directly.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_FOCUS, KeyEvent.KEYCODE_CAMERA ->
                // Handle these events so they don't launch the Camera app
                return true
            // Use volume up/down to turn on light
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                setTorchOff()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                setTorchOn()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }


    inner class WrappedCallback(private val delegate: BarcodeCallback) : BarcodeCallback {

        override fun barcodeResult(result: BarcodeResult) {
            delegate.barcodeResult(result)
        }

        override fun possibleResultPoints(resultPoints: List<ResultPoint>) {
            for (point in resultPoints) {
                viewFinder.addPossibleResultPoint(point)
            }
            delegate.possibleResultPoints(resultPoints)
        }
    }
}

