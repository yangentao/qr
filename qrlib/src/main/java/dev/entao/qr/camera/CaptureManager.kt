@file:Suppress("UNUSED_PARAMETER")

package dev.entao.qr.camera

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.Surface
import android.view.WindowManager
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import dev.entao.qr.R
import dev.entao.qr.ScanConfig
import dev.entao.util.Task

/**
 * Manages barcode scanning for a CaptureActivity. This class may be used to have a custom Activity
 * (e.g. with a customized look and feel, or a different superclass), but not the barcode scanning
 * process itself.
 *
 *
 * This is intended for an Activity that is dedicated to capturing a single barcode and returning
 * it via setResult(). For other use cases, use DefaultBarcodeScannerView or BarcodeView directly.
 *
 *
 * The following is managed by this class:
 * - Orientation lock
 * - InactivityTimer
 * - BeepManager
 * - Initializing from an Intent (via IntentIntegrator)
 * - Setting the result and finishing the Activity when a barcode is scanned
 * - Displaying camera errors
 */
class CaptureManager(private val activity: Activity, private val barcodeView: DecoratedBarcodeView, config: ScanConfig) : CameraPreview.StateListener,
    BarcodeCallback {
    private var orientationLock = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    private var destroyed = false

    private val inactivityTimer: InactivityTimer = InactivityTimer(activity, Runnable {
        finish()
    })

    private val beepManager: BeepManager = BeepManager(activity)

    private val handler: Handler = Handler()

    var onResult: (BarcodeResult) -> Unit = {}
    var onFinish: () -> Unit = {
        activity.finish()
    }


    init {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        barcodeView.barcodeView.addStateListener(this)
        lockOrientation()
        beepManager.isBeepEnabled = config.beep
        beepManager.updatePrefs()
        if (config.timeout > 0) {
            handler.postDelayed({
                finish()
            }, config.timeout.toLong())
        }
    }


    /**
     * Lock display to current orientation.
     */
    private fun lockOrientation() {
        // Only get the orientation if it's not locked to one yet.
        if (this.orientationLock == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            // Adapted from http://stackoverflow.com/a/14565436
            val display = activity.windowManager.defaultDisplay
            val rotation = display.rotation
            val baseOrientation = activity.resources.configuration.orientation
            val orientation: Int = if (baseOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
            } else if (baseOrientation == Configuration.ORIENTATION_PORTRAIT) {
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_270) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                }
            } else {
                0
            }

            this.orientationLock = orientation
        }
        //noinspection ResourceType
        activity.requestedOrientation = this.orientationLock
    }

    override fun previewSized() {

    }

    override fun previewStarted() {

    }

    override fun previewStopped() {

    }

    override fun cameraError(error: Exception) {
        displayFrameworkBugMessageAndExit()
    }

    override fun barcodeResult(result: BarcodeResult) {
        barcodeView.pause()
        beepManager.playBeepSoundAndVibrate()

        Task.foreDelay(DELAY_BEEP) {
            finish()
            onResult(result)
        }
    }

    override fun possibleResultPoints(resultPoints: List<ResultPoint>) {

    }


    fun decode() {
        barcodeView.decodeSingle(this)
    }

    fun onResume() {
        if (Build.VERSION.SDK_INT >= 23) {
            openCameraWithPermission()
        } else {
            barcodeView.resume()
        }
        beepManager.updatePrefs()
        inactivityTimer.start()
    }

    private var askedPermission = false

    @TargetApi(23)
    private fun openCameraWithPermission() {
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            barcodeView.resume()
        } else if (!askedPermission) {
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraPermissionReqCode)
            askedPermission = true
        } else {
            // Wait for permission result
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == cameraPermissionReqCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                barcodeView.resume()
            } else {
                displayFrameworkBugMessageAndExit()
            }
        }
    }


    fun onPause() {
        barcodeView.pause()
        inactivityTimer.cancel()
        beepManager.close()
    }

    fun onDestroy() {
        destroyed = true
        inactivityTimer.cancel()
    }


    fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SAVED_ORIENTATION_LOCK, this.orientationLock)
    }

    private fun finish() {
        onFinish()
    }


    private fun displayFrameworkBugMessageAndExit() {
        if (activity.isFinishing || this.destroyed) {
            return
        }
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(activity.getString(R.string.zxing_app_name))
        builder.setMessage(activity.getString(R.string.zxing_msg_camera_framework_bug))
        builder.setPositiveButton(R.string.zxing_button_ok) { _, _ -> finish() }
        builder.setOnCancelListener { finish() }
        builder.show()
    }

    companion object {
        const val cameraPermissionReqCode = 250
        private const val SAVED_ORIENTATION_LOCK = "SAVED_ORIENTATION_LOCK"
        private const val DELAY_BEEP: Long = 150


    }
}
