package dev.entao.utilapp

import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import dev.entao.kan.appbase.App
import dev.entao.kan.base.openActivity
import dev.entao.kan.base.pushPage
import dev.entao.kan.creator.buttonGreenRound
import dev.entao.kan.creator.edit
import dev.entao.kan.ext.*
import dev.entao.kan.log.logd
import dev.entao.kan.page.TitlePage
import dev.entao.kan.qr.QRPage


class MainPage : TitlePage() {

    lateinit var edit: EditText

    override fun onCreateContent(context: Context, contentView: LinearLayout) {
        super.onCreateContent(context, contentView)
        titleBar {
            title("Main")
            rightText("QR").onClick = {
                val p = QRPage()
                p.onScanText = {
                    logd("Scan: $it ")
                    edit.textS = it
                }
                pushPage(p)
            }
            rightText("Open").onClick = {
                openActivity(TestActivity::class){}
            }
        }

        edit = contentView.edit(LParam.WidthFill.height(120).margins(20)) {
            gravityTopLeft()
            setSingleLine(false)
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
        }
        contentView.buttonGreenRound {
            text = "Clear"
            onClick {
                edit.textS = ""
            }
        }
        contentView.buttonGreenRound {
            text = "Copy"
            App.copyToClipboard(edit.textS)
        }

    }


}