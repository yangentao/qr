@file:Suppress("DEPRECATION")

package com.journeyapps.barcodescanner.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.util.Log
import android.view.Surface
import com.journeyapps.barcodescanner.SourceData
import dev.entao.appbase.App.context
import dev.entao.qr.QRConfig
import dev.entao.qr.camera.*
import java.util.*


/**
 * Wrapper to manage the Camera. This is not thread-safe, and the methods must always be called
 * from the same thread.
 * Call order:
 * 1. setCameraSettings()
 * 2. open(), set desired preview size (any order)
 * 3. configure(), setPreviewDisplay(holder) (any order)
 * 4. startPreview()
 * 5. requestPreviewFrame (repeat)
 * 6. stopPreview()
 * 7. close()
 */
class CameraInstance(context: Context ) : Camera.PreviewCallback {


    private var camera: Camera? = null

    val isOpen: Boolean get() = camera != null

    private var cameraInfo: Camera.CameraInfo? = null

    private var focusManager: AutoFocusManager? = null
    private var lightManager: LightManager? = null

    private var previewing: Boolean = false


    private var displayConfiguration: DisplayConfiguration? = null

    val configured: Boolean get() = displayConfiguration != null

    // Actual chosen preview size
    private var requestedPreviewSize: Size? = null
    /**
     * Actual preview size in *natural camera* orientation. null if not determined yet.
     *
     * @return preview size
     */
    var naturalPreviewSize: Size? = null
        private set

    /**
     * @return the camera rotation relative to display rotation, in degrees. Typically 0 if the
     * display is in landscape orientation.
     */
    var cameraRotation = -1
        private set    // camera rotation vs display rotation


    /**
     * @return true if the camera rotation is perpendicular to the current display rotation.
     */
    val isCameraRotated: Boolean
        get() {
            if (cameraRotation == -1) {
                throw IllegalStateException("Rotation not calculated yet. Call configure() first.")
            }
            return cameraRotation % 180 != 0
        }


    /**
     * Actual preview size in *current display* rotation. null if not determined yet.
     *
     * @return preview size
     */
    val previewSize: Size?
        get() = when {
            naturalPreviewSize == null -> null
            this.isCameraRotated -> naturalPreviewSize!!.rotate()
            else -> naturalPreviewSize
        }

    val isTorchOn: Boolean
        get() {
            val parameters = camera!!.parameters
            if (parameters != null) {
                val flashMode = parameters.flashMode
                return flashMode != null && (Camera.Parameters.FLASH_MODE_ON == flashMode || Camera.Parameters.FLASH_MODE_TORCH == flashMode)
            } else {
                return false
            }
        }

    private var resolution: Size? = null
    private var callback: PreviewDataCallback? = null

    override fun onPreviewFrame(data: ByteArray, camera: Camera) {
        val sz = resolution ?: return
        val cb = this.callback ?: return
        val format = camera.parameters.previewFormat
        val source = SourceData(data, sz.width, sz.height, format, cameraRotation)
        cb.onPreview(source)
    }

    fun open() {
        for (i in 0 until Camera.getNumberOfCameras()) {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                camera = Camera.open(i)
                this.cameraInfo = info
                return
            }
        }
    }

    fun configureCamera(cfg: DisplayConfiguration) :Size {
        this.displayConfiguration = cfg
        try {
            this.cameraRotation = calculateDisplayRotation()
            setCameraDisplayOrientation(cameraRotation)
        } catch (e: Exception) {
            Log.w("Camera", "Failed to set rotation.")
        }

        try {
            setDesiredParameters(false)
        } catch (e: Exception) {
            try {
                setDesiredParameters(true)
            } catch (e2: Exception) {
            }
        }

        val realPreviewSize = camera!!.parameters.previewSize
        naturalPreviewSize = if (realPreviewSize == null) {
            requestedPreviewSize
        } else {
            Size(realPreviewSize.width, realPreviewSize.height)
        }
        this.resolution = naturalPreviewSize

        return previewSize!!
    }

    fun startPreview(texure: SurfaceTexture) {
        val theCamera = camera ?: return
        theCamera.setPreviewTexture(texure)
        if (!previewing) {
            theCamera.startPreview()
            previewing = true
            focusManager = AutoFocusManager(theCamera)
            if (QRConfig.isAutoTorchEnabled) {
                lightManager = LightManager(context, this)
                lightManager?.start()
            }
        }

    }

    fun setTorch(on: Boolean) {
        val ca = this.camera ?: return
        val isOn = isTorchOn
        if (on != isOn) {
            focusManager?.stop()

            val parameters = ca.parameters
            ConfigUtil.setTorch(parameters, on)
            if (QRConfig.isExposureEnabled) {
                ConfigUtil.setBestExposure(parameters, on)
            }
            ca.parameters = parameters
            focusManager?.start()
        }
    }

    fun close() {
        if (isOpen) {
            focusManager?.stop()
            focusManager = null
            lightManager?.stop()
            lightManager = null
            if (previewing) {
                camera?.stopPreview()
                this.callback = null
                previewing = false
            }
            camera?.release()
            camera = null
        }
    }

    fun requestPreview(callback: PreviewDataCallback) {
        val theCamera = camera ?: return
        if (previewing) {
            this.callback = callback
            theCamera.setOneShotPreviewCallback(this)
        }
    }


    private fun setDesiredParameters(safeMode: Boolean) {
        val parameters = camera!!.parameters
        ConfigUtil.setFocus(parameters)
        ConfigUtil.setBarcodeSceneMode(parameters)

        if (!safeMode) {
            ConfigUtil.setTorch(parameters, false)
            ConfigUtil.setVideoStabilization(parameters)
            ConfigUtil.setFocusArea(parameters)
            ConfigUtil.setMetering(parameters)
        }

        val previewSizes = getPreviewSizes(parameters)
        if (previewSizes.isEmpty()) {
            requestedPreviewSize = null
        } else {
            requestedPreviewSize = displayConfiguration!!.getBestPreviewSize(previewSizes, isCameraRotated)

            parameters.setPreviewSize(requestedPreviewSize!!.width, requestedPreviewSize!!.height)
        }



        camera!!.parameters = parameters
    }


    private fun calculateDisplayRotation(): Int {
        // http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
        val rotation = displayConfiguration!!.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        var result: Int
        if (cameraInfo!!.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (cameraInfo!!.orientation + degrees) % 360
            result = (360 - result) % 360  // compensate the mirror
        } else {  // back-facing
            result = (cameraInfo!!.orientation - degrees + 360) % 360
        }
        Log.i("Camera", "Camera Display Orientation: $result")
        return result
    }

    private fun setCameraDisplayOrientation(rotation: Int) {
        camera!!.setDisplayOrientation(rotation)
    }


    private fun getPreviewSizes(parameters: Camera.Parameters): List<Size> {
        val supportedSizes = parameters.supportedPreviewSizes
        val ls = ArrayList<Size>()
        if (supportedSizes == null) {
            val sz = parameters.previewSize ?: return ls
            ls.add(Size(sz.width, sz.height))
        } else {
            for (size in supportedSizes) {
                ls.add(Size(size.width, size.height))
            }
        }
        return ls
    }

}
