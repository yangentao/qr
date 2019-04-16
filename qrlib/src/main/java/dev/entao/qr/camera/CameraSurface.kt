package dev.entao.qr.camera

import android.graphics.SurfaceTexture
import android.hardware.Camera

/**
 * A surface on which a camera preview is displayed.
 *
 *
 * This wraps either a SurfaceHolder or a SurfaceTexture.
 */
class CameraSurface(var surfaceTexture: SurfaceTexture?) {
    fun setPreview(camera: Camera) {
        camera.setPreviewTexture(surfaceTexture)
    }
}
