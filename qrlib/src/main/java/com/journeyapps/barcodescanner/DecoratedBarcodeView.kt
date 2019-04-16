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
	var barcodeView: BarcodeView
		private set
	var viewFinder: ViewfinderView
		private set
	var statusView: TextView
		private set


	private var torchListener: TorchListener? = null


	init {
		genId()
		barcodeView = BarcodeView(context).genId()
		addView(barcodeView, FParam.fill())
		barcodeView.initializeAttributes(null)

		viewFinder = ViewfinderView(context, null).genId()
		addView(viewFinder, FParam.fill())
		viewFinder.setCameraPreview(barcodeView)

		statusView = createTextViewB().textColorWhite().backColor(Color.TRANSPARENT)
		addView(statusView, FParam.Wrap.GravityTopCenter.margins(20))
	}


	fun initializeFromIntent(config: ScanConfig) {
		setStatusText(config.tips)
		barcodeView.decoderFactory = DefaultDecoderFactory(config.decodeSet, null, config.charset)
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


	fun decodeContinuous(callback: BarcodeCallback) {
		barcodeView.decodeContinuous(WrappedCallback(callback))
	}


	fun setTorchOn() {
		barcodeView.setTorch(true)

		if (torchListener != null) {
			torchListener!!.onTorchOn()
		}
	}


	fun setTorchOff() {
		barcodeView.setTorch(false)

		if (torchListener != null) {
			torchListener!!.onTorchOff()
		}
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

	fun setTorchListener(listener: TorchListener) {
		this.torchListener = listener
	}


	interface TorchListener {

		fun onTorchOn()

		fun onTorchOff()
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

