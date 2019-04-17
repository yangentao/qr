@file:Suppress("MemberVisibilityCanBePrivate")

package com.journeyapps.barcodescanner

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.TextureView
import android.view.WindowManager
import android.widget.FrameLayout
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.ResultPoint
import com.google.zxing.ResultPointCallback
import com.journeyapps.barcodescanner.camera.CameraInstance
import com.journeyapps.barcodescanner.camera.DisplayConfiguration
import dev.entao.qr.QRConfig
import dev.entao.qr.TaskHandler
import dev.entao.qr.camera.*
import dev.entao.ui.ext.FParam
import dev.entao.ui.ext.Fill
import dev.entao.util.Task
import java.util.*

/**
 * CameraPreview is a view that handles displaying of a camera preview on a SurfaceView. It is
 * intended to be used as a base for realtime processing of camera images, e.g. barcode decoding
 * or OCR, although none of this happens in CameraPreview itself.
 *
 *
 * The camera is managed on a separate thread, using CameraInstance.
 *
 *
 * Two methods MUST be called on CameraPreview to manage its state:
 * 1. resume() - initialize the camera and start the preview. Call from the Activity's onResume().
 * 2. pause() - stop the preview and release any resources. Call from the Activity's onPause().
 *
 *
 * Startup sequence:
 *
 *
 * 1. Create SurfaceView.
 * 2. open camera.
 * 2. layout this container, to get size
 * 3. set display config, according to the container size
 * 4. configure()
 * 5. wait for preview size to be ready
 * 6. set surface size according to preview size
 * 7. set surface and start preview
 */
class CameraPreview(context: Context) : FrameLayout(context), TextureView.SurfaceTextureListener, ResultPointCallback, PreviewDataCallback {

    var cameraInstance: CameraInstance? = null
        private set

    private var textureView: TextureView

    /**
     * The preview typically starts being active a while after calling resume(), and stops
     * when calling pause().
     *
     * @return true if the preview is active
     */
    var isPreviewActive = false
        private set

    private var rotationListener: RotationListener = RotationListener(context)

    private val stateListeners = ArrayList<StateListener>()


    // Rect placing the preview surface
    private var surfaceRect: Rect? = null


    var framingRect: Rect? = null
        private set

    // Framing rectangle relative to the preview resolution
    /**
     * The framing rect, relative to the camera preview resolution.
     *
     *
     * Will never be null while the preview is active.
     *
     * @return the preview rect, or null
     * @see .isPreviewActive
     */
    var previewFramingRect: Rect? = null
        private set


    private var callback: BarcodeCallback? = null
    private val decoding: Boolean get() = callback != null

    private var taskHandler: TaskHandler? = null
    private var cropRect: Rect? = null
    private val decoder: Decoder


    private val fireState = object : StateListener {
        override fun previewSized() {
            for (listener in stateListeners) {
                listener.previewSized()
            }
        }

        override fun previewStarted() {
            for (listener in stateListeners) {
                listener.previewStarted()
            }

        }

        override fun previewStopped() {
            for (listener in stateListeners) {
                listener.previewStopped()
            }
        }

        override fun cameraError(error: Exception) {
            for (listener in stateListeners) {
                listener.cameraError(error)
            }
        }
    }

    /**
     * Considered active if between resume() and pause().
     *
     * @return true if active
     */
    val isActive: Boolean
        get() = cameraInstance != null

    private val displayRotation: Int
        get() = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

    interface StateListener {
        /**
         * Preview and frame sizes are determined.
         */
        fun previewSized()

        /**
         * Preview has started.
         */
        fun previewStarted()

        /**
         * Preview has stopped.
         */
        fun previewStopped()

        /**
         * The camera has errored, and cannot display a preview.
         *
         * @param error the error
         */
        fun cameraError(error: Exception)
    }


