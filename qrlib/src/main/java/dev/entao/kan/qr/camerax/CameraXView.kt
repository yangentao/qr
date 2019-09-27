package dev.entao.kan.qr.camerax

import android.content.Context
import android.graphics.Matrix
import android.util.Rational
import android.view.Surface
import android.view.TextureView
import androidx.camera.core.*
import androidx.lifecycle.LifecycleOwner

class CameraXView(context: Context) : TextureView(context) {
    var onResult: (String) -> Unit = {}

    fun start(lifeOwner: LifecycleOwner) {
        val dm = context.resources.displayMetrics
        val screenAspectRatio = Rational(dm.widthPixels, dm.heightPixels)


        val preConfig = PreviewConfig.Builder().apply {
            setTargetAspectRatio(screenAspectRatio)
            setTargetRotation(this@CameraXView.display.rotation)
            setLensFacing(CameraX.LensFacing.BACK)
        }.build()
        val pre = Preview(preConfig)
        pre.setOnPreviewOutputUpdateListener {
            this.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        val alConfig = ImageAnalysisConfig.Builder().apply {
            setTargetAspectRatio(screenAspectRatio)
            setTargetRotation(this@CameraXView.display.rotation)
            setLensFacing(CameraX.LensFacing.BACK)
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            this.setImageQueueDepth(2)
        }.build()
        val al = ImageAnalysis(alConfig).apply {
            val a = QRImageAnalysis()
            a.onResult = this@CameraXView.onResult
            analyzer = a
        }


        CameraX.bindToLifecycle(lifeOwner, pre, al)
    }

    private fun updateTransform() {
        val ddd = this.display ?: return
        val deg = when (ddd.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        val m = Matrix()
        val cX = this.width / 2f
        val cY = this.height / 2f
        m.postRotate(-deg.toFloat(), cX, cY)
        this.setTransform(m)

    }

}