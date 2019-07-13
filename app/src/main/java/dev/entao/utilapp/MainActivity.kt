package dev.entao.utilapp

import android.os.Bundle
import dev.entao.kan.base.StackActivity

class MainActivity : StackActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        doubleBack = true
        setContentPage(MainPage())

//        val tv = TextureView(this)
//        containerView.addView(tv, FParam.Fill)
//        tv.surfaceTextureListener = this
//
//        Task.foreDelay(3000) {
//            this.openActivity(TestActivity::class)
//        }
    }

}
