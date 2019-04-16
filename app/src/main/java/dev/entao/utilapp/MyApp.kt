package dev.entao.utilapp

import dev.entao.log.logd
import dev.entao.ui.widget.TitleBar
import dev.entao.util.app.YetApp

class MyApp : YetApp() {

    override fun onCreate() {
        super.onCreate()
        TitleBar.TitleCenter = true
        val s = "abc0123"
        val a = s.map { it.toInt().toString(16) }.joinToString("")
        logd(a)
    }
}