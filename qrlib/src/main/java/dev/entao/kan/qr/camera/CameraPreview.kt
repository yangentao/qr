@file:Suppress("MemberVisibilityCanBePrivate")

package dev.entao.kan.qr.camera

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.widget.FrameLayout
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.ResultPoint
import com.google.zxing.ResultPointCallback
import dev.entao.kan.appbase.Task
import dev.entao.kan.base.hasPerm
import dev.entao.kan.ext.FParam
import dev.entao.kan.ext.Fill
import dev.entao.kan.log.logd
import dev.entao.kan.qr.QRConfig
import dev.entao.kan.qr.TaskHandler
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

    private var rotationListener: RotationListener = RotationListener(context)

    private val stateListeners = ArrayList<StateListener>()


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

    fun onResume() {
        if (cameraInstance == null && textureView.isAvailable) {
            startCamera(textureView.surfaceTexture, textureView.width, textureView.height)
        }
    }

    private fun startCamera(surface: SurfaceTexture, width: Int, height: Int) {
        if (cameraInstance != null) {
            return
        }
        if (context.hasPerm(Manifest.permission.CAMERA)) {
            cameraInstance = CameraInstance(context, this.textureView, surface, Size(width, height))
            cameraInstance?.previewCallback = this
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
    }

    private fun stopCamera() {
        stopDecoderThread()
        cameraInstance?.close()
        cameraInstance = null
        this.previewFramingRect = null
        rotationListener.stop()
        fireState.previewStopped()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun resizeCamera(surface: SurfaceTexture, width: Int, height: Int) {
        containerSized(Size(width, height))
    }

    private fun containerSized(containerSize: Size) {
        val camInst = this.cameraInstance ?: return
        camInst.configureCamera()
        calculateFrames(containerSize)
        if (camInst.previewing) {
            camInst.stopPreview()
        }
        camInst.startPreview()
        stopDecoderThread() // To be safe
        cropRect = previewFramingRect
        taskHandler = TaskHandler("decoder")
        requestNextPreview()
        fireState.previewStarted()
    }

    override fun foundPossibleResultPoint(point: ResultPoint) {
        decoder.foundPossibleResultPoint(point)
    }

    fun decodeSingle(callback: BarcodeCallback) {
        this.callback = callback
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
        cameraInstance?.requestPreview()
    }


    private fun decode(sourceData: SourceData) {
        val cb = this.callback ?: return
        val rect = this.cropRect ?: return requestNextPreview()

        sourceData.cropRect = rect
        val source = sourceData.createSource()
        val rawResult = decoder.decode(source)
        if (rawResult != null) {
            val r = BarcodeResult(rawResult)
            cb.barcodeResult(r)
            this.callback = null
            stopDecoderThread()
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

    private fun calculateFrames(containerSize: Size) {
        val camInst = this.cameraInstance ?: return
        val preSize = camInst.previewSize

        val previewWidth = preSize.width
        val previewHeight = preSize.height


        //取景窗在界面上的位置
        framingRect = calculateFramingRect(Rect(0, 0, camInst.surfaceSize.width, camInst.surfaceSize.height))

        val surfaceRect = scalePreview(preSize, containerSize)
        logd("SurfaceRect: $surfaceRect", "preSize: $preSize", " containerSize: $containerSize")
        val frameInPreview = Rect(framingRect)
        frameInPreview.offset(-surfaceRect.left, -surfaceRect.top)
        logd("frameInPreview", frameInPreview)

        //取景窗在截取的图片上的位置
        previewFramingRect = Rect(
            frameInPreview.left * previewWidth / surfaceRect.width(),
            frameInPreview.top * previewHeight / surfaceRect.height(),
            frameInPreview.right * previewWidth / surfaceRect.width(),
            frameInPreview.bottom * previewHeight / surfaceRect.height()
        )
        logd("previewFramingRect", previewFramingRect)
        fireState.previewSized()
    }

    private fun scalePreview(previewSize: Size, viewfinderSize: Size): Rect {
        val scaledPreview = previewSize.scaleCrop(viewfinderSize)
        val dx = (scaledPreview.width - viewfinderSize.width) / 2
        val dy = (scaledPreview.height - viewfinderSize.height) / 2
        return Rect(-dx, -dy, scaledPreview.width - dx, scaledPreview.height - dy)
    }


    fun setTorch(on: Boolean) {
        cameraInstance?.setTorch(on)
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
