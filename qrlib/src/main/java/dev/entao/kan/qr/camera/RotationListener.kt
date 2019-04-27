package dev.entao.kan.qr.camera

import android.content.Context
import android.hardware.SensorManager
import android.view.OrientationEventListener
import android.view.WindowManager

/**
 * Hack to detect when screen rotation is reversed, since that does not cause a configuration change.
 *
 *
 * If it is changed through something other than the sensor (e.g. programmatically), this may not work.
 *
 *
 * See http://stackoverflow.com/q/9909037
 */
class RotationListener(context: Context) {
    val appContext: Context = context.applicationContext
    private val windowManager: WindowManager get() = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var lastRotation: Int = 0
    private var orientationEventListener: OrientationEventListener? = null
    private var callback: RotationCallback? = null

    fun listen(callback: RotationCallback) {
        stop()
        this.callback = callback
        this.orientationEventListener = object : OrientationEventListener(appContext, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                val localCallback = this@RotationListener.callback
                if (localCallback != null) {
                    val newRotation = windowManager.defaultDisplay.rotation
                    if (newRotation != lastRotation) {
                        lastRotation = newRotation
                        localCallback.onRotationChanged(newRotation)
                    }
                }
            }
        }
        this.orientationEventListener?.enable()
        lastRotation = windowManager.defaultDisplay.rotation
    }

    fun stop() {
        this.orientationEventListener?.disable()
        this.orientationEventListener = null
        this.callback = null
    }
}
