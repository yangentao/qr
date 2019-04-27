@file:Suppress("DEPRECATION", "MemberVisibilityCanBePrivate", "UNUSED_PARAMETER")

package dev.entao.kan.qr.camera

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import dev.entao.kan.appbase.App.context
import dev.entao.kan.qr.QRConfig


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

    var previewing: Boolean = false
        private set

    private var cameraRotation = -1

    var previewSize: Size = Size(0, 0)
        private set

    private var resolution: Size = Size(0, 0)

    var previewCallback: PreviewDataCallback? = null

    val isTorchOn: Boolean
        get() {
            val parameters = camera?.parameters ?: return false
            val flashMode = parameters.flashMode ?: return false
            return Camera.Parameters.FLASH_MODE_ON == flashMode || Camera.Parameters.FLASH_MODE_TORCH == flashMode
        }

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
            val reqSize = find720(paramList)
            paramList.setPreviewSize(reqSize.width, reqSize.height)
            this.resolution = reqSize
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


    fun configureCamera() {
        val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        var isCameraRotated = false
        try {
            val degrees = when (rotation) {
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

        this.previewSize = if (isCameraRotated) {
            this.resolution.rotate()
        } else {
            this.resolution
        }
        val mx = this.calculateTextureTransform(surfaceSize, previewSize)
        this.textureView.setTransform(mx)
    }

    private fun find720(ps: Camera.Parameters): Size {
        val ls = ps.supportedPreviewSizes
        val sz = ls.firstOrNull {
            it.height == 1080 && it.width == 1440
        } ?: ls.firstOrNull {
            it.height == 1080 && it.width == 1920
        } ?: ls.firstOrNull {
            it.height == 1080
        } ?: ls.firstOrNull {
            it.height == 720
        } ?: ls.firstOrNull {
            it.height == 960
        } ?: ls.filter { it.height >= 720 }.minBy { it.height } ?: ls.firstOrNull {
            it.height == 480 && it.width == 800
        } ?: ls.firstOrNull {
            it.height == 640
        } ?: ls.first()
        return Size(sz.width, sz.height)
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
        val sz = resolution
        val cb = this.previewCallback ?: return
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

    fun stopPreview() {
        if (previewing) {
            previewing = false
            camera?.stopPreview()
            focusManager?.stop()
            focusManager = null
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
                this.previewCallback = null
                previewing = false
            }
            camera?.release()
            camera = null
        }
    }

    fun requestPreview() {
        if (previewing) {
            camera?.setOneShotPreviewCallback(this)
        }
    }


}
