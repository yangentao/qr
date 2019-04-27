package dev.entao.utilapp

import android.widget.LinearLayout
import dev.entao.kan.base.TitledActivity
import dev.entao.kan.log.logd
import dev.entao.kan.qr.camera.RotationCallback
import dev.entao.kan.qr.camera.RotationListener

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