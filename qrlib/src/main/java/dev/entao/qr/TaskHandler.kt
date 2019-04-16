package dev.entao.qr

import android.os.Handler
import android.os.HandlerThread

class TaskHandler(val name: String) {

    val thread = HandlerThread(name)
    val handler: Handler


    init {
        thread.start()
        handler = Handler(thread.looper)
    }

    fun quit() {
        handler.removeCallbacksAndMessages(null)
        thread.quit()
    }


    fun post(block: () -> Unit) {
        handler.post(block)
    }

    fun post(delay: Int, block: () -> Unit) {
        handler.postDelayed(block, delay.toLong())
    }
}