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
	}


}
