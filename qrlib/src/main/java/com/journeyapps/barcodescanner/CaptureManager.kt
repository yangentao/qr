@file:Suppress("UNUSED_PARAMETER")

package com.journeyapps.barcodescanner

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
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.google.zxing.ResultPoint
import dev.entao.qr.R
import dev.entao.qr.ScanConfig
import dev.entao.qr.camera.BeepManager
import dev.entao.qr.camera.InactivityTimer
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
class CaptureManager(private val activity: Activity, private val barcodeView: DecoratedBarcodeView) {
    private var orientationLock = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var returnBarcodeImagePath = false

    private var destroyed = false

    private val inactivityTimer: InactivityTimer = InactivityTimer(activity, Runnable {
        Log.d(TAG, "Finishing due to inactivity")
        finish()
    })

    private val beepManager: BeepManager = BeepManager(activity)

    private val handler: Handler = Handler()

    var onResult: (BarcodeResult) -> Unit = {}


    private val stateListener = object : CameraPreview.StateListener {
        override fun previewSized() {

        }

        override fun previewStarted() {

        }

        override fun previewStopped() {

        }

        override fun cameraError(error: Exception) {
            displayFrameworkBugMessageAndExit()
        }
    }

    init {
        barcodeView.barcodeView.addStateListener(stateListener)
    }

    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            barcodeView.pause()
            beepManager.playBeepSoundAndVibrate()

            Task.foreDelay(DELAY_BEEP) {
                activity.finish()
                onResult(result)
            }
        }

        override fun possibleResultPoints(resultPoints: List<ResultPoint>) {

        }
    }

    /**
     * Perform initialization, according to preferences set in the intent.
     */
    fun initializeFromIntent(config: ScanConfig) {
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lockOrientation()
        barcodeView.initializeFromIntent(config)
        beepManager.isBeepEnabled = config.beep
        beepManager.updatePrefs()
        if (config.timeout > 0) {
            val runnable = Runnable { returnResultTimeout() }
            handler.postDelayed(runnable, config.timeout.toLong())
        }

        if (config.returnImage) {
            returnBarcodeImagePath = true
        }
    }

    /**
     * Lock display to current orientation.
     */
    protected fun lockOrientation() {
        // Only get the orientation if it's not locked to one yet.
        if (this.orientationLock == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            // Adapted from http://stackoverflow.com/a/14565436
            val display = activity.windowManager.defaultDisplay
            val rotation = display.rotation
            val baseOrientation = activity.resources.configuration.orientation
            var orientation = 0
            if (baseOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90) {
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                }
            } else if (baseOrientation == Configuration.ORIENTATION_PORTRAIT) {
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_270) {
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                }
            }

            this.orientationLock = orientation
        }
        //noinspection ResourceType
        activity.requestedOrientation = this.orientationLock
    }

    /**
     * Start decoding.
     */
    fun decode() {
        barcodeView.decodeSingle(callback)
    }

    /**
     * Call from Activity#onResume().
     */
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
            activity.requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionReqCode
            )
            askedPermission = true
        } else {
            // Wait for permission result
        }
    }

    /**
     * Call from Activity#onRequestPermissionsResult

     * @param requestCode  The request code passed in [android.support.v4.app.ActivityCompat.requestPermissions].
     * *
     * @param permissions  The requested permissions.
     * *
     * @param grantResults The grant results for the corresponding permissions
     * *                     which is either [android.content.pm.PackageManager.PERMISSION_GRANTED]
     * *                     or [android.content.pm.PackageManager.PERMISSION_DENIED]. Never null.
     */
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == cameraPermissionReqCode) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted
                barcodeView.resume()
            } else {
                // TODO: display better error message.
                displayFrameworkBugMessageAndExit()
            }
        }
    }

    /**
     * Call from Activity#onPause().
     */
    fun onPause() {
        barcodeView.pause()
        inactivityTimer.cancel()
        beepManager.close()
    }

    /**
     * Call from Activity#onDestroy().
     */
    fun onDestroy() {
        destroyed = true
        inactivityTimer.cancel()
    }

    /**
     * Call from Activity#onSaveInstanceState().
     */
    fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SAVED_ORIENTATION_LOCK, this.orientationLock)
    }


    private fun finish() {
        activity.finish()
    }

    protected fun returnResultTimeout() {
        finish()
    }


    protected fun displayFrameworkBugMessageAndExit() {
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
        private val TAG = CaptureManager::class.java.simpleName

        var cameraPermissionReqCode = 250
        private val SAVED_ORIENTATION_LOCK = "SAVED_ORIENTATION_LOCK"

        // Delay long enough that the beep can be played.
        // TODO: play beep in background
        private val DELAY_BEEP: Long = 150


    }
}
