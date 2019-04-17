@file:Suppress("MemberVisibilityCanBePrivate")

package com.journeyapps.barcodescanner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import android.view.WindowManager
import com.journeyapps.barcodescanner.camera.CameraInstance
import com.journeyapps.barcodescanner.camera.DisplayConfiguration
import dev.entao.log.logd
import dev.entao.qr.QRConfig
import dev.entao.qr.camera.RotationCallback
import dev.entao.qr.camera.RotationListener
import dev.entao.qr.camera.Size
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
open class CameraPreview(context: Context) : ViewGroup(context), TextureView.SurfaceTextureListener {

    var cameraInstance: CameraInstance? = null
        private set

    private lateinit var textureView: TextureView

    /**
     * The preview typically starts being active a while after calling resume(), and stops
     * when calling pause().
     *
     * @return true if the preview is active
     */
    var isPreviewActive = false
        private set

    private var rotationListener: RotationListener = RotationListener(context)
    private var openedOrientation = -1

    private val stateListeners = ArrayList<StateListener>()

    // Size of this container, non-null after layout is performed
    private var containerSize: Size? = null

    // Size of the preview resolution
    private var previewSize: Size? = null

    // Rect placing the preview surface
    private var surfaceRect: Rect? = null

    // Size of the current surface. non-null if the surface is ready
    private var currentSurfaceSize: Size? = null

    // Framing rectangle relative to this view
    /**
     * The framing rectangle, relative to this view. Use to draw the rectangle.
     *
     *
     * Will never be null while the preview is active.
     *
     * @return the framing rect, or null
     * @see .isPreviewActive
     */
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

    // Size of the framing rectangle. If null, defaults to using a margin percentage.
    /**
     * Set an exact size for the framing rectangle. It will be centered in the view.
     *
     * @param framingRectSize the size
     */
    var framingRectSize: Size? = null

    // Fraction of the width / heigth to use as a margin. This fraction is used on each size, so
    // must be smaller than 0.5;
    /**
     * The the fraction of the width/height of view to be used as a margin for the framing rect.
     * This is ignored if framingRectSize is specified.
     *
     * @param marginFraction the fraction
     */
    var marginFraction = 0.1
        set(marginFraction) {
            if (marginFraction >= 0.5) {
                throw IllegalArgumentException("The margin fraction must be less than 0.5")
            }
            field = marginFraction
        }


    private var torchOn = false


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
        val framingRectWidth = QRConfig.width
        val framingRectHeight = QRConfig.height

