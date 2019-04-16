package dev.entao.qr

import android.os.Handler
import android.os.HandlerThread

class QueueTask(val name: String) {

    val thread = HandlerThread(name)
    val handler = Handler(thread.looper)


    init {
        thread.start()
    }

    fun stop() {
        handler.looper.quit()
    }


    fun run(block: () -> Unit) {
        handler.post(block)
    }

    fun run(delay: Int, block: () -> Unit) {
        handler.postDelayed(block, delay.toLong())
    }
}