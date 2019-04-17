package dev.entao.utilapp

import android.content.Context
import android.widget.LinearLayout
import dev.entao.log.logd
import dev.entao.qr.QRPage
import dev.entao.ui.base.openActivity
import dev.entao.ui.base.pushPage
import dev.entao.ui.creator.textView
import dev.entao.ui.ext.*
import dev.entao.ui.page.TitlePage

class MainPage : TitlePage() {

    override fun onCreateContent(context: Context, contentView: LinearLayout) {
        super.onCreateContent(context, contentView)
        titleBar {
            title("Main")
            rightText("QR").onClick = {
                val p = QRPage()
                p.onScanText = {
                    logd("Scan: $it ")
                }
                pushPage(p)
            }
            rightText("Open").onClick = {
                openActivity(TestActivity::class)
            }
        }

        contentView.textView(LParam.WidthFill.height(300).marginY(50)) {
            text = "Main"
            textColorRed()
            gravityCenter()
        }
    }


}