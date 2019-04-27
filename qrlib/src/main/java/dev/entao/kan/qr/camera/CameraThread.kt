package dev.entao.kan.qr.camera

import android.os.Handler
import android.os.HandlerThread

/**
 * Singleton thread that is started and stopped on demand.
 * Any access to Camera / CameraManager should happen on this thread, through CameraInstance.
 */
object CameraThread {

    private var handler: Handler? = null
    private var thread: HandlerThread? = null

    private var openCount = 0


    @Synchronized
    fun enqueue(block: () -> Unit) {
        checkRunning()
        handler?.post {
            block()
        }
    }

    // Call from main thread or camera thread.
    @Synchronized
    fun enqueue(runnable: Runnable) {
        checkRunning()
        handler?.post(runnable)
    }

    // Call from main thread or camera thread.
    @Synchronized
    fun enqueueDelayed(runnable: Runnable, delayMillis: Long) {
        checkRunning()
        handler?.postDelayed(runnable, delayMillis)
    }

    @Synchronized
    private fun checkRunning() {
        if (handler == null) {
            if (openCount <= 0) {
                throw IllegalStateException("CameraThread is not open")
            }
            thread = HandlerThread("CameraThread")
            thread?.start()
            handler = Handler(thread!!.looper)
        }
    }

    @Synchronized
    private fun quit() {
        thread!!.quit()
        thread = null
        handler = null
    }

    @Synchronized
    fun pop() {
        openCount -= 1
        if (openCount == 0) {
            quit()
        }
    }

    @Synchronized
    fun push(block: () -> Unit) {
        openCount += 1
        enqueue(block)
    }


}
