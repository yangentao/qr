/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.journeyapps.barcodescanner.camera

import android.content.Context
import android.hardware.Camera
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import com.google.zxing.client.android.camera.CameraConfigurationUtils
import com.journeyapps.barcodescanner.Size
import com.journeyapps.barcodescanner.SourceData
import dev.entao.qr.camera.AutoFocusManager
import dev.entao.qr.camera.CameraSettings
import dev.entao.qr.camera.ConfigUtil
import dev.entao.qr.camera.LightManager
import java.io.IOException
import java.util.*

/**
 * Wrapper to manage the Camera. This is not thread-safe, and the methods must always be called
 * from the same thread.
 *
 *
 *
 *
 * Call order:
 *
 *
 * 1. setCameraSettings()
 * 2. open(), set desired preview size (any order)
 * 3. configure(), setPreviewDisplay(holder) (any order)
 * 4. startPreview()
 * 5. requestPreviewFrame (repeat)
 * 6. stopPreview()
 * 7. close()
 */
class CameraManager(private val context: Context) {

    /**
     * Returns the Camera. This returns null if the camera is not opened yet, failed to open, or has
     * been closed.
     *
     * @return the Camera
     */
    var camera: Camera? = null
        private set
    private var cameraInfo: Camera.CameraInfo? = null

    private var focusManager: AutoFocusManager? = null
    private var lightManager: LightManager? = null

    private var previewing: Boolean = false
    private var defaultParameters: String? = null


    var displayConfiguration: DisplayConfiguration? = null

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
     * Preview frames are delivered here, which we pass on to the registered handler. Make sure to
     * clear the handler so it will only receive one message.
     */
    private val cameraPreviewCallback: CameraPreviewCallback

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

    private val defaultCameraParameters: Camera.Parameters
        get() {
            val parameters = camera!!.parameters
            if (defaultParameters == null) {
                defaultParameters = parameters.flatten()
            } else {
                parameters.unflatten(defaultParameters)
            }
            return parameters
        }

    /**
     * This returns false if the camera is not opened yet, failed to open, or has
     * been closed.
     */
    val isOpen: Boolean
        get() = camera != null

    /**
     * Actual preview size in *current display* rotation. null if not determined yet.
     *
     * @return preview size
     */
    val previewSize: Size?
        get() = if (naturalPreviewSize == null) {
            null
        } else if (this.isCameraRotated) {
            naturalPreviewSize!!.rotate()
        } else {
            naturalPreviewSize
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


    private inner class CameraPreviewCallback : Camera.PreviewCallback {
        private var callback: PreviewCallback? = null

        private var resolution: Size? = null

        fun setResolution(resolution: Size?) {
            this.resolution = resolution
        }

        fun setCallback(callback: PreviewCallback?) {
            this.callback = callback
        }

        override fun onPreviewFrame(data: ByteArray, camera: Camera) {
            val cameraResolution = resolution
            val callback = this.callback
            if (cameraResolution != null && callback != null) {
                val format = camera.parameters.previewFormat
                val source = SourceData(data, cameraResolution.width, cameraResolution.height, format, cameraRotation)
                callback.onPreview(source)
            } else {
                Log.d(TAG, "Got preview callback, but no handler or resolution available")
            }
        }
    }

    init {
        cameraPreviewCallback = CameraPreviewCallback()
    }


    fun open() {
        for (i in 0 until Camera.getNumberOfCameras()) {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                camera = Camera.open(i)
                this.cameraInfo = info
            }
        }
    }

    /**
     * Configure the camera parameters, including preview size.
     *
     *
     * The camera must be opened before calling this.
     *
     *
     * Must be called from camera thread.
     */
    fun configure() {
        if (camera != null) {
            setParameters()
        }
    }

    /**
     * Must be called from camera thread.
     */
    @Throws(IOException::class)
    fun setPreviewDisplay(holder: SurfaceHolder) {
        setPreviewDisplay(CameraSurface(holder))
    }

    @Throws(IOException::class)
    fun setPreviewDisplay(surface: CameraSurface) {
        surface.setPreview(camera)
    }

    fun startPreview() {
        val theCamera = camera ?: return
        if (!previewing) {
            theCamera.startPreview()
            previewing = true
            focusManager = AutoFocusManager(theCamera)
            if (CameraSettings.isAutoTorchEnabled) {
                lightManager = LightManager(context, this)
                lightManager?.start()
            }
        }
    }

    /**
     * Tells the camera to stop drawing preview frames.
     *
     *
     * Must be called from camera thread.
     */
    fun stopPreview() {
        focusManager?.stop()
        focusManager = null
        lightManager?.stop()
        lightManager = null
        if (previewing) {
            camera?.stopPreview()
            cameraPreviewCallback.setCallback(null)
            previewing = false
        }
    }

    /**
     * Closes the camera driver if still in use.
     *
     *
     * Must be called from camera thread.
     */
    fun close() {
        camera?.release()
        camera = null
    }

    private fun setDesiredParameters(safeMode: Boolean) {
        val parameters = defaultCameraParameters
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

        if (Build.DEVICE == "glass-1") {
            // We need to set the FPS on Google Glass devices, otherwise the preview is scrambled.
            // FIXME - can/should we do this for other devices as well?
            CameraConfigurationUtils.setBestPreviewFPS(parameters)
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
        Log.i(TAG, "Camera Display Orientation: $result")
        return result
    }

    private fun setCameraDisplayOrientation(rotation: Int) {
        camera!!.setDisplayOrientation(rotation)
    }

    private fun setParameters() {
        try {
            this.cameraRotation = calculateDisplayRotation()
            setCameraDisplayOrientation(cameraRotation)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set rotation.")
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
        if (realPreviewSize == null) {
            naturalPreviewSize = requestedPreviewSize
        } else {
            naturalPreviewSize = Size(realPreviewSize.width, realPreviewSize.height)
        }
        cameraPreviewCallback.setResolution(naturalPreviewSize)
    }

    /**
     * A single preview frame will be returned to the supplied callback.
     *
     *
     * The thread on which this called is undefined, so a Handler should be used to post the result
     * to the correct thread.
     *
     * @param callback The callback to receive the preview.
     */
    fun requestPreviewFrame(callback: PreviewCallback) {
        val theCamera = camera
        if (theCamera != null && previewing) {
            cameraPreviewCallback.setCallback(callback)
            theCamera.setOneShotPreviewCallback(cameraPreviewCallback)
        }
    }

    fun setTorch(on: Boolean) {
        if (camera != null) {
            val isOn = isTorchOn
            if (on != isOn) {
                if (focusManager != null) {
                    focusManager!!.stop()
                }

                val parameters = camera!!.parameters
                CameraConfigurationUtils.setTorch(parameters, on)
                if (CameraSettings.isExposureEnabled) {
                    CameraConfigurationUtils.setBestExposure(parameters, on)
                }
                camera!!.parameters = parameters

                if (focusManager != null) {
                    focusManager!!.start()
                }
            }
        }
    }

    companion object {

        private val TAG = CameraManager::class.java.simpleName

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
}