        if (framingRectWidth > 0 && framingRectHeight > 0) {
            this.framingRectSize = Size(framingRectWidth, framingRectHeight)
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        onSurfaceTextureSizeChanged(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        currentSurfaceSize = Size(width, height)
        startPreviewIfReady()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setBackgroundColor(Color.BLACK)
        textureView = TextureView(context)
        textureView.surfaceTextureListener = this
        addView(textureView)
    }


    private fun rotationChanged() {
        // Confirm that it did actually change
        if (isActive && displayRotation != openedOrientation) {
            pause()
            resume()
        }
    }


    /**
     * Add a listener to be notified of changes to the preview state, as well as camera errors.
     *
     * @param listener the listener
     */
    fun addStateListener(listener: StateListener) {
        stateListeners.add(listener)
    }

    private fun calculateFrames(displayConfiguration: DisplayConfiguration) {
        if (containerSize == null || previewSize == null || displayConfiguration == null) {
            previewFramingRect = null
            framingRect = null
            surfaceRect = null
            throw IllegalStateException("containerSize or previewSize is not set yet")
        }
        val preSize = previewSize ?: return

        val previewWidth = preSize.width
        val previewHeight = preSize.height

        val width = containerSize!!.width
        val height = containerSize!!.height

        surfaceRect = displayConfiguration!!.scalePreview(preSize)

        val container = Rect(0, 0, width, height)
        framingRect = calculateFramingRect(container, surfaceRect)
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

    /**
     * Call this on the main thread, while the preview is running.
     *
     * @param on true to turn on the torch
     */
    fun setTorch(on: Boolean) {
        torchOn = on
        cameraInstance?.setTorch(on)
    }

    private fun containerSized(containerSize: Size) {
        this.containerSize = containerSize
        val ci = cameraInstance ?: return
        if (ci.configured) {
            return
        }
        val displayConfiguration = DisplayConfiguration(displayRotation, containerSize)
        this.previewSize = ci.configureCamera(displayConfiguration)
        logd("PreviewSize: ", this.previewSize?.width, this.previewSize?.height)
        calculateFrames(displayConfiguration)


        requestLayout()
        startPreviewIfReady()
        if (torchOn) {
            ci.setTorch(torchOn)
        }
    }


    /**
     * Calculate transformation for the TextureView.
     *
     *
     * An identity matrix would cause the preview to be scaled up/down to fill the TextureView.
     *
     * @param textureSize the size of the textureView
     * @param previewSize the camera preview resolution
     * @return the transform matrix for the TextureView
     */
    protected fun calculateTextureTransform(textureSize: Size, previewSize: Size): Matrix {
        val ratioTexture = textureSize.width.toFloat() / textureSize.height.toFloat()
        val ratioPreview = previewSize.width.toFloat() / previewSize.height.toFloat()

        val scaleX: Float
        val scaleY: Float

        // We scale so that either width or height fits exactly in the TextureView, and the other
        // is bigger (cropped).
        if (ratioTexture < ratioPreview) {
            scaleX = ratioPreview / ratioTexture
            scaleY = 1f
        } else {
            scaleX = 1f
            scaleY = ratioTexture / ratioPreview
        }

        val matrix = Matrix()

        matrix.setScale(scaleX, scaleY)

        // Center the preview
        val scaledWidth = textureSize.width * scaleX
        val scaledHeight = textureSize.height * scaleY
        val dx = (textureSize.width - scaledWidth) / 2
        val dy = (textureSize.height - scaledHeight) / 2

        // Perform the translation on the scaled preview
        matrix.postTranslate(dx, dy)

        return matrix
    }

    private fun startPreviewIfReady() {
        if (currentSurfaceSize != null && previewSize != null && surfaceRect != null) {
            if (textureView.surfaceTexture != null) {
                if (previewSize != null) {
                    val transform = calculateTextureTransform(Size(textureView.width, textureView.height), previewSize!!)
                    textureView.setTransform(transform)
                }

                startCameraPreview(textureView.surfaceTexture)
            } else {
                // Surface is not the correct size yet
            }
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        containerSized(Size(r - l, b - t))
        textureView.layout(0, 0, width, height)
    }


    /**
     * Start the camera preview and decoding. Typically this should be called from the Activity's
     * onResume() method.
     *
     *
     * Call from UI thread only.
     */
    fun resume() {
        if (cameraInstance != null) {
            Log.w(TAG, "initCamera called twice")
            return
        }
        cameraInstance = CameraInstance(context)

        // Keep track of the orientation we opened at, so that we don't reopen the camera if we
        // don't need to.
        openedOrientation = displayRotation

        if (currentSurfaceSize != null) {
            // The activity was paused but not stopped, so the surface still exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            startPreviewIfReady()
        } else {
            if (textureView.isAvailable) {
                this.onSurfaceTextureAvailable(textureView.surfaceTexture, textureView.width, textureView.height)
            } else {
                textureView.surfaceTextureListener = this
            }
        }

        // To trigger surfaceSized again
        requestLayout()
        rotationListener.listen(object : RotationCallback {
            override fun onRotationChanged(rotation: Int) {
                Task.foreDelay(ROTATION_LISTENER_DELAY_MS.toLong()) {
                    rotationChanged()
                }
            }
        })
    }

    open fun pause() {
        openedOrientation = -1
        cameraInstance?.close()
        cameraInstance = null
        isPreviewActive = false

        if (currentSurfaceSize == null) {
            textureView.surfaceTextureListener = null
        }

        this.containerSize = null
        this.previewSize = null
        this.previewFramingRect = null
        rotationListener.stop()

        fireState.previewStopped()
    }


    private fun startCameraPreview(texure: SurfaceTexture) {
        if (!isPreviewActive && cameraInstance != null) {
            Log.i(TAG, "Starting preview")
            cameraInstance!!.startPreview(texure)
            isPreviewActive = true

            previewStarted()
            fireState.previewStarted()
        }
    }

    /**
     * Called when the preview is started. Override this to start decoding work.
     */
    protected open fun previewStarted() {

    }

    /**
     * Calculate framing rectangle, relative to the preview frame.
     *
     *
     * Note that the SurfaceView may be larger than the container.
     *
     *
     * Override this for more control over the framing rect calculations.
     *
     * @param container this container, with left = top = 0
     * @param surface   the SurfaceView, relative to this container
     * @return the framing rect, relative to this container
     */
    protected fun calculateFramingRect(container: Rect, surface: Rect?): Rect {
        // intersection is the part of the container that is used for the preview
        val intersection = Rect(container)
        val okk = intersection.intersect(surface)

        if (framingRectSize != null) {
            // Specific size is specified. Make sure it's not larger than the container or surface.
            val horizontalMargin = Math.max(0, (intersection.width() - framingRectSize!!.width) / 2)
            val verticalMargin = Math.max(0, (intersection.height() - framingRectSize!!.height) / 2)
            intersection.inset(horizontalMargin, verticalMargin)
            return intersection
        }
        // margin as 10% (default) of the smaller of width, height
        val margin = Math.min(intersection.width() * this.marginFraction, intersection.height() * this.marginFraction).toInt()
        intersection.inset(margin, margin)
        if (intersection.height() > intersection.width()) {
            // We don't want a frame that is taller than wide.
            intersection.inset(0, (intersection.height() - intersection.width()) / 2)
        }
        return intersection
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()

        val myState = Bundle()
        myState.putParcelable("super", superState)
        myState.putBoolean("torch", torchOn)
        return myState
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        val superState = state.getParcelable<Parcelable>("super")
        super.onRestoreInstanceState(superState)
        val torch = state.getBoolean("torch")
        setTorch(torch)
    }

    companion object {

        private val TAG = CameraPreview::class.java.simpleName

        // Delay after rotation change is detected before we reorientate ourselves.
        // This is to avoid double-reinitialization when the Activity is destroyed and recreated.
        private const val ROTATION_LISTENER_DELAY_MS = 250
    }
}
