package dev.entao.qr.camera

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.view.SurfaceHolder

/**
 * A surface on which a camera preview is displayed.
 *
 *
 * This wraps either a SurfaceHolder or a SurfaceTexture.
 */
class CameraSurface {
    var surfaceHolder: SurfaceHolder? = null
    var surfaceTexture: SurfaceTexture? = null

    constructor(surfaceHolder: SurfaceHolder) {
        this.surfaceHolder = surfaceHolder
    }

    constructor(surfaceTexture: SurfaceTexture?) {
        this.surfaceTexture = surfaceTexture
    }

    fun setPreview(camera: Camera) {
        if (surfaceHolder != null) {
            camera.setPreviewDisplay(surfaceHolder)
        } else {
            camera.setPreviewTexture(surfaceTexture)
        }
    }
}
