package dev.entao.utilapp

import android.os.Bundle
import dev.entao.ui.base.ContainerActivity
import dev.entao.ui.creator.createFrame

class MainActivity : ContainerActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        containerView = this.createFrame()
        setContentView(containerView)
        push(MainPage())

//        val tv = TextureView(this)
//        containerView.addView(tv, FParam.Fill)
//        tv.surfaceTextureListener = this
//
//        Task.foreDelay(3000) {
//            this.openActivity(TestActivity::class)
//        }
    }

}
