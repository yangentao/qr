package dev.entao.qr

import android.content.Context
import android.widget.LinearLayout
import dev.entao.ui.page.TitlePage

class HelloPage : TitlePage() {

    override fun onCreateContent(context: Context, contentView: LinearLayout) {
        super.onCreateContent(context, contentView)
        titleBar {
            title("Hello")
            showBack()
        }
    }
}