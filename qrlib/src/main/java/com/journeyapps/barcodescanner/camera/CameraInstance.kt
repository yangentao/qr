package com.journeyapps.barcodescanner.camera

import android.content.Context
import android.os.Handler
import com.journeyapps.barcodescanner.Size
import com.journeyapps.barcodescanner.Util
import dev.entao.qr.R
import dev.entao.qr.camera.CameraSurface
import dev.entao.qr.camera.CameraThread


/**
 * 在主线程
 */
class CameraInstance(context: Context, val readyHandler: Handler?) {

    var surface: CameraSurface? = null

    private val cameraManager: CameraManager = CameraManager(context)
    var displayConfiguration: DisplayConfiguration? = null
        set(configuration) {
            field = configuration
            cameraManager.displayConfiguration = configuration
        }
    var isOpen = false
        private set

    private val previewSize: Size?
        get() = cameraManager.previewSize

    fun open() {
        isOpen = true
        CameraThread.push {
            try {
                cameraManager.open()
            } catch (e: Exception) {
                notifyError(e)
            }
        }
    }

    fun configureCamera() {
        Util.validateMainThread()
        validateOpen()
        CameraThread.enqueue {
            try {
                cameraManager.configure()
                readyHandler?.obtainMessage(R.id.zxing_prewiew_size_ready, previewSize)?.sendToTarget()
            } catch (e: Exception) {
                notifyError(e)
            }
        }
    }

    fun startPreview() {
        Util.validateMainThread()
        validateOpen()

        CameraThread.enqueue {
            try {
                cameraManager.setPreviewDisplay(surface!!)
                cameraManager.startPreview()
            } catch (e: Exception) {
                notifyError(e)
            }
        }
    }

    fun setTorch(on: Boolean) {
        if (isOpen) {
            CameraThread.enqueue { cameraManager.setTorch(on) }
        }
    }

    fun close() {
        if (isOpen) {
            CameraThread.enqueue {
                try {
                    cameraManager.stopPreview()
                    cameraManager.close()
                } catch (e: Exception) {
                }
                CameraThread.pop()
            }
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
        readyHandler?.obtainMessage(R.id.zxing_camera_error, error)?.sendToTarget()
    }

}
