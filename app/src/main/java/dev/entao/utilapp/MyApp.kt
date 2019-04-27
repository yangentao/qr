package dev.entao.utilapp

import dev.entao.kan.log.logd
import dev.entao.kan.util.app.YetApp
import dev.entao.kan.widget.TitleBar


class MyApp : YetApp() {

    override fun onCreate() {
        super.onCreate()
        TitleBar.TitleCenter = true
        val s = "abc0123"
        val a = s.map { it.toInt().toString(16) }.joinToString("")
        logd(a)
    }
}