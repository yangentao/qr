package dev.entao.utilapp

import android.content.Context
import android.widget.LinearLayout
import dev.entao.ui.creator.textView
import dev.entao.ui.ext.*
import dev.entao.ui.page.TitlePage

class MainPage : TitlePage() {

	override fun onCreateContent(context: Context, contentView: LinearLayout) {
		super.onCreateContent(context, contentView)
		titleBar {
			title("Main")
			rightText("Push").onClick = {
				(activity as MainActivity).push(LoginPage())
			}
		}

		contentView.textView(LParam.WidthFill.height(300).marginY(50)) {
			text = "Main"
			textColorRed()
			gravityCenter()
		}
	}


}