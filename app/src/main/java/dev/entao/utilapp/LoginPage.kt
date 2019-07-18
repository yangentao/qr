package dev.entao.utilapp

import android.content.Context
import android.widget.LinearLayout
import dev.entao.kan.creator.textView
import dev.entao.kan.dialogs.dialogX
import dev.entao.kan.ext.*
import dev.entao.kan.page.TitlePage


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
				dialogX.showAlert("Hello")
			}
		}

		contentView.textView(LParam.WidthFill.height(300).marginY(50)) {
			text = "Login"
			textColorRed()
			gravityCenter()
		}
	}

}