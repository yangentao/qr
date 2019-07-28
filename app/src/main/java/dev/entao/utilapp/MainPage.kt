package dev.entao.utilapp

import android.content.Context
import android.net.Uri
import android.widget.EditText
import android.widget.LinearLayout
import dev.entao.kan.appbase.App
import dev.entao.kan.appbase.copyToClipboard
import dev.entao.kan.base.pushPage
import dev.entao.kan.base.viewUrl
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
            title("二维码扫描")
        }

        edit = contentView.edit(LParam.WidthFill.height(150).margins(20)) {
            gravityTopLeft()
            isSingleLine = false
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
        }
        contentView.buttonGreenRound {
            text = "清除"
            onClick {
                edit.textS = ""
            }
        }
        contentView.buttonGreenRound {
            text = "复制"
            onClick {
                App.inst.copyToClipboard(edit.textS)
            }
        }
        contentView.buttonGreenRound {
            text = "在浏览器打开"
            onClick {
                try {
                    App.inst.viewUrl(Uri.parse(edit.textS))
                } catch (ex: Exception) {

                }
            }
        }
        contentView.buttonGreenRound {
            text = "扫描"
            onClick {
                val p = QRPage()
                p.onScanText = {
                    logd("Scan: $it ")
                    edit.textS = it
                }
                pushPage(p)
            }
        }
    }


}