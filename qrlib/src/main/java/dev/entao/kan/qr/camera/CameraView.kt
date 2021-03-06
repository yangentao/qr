package dev.entao.kan.qr.camera

import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.google.zxing.ResultPoint
import dev.entao.kan.creator.createTextViewB
import dev.entao.kan.ext.*
import dev.entao.kan.qr.QRConfig


/**
 * Encapsulates BarcodeView, ViewfinderView and status text.

 * To customize the UI, use BarcodeView and ViewfinderView directly.
 */
class CameraView(context: Context) : FrameLayout(context) {
    val barcodeView: CameraPreview = CameraPreview(context).genId()
    val viewFinder: ViewfinderView = ViewfinderView(context).genId()
    private val statusView: TextView = context.createTextViewB().textColorWhite().backColor(Color.TRANSPARENT)


    init {
        genId()
        addView(barcodeView, FParam.fill())
        addView(viewFinder, FParam.fill())
        viewFinder.setPreview(barcodeView)
        addView(statusView, FParam.Wrap.GravityTopCenter.margins(20))
        setStatusText(QRConfig.tips)
    }


    fun setStatusText(text: String) {
        statusView.text = text
    }


    fun onPause() {
    }



    fun onResume() {
        barcodeView.onResume()
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