    init {

        setBackgroundColor(Color.BLACK)
        textureView = TextureView(context)
        textureView.surfaceTextureListener = this
        addView(textureView, FParam.Fill)

        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
        hints[DecodeHintType.NEED_RESULT_POINT_CALLBACK] = this
        hints[DecodeHintType.POSSIBLE_FORMATS] = QRConfig.decodeSet
        hints[DecodeHintType.CHARACTER_SET] = QRConfig.charset
        val reader = MultiFormatReader()
        reader.setHints(hints)
        decoder = Decoder(reader)
    }


    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startCamera(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        resizeCamera(surface, width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopCamera()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    private fun startCamera(surface: SurfaceTexture, width: Int, height: Int) {
        cameraInstance = CameraInstance(context, this.textureView, surface, Size(width, height))
        containerSized(Size(width, height))

        rotationListener.listen(object : RotationCallback {
            override fun onRotationChanged(rotation: Int) {
                Task.foreDelay(250.toLong()) {
                    if (isActive) {
                        stopCamera()
                        if (this@CameraPreview.textureView.isAvailable) {
                            startCamera(textureView.surfaceTexture, textureView.width, textureView.height)
                        }
                    }
                }
            }
        })
    }

    private fun stopCamera() {
        stopDecoderThread()
        cameraInstance?.close()
        cameraInstance = null
        isPreviewActive = false
        this.previewFramingRect = null
        rotationListener.stop()
        fireState.previewStopped()
    }

    private fun resizeCamera(surface: SurfaceTexture, width: Int, height: Int) {
        containerSized(Size(width, height))
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
        if (this.decoding && isPreviewActive) {
            cropRect = previewFramingRect
            taskHandler = TaskHandler("decoder")
            requestNextPreview()
        }
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


    /**
     * Add a listener to be notified of changes to the preview state, as well as camera errors.
     *
     * @param listener the listener
     */
    fun addStateListener(listener: StateListener) {
        stateListeners.add(listener)
    }

    private fun calculateFrames(dc: DisplayConfiguration) {
        val camInst = this.cameraInstance ?: return
        val preSize = camInst.previewSize

        val previewWidth = preSize.width
        val previewHeight = preSize.height

        surfaceRect = dc.scalePreview(preSize)

        framingRect = calculateFramingRect(Rect(0, 0, camInst.surfaceSize.width, camInst.surfaceSize.height))
        val frameInPreview = Rect(framingRect)
        frameInPreview.offset(-surfaceRect!!.left, -surfaceRect!!.top)

        previewFramingRect = Rect(
            frameInPreview.left * previewWidth / surfaceRect!!.width(),
            frameInPreview.top * previewHeight / surfaceRect!!.height(),
            frameInPreview.right * previewWidth / surfaceRect!!.width(),
            frameInPreview.bottom * previewHeight / surfaceRect!!.height()
        )

        if (previewFramingRect!!.width() <= 0 || previewFramingRect!!.height() <= 0) {
            previewFramingRect = null
            framingRect = null
            Log.w(TAG, "Preview frame is too small")
        } else {
            fireState.previewSized()
        }
    }


    private fun containerSized(containerSize: Size) {
        val ci = cameraInstance ?: return
        val dc = DisplayConfiguration(displayRotation, containerSize)
        ci.configureCamera(dc)
        calculateFrames(dc)

        startPreviewIfReady()
    }


    fun setTorch(on: Boolean) {
        cameraInstance?.setTorch(on)
    }


    private fun startPreviewIfReady() {
        val camInst = this.cameraInstance ?: return
        if (surfaceRect != null) {
            if (!isPreviewActive) {
                Log.i(TAG, "Starting preview")
                camInst.startPreview()
                isPreviewActive = true

                startDecoderThread()
                fireState.previewStarted()
            }
        }
    }


    private fun calculateFramingRect(container: Rect): Rect {
        val rect = Rect(container)

        val rw = QRConfig.width
        val rh = QRConfig.height
        if (rw > 0 && rh > 0) {
            val horizontalMargin = Math.max(0, (rect.width() - rw) / 2)
            val verticalMargin = Math.max(0, (rect.height() - rh) / 2)
            rect.inset(horizontalMargin, verticalMargin)
            return rect
        }
        val minEdge = Math.min(container.width(), container.height())
        val fEdge: Int = (QRConfig.percentEdge * minEdge).toInt()
        val horizontalMargin = Math.max(0, (rect.width() - fEdge) / 2)
        val verticalMargin = Math.max(0, (rect.height() - fEdge) / 2)
        rect.inset(horizontalMargin, verticalMargin)
        return rect

    }

    companion object {

        private val TAG = CameraPreview::class.java.simpleName

    }
}
