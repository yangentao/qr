@file:Suppress("DEPRECATION", "MemberVisibilityCanBePrivate")

package com.journeyapps.barcodescanner.camera

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.view.Surface
import android.view.TextureView
import com.journeyapps.barcodescanner.SourceData
import dev.entao.qr.QRConfig
import dev.entao.qr.camera.AutoFocusManager
import dev.entao.qr.camera.ConfigUtil
import dev.entao.qr.camera.PreviewDataCallback
import dev.entao.qr.camera.Size
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
class CameraInstance(context: Context, val textureView: TextureView, val surface: SurfaceTexture, val surfaceSize: Size) : Camera.PreviewCallback {


    private var camera: Camera? = null

    val isOpen: Boolean get() = camera != null

    private var cameraInfo: Camera.CameraInfo? = null

    private var focusManager: AutoFocusManager? = null

    private var previewing: Boolean = false


    private var cameraRotation = -1
    var previewSize: Size = Size(0, 0)
        private set

    val isTorchOn: Boolean
        get() {
            val parameters = camera?.parameters ?: return false
            val flashMode = parameters.flashMode ?: return false
            return Camera.Parameters.FLASH_MODE_ON == flashMode || Camera.Parameters.FLASH_MODE_TORCH == flashMode
        }

    private var resolution: Size? = null
    private var callback: PreviewDataCallback? = null


    init {
        for (i in 0 until Camera.getNumberOfCameras()) {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                camera = Camera.open(i)
                this.cameraInfo = info
                break
            }
        }
        val cam = this.camera
        if (cam != null) {
            val paramList = cam.parameters
            ConfigUtil.setFocus(paramList)
            ConfigUtil.setBarcodeSceneMode(paramList)
            ConfigUtil.setTorch(paramList, false)
            try {
                cam.parameters = paramList
            } catch (e: Exception) {

            }
            try {
                val pEx = cam.parameters
                ConfigUtil.setVideoStabilization(pEx)
                ConfigUtil.setFocusArea(pEx)
                ConfigUtil.setMetering(pEx)
                cam.parameters = pEx
            } catch (ex: Exception) {

            }
        }
    }


    fun configureCamera(cfg: DisplayConfiguration) {
        var isCameraRotated = false
        try {
            val degrees = when (cfg.rotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            val result: Int = if (cameraInfo!!.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                val a = (cameraInfo!!.orientation + degrees) % 360
                (360 - a) % 360  // compensate the mirror
            } else {  // back-facing
                (cameraInfo!!.orientation - degrees + 360) % 360
            }
            this.cameraRotation = result
            isCameraRotated = result % 180 != 0
            camera?.setDisplayOrientation(result)
        } catch (e: Exception) {
        }


        val paramList = camera!!.parameters
        val supportedSizes = paramList.supportedPreviewSizes //This method will always return a list with at least one element
        val sizeList = ArrayList<Size>()
        for (size in supportedSizes) {
            sizeList.add(Size(size.width, size.height))
        }
        val reqSize = cfg.getBestPreviewSize(sizeList, isCameraRotated)
        paramList.setPreviewSize(reqSize.width, reqSize.height)
        try {
            camera?.parameters = paramList
        } catch (e: Exception) {

        }

        val realPreviewSize = camera!!.parameters.previewSize
        val naturalPreviewSize = if (realPreviewSize == null) {
            reqSize
        } else {
            Size(realPreviewSize.width, realPreviewSize.height)
        }
        this.resolution = naturalPreviewSize

        this.previewSize = if (isCameraRotated) {
            naturalPreviewSize.rotate()
        } else {
            naturalPreviewSize
        }

        val mx = this.calculateTextureTransform(surfaceSize, previewSize)
        this.textureView.setTransform(mx)
    }

    private fun calculateTextureTransform(textureSize: Size, previewSize: Size): Matrix {
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

    override fun onPreviewFrame(data: ByteArray, camera: Camera) {
        val sz = resolution ?: return
        val cb = this.callback ?: return
        val format = camera.parameters.previewFormat
        val source = SourceData(data, sz.width, sz.height, format, cameraRotation)
        cb.onPreview(source)
    }

    fun startPreview() {
        val theCamera = camera ?: return
        theCamera.setPreviewTexture(surface)
        if (!previewing) {
            theCamera.startPreview()
            previewing = true
            focusManager = AutoFocusManager(theCamera)
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


}
