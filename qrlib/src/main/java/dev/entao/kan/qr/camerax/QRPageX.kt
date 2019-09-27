package dev.entao.kan.qr.camerax

import android.content.Context
import android.widget.LinearLayout
import dev.entao.kan.base.ManiPerm
import dev.entao.kan.base.hasPerm
import dev.entao.kan.base.popPage
import dev.entao.kan.base.reqPerm
import dev.entao.kan.ext.HeightFill
import dev.entao.kan.ext.LParam
import dev.entao.kan.ext.WidthFill
import dev.entao.kan.log.logd
import dev.entao.kan.page.TitlePage

class QRPageX : TitlePage() {
    private lateinit var previewView: CameraXView

    var onResult: (String) -> Unit = {}

    override fun onCreateContent(context: Context, contentView: LinearLayout) {
        super.onCreateContent(context, contentView)
        previewView = CameraXView(context)
        contentView.addView(previewView, LParam.WidthFill.HeightFill)

        previewView.onResult = {
            onQRResult(it)
        }

    }

    private fun onQRResult(s: String) {
        logd(s)
        val a = this.onResult
        this.onResult = {}
        a(s)
        this.popPage()
    }

    override fun onResume() {
        super.onResume()
        if (hasPerm(ManiPerm.CAMERA)) {
            previewView.start(this)
        } else {
            reqPerm(ManiPerm.CAMERA) {
                if (it) {
                    previewView.start(this)
                }
            }
        }
    }

}