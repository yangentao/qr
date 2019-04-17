package dev.entao.utilapp

import android.widget.LinearLayout
import dev.entao.log.logd
import dev.entao.qr.camera.RotationCallback
import dev.entao.qr.camera.RotationListener
import dev.entao.ui.base.TitledActivity

class TestActivity : TitledActivity() {
    lateinit  var rl:RotationListener

    override fun onCreateContent(contentView: LinearLayout) {
        titleBar.title("Hello")
        titleBar.showBack()
        rl = RotationListener(this)
        rl.listen(object :RotationCallback{
            override fun onRotationChanged(rotation: Int) {
                 logd("onRotationChanged", rotation)
            }

        })
    }
}