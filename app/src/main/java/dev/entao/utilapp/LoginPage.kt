package dev.entao.utilapp

import android.content.Context
import android.widget.LinearLayout
import dev.entao.ui.creator.textView
import dev.entao.ui.dialogs.alert
import dev.entao.ui.ext.*
import dev.entao.ui.page.TitlePage

class LoginPage : TitlePage() {

	override fun onCreateContent(context: Context, contentView: LinearLayout) {
		super.onCreateContent(context, contentView)
		titleBar {
			title("Login")
			showBack()
			rightText("Pop").onClick = {
				(activity as MainActivity).pop()
			}
			rightText("Dialog").onClick = {
				alert("Hello")
			}
		}

		contentView.textView(LParam.WidthFill.height(300).marginY(50)) {
			text = "Login"
			textColorRed()
			gravityCenter()
		}
	}

}