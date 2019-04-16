package com.journeyapps.barcodescanner.camera

import android.content.Context
import android.os.Handler
import android.view.SurfaceHolder
import com.journeyapps.barcodescanner.Size
import com.journeyapps.barcodescanner.Util
import dev.entao.qr.R
import dev.entao.qr.camera.CameraThread


/**
 * 必须在主线程
 */
class CameraInstance(context: Context) {

    var surface: CameraSurface? = null

    // The CameraManager is not thread-safe, and must only be used from the CameraThread.
    private val cameraManager: CameraManager = CameraManager(context)
    private var readyHandler: Handler? = null
    var displayConfiguration: DisplayConfiguration? = null
        set(configuration) {
            field = configuration
            cameraManager!!.displayConfiguration = configuration
        }
    var isOpen = false
        private set
    //open 前有效
    val cameraSettings: CameraSettings get() = this.cameraManager.cameraSettings

    private val previewSize: Size?
        get() = cameraManager.previewSize

    private val opener = Runnable {
        try {
            cameraManager.open()
        } catch (e: Exception) {
            notifyError(e)
        }
    }

    private val configure = Runnable {
        try {
            cameraManager.configure()
            if (readyHandler != null) {
                readyHandler!!.obtainMessage(R.id.zxing_prewiew_size_ready, previewSize).sendToTarget()
            }
        } catch (e: Exception) {
            notifyError(e)
        }
    }

    private val previewStarter = Runnable {
        try {
            cameraManager.setPreviewDisplay(surface!!)
            cameraManager.startPreview()
        } catch (e: Exception) {
            notifyError(e)
        }
    }

    private val closer = Runnable {
        try {
            cameraManager.stopPreview()
            cameraManager.close()
        } catch (e: Exception) {
        }
        CameraThread.pop()
    }


    fun setReadyHandler(readyHandler: Handler) {
        this.readyHandler = readyHandler
    }

    fun setSurfaceHolder(surfaceHolder: SurfaceHolder) {
        surface = CameraSurface(surfaceHolder)
    }

    fun open() {
        Util.validateMainThread()

        isOpen = true

        CameraThread.push(opener)
    }

    fun configureCamera() {
        Util.validateMainThread()
        validateOpen()

        CameraThread.enqueue(configure)
    }

    fun startPreview() {
        Util.validateMainThread()
        validateOpen()

        CameraThread.enqueue(previewStarter)
    }

    fun setTorch(on: Boolean) {
        if (isOpen) {
            CameraThread.enqueue { cameraManager.setTorch(on) }
        }
    }

    fun close() {
        if (isOpen) {
            CameraThread.enqueue(closer)
        }
        isOpen = false
    }

    fun requestPreview(callback: PreviewCallback) {
        validateOpen()

        CameraThread.enqueue { cameraManager.requestPreviewFrame(callback) }
    }

    private fun validateOpen() {
        if (!isOpen) {
            throw IllegalStateException("CameraInstance is not open")
        }
    }

    private fun notifyError(error: Exception) {
        if (readyHandler != null) {
            readyHandler!!.obtainMessage(R.id.zxing_camera_error, error).sendToTarget()
        }
    }

    companion object {
        private val TAG = CameraInstance::class.java.simpleName
    }
}
