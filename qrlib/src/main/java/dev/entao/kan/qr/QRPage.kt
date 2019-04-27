package dev.entao.kan.qr

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.util.Log
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import dev.entao.kan.appbase.ex.ImageStated
import dev.entao.kan.appbase.ex.sized
import dev.entao.kan.base.act
import dev.entao.kan.base.popPage
import dev.entao.kan.creator.createLinearHorizontal
import dev.entao.kan.creator.createTextViewC
import dev.entao.kan.dialogs.showInput
import dev.entao.kan.ext.*
import dev.entao.kan.log.logd
import dev.entao.kan.page.PageClass
import dev.entao.kan.page.TitlePage
import dev.entao.kan.qr.camera.BarcodeResult
import dev.entao.kan.qr.camera.CameraView
import dev.entao.kan.qr.camera.CaptureManager



/**
 * Created by entaoyang@163.com on 2016-10-29.
 */

class QRPage : TitlePage() {

    var title: String = "二维码扫描"

    lateinit var capture: CaptureManager
    lateinit var barcodeScannerView: CameraView

    var inputTextView: TextView? = null
    var lightTextView: TextView? = null

    var onScanText: (String) -> Unit = {}


    override fun onCreateContent(context: Context, contentView: LinearLayout) {
        titleBar.title(title)

        barcodeScannerView = CameraView(context)
        contentView.addView(barcodeScannerView) {
            WidthFill.HeightFlex
        }
        capture = CaptureManager(act, barcodeScannerView)
        capture.onFinish = {
            popPage()
        }
        capture.onResult = {
            onScanResult(it)
        }
        capture.decode()

        val ll = createLinearHorizontal().backColor(Color.rgb(50, 50, 50)).padding(10)
        contentView.addViewParam(ll) {
            widthFill().heightWrap()
        }
        if (QRConfig.enableManualInput) {
            val tv = makeButton(R.mipmap.qr_round, R.mipmap.qr_round2)
            tv.text = "手动输入"
            ll.addView(tv) {
                WidthFlex.HeightWrap
            }
            inputTextView = tv
            tv.onClick {
                onInputCode()
            }

        }
        if (QRConfig.enableLight) {
            val tv = makeButton(R.mipmap.light, R.mipmap.light2)
            tv.text = "开灯"
            ll.addViewParam(tv) {
                WidthFlex.HeightWrap
            }
            lightTextView = tv
            tv.onClick {
                onLightToggle()
            }
        }
//		if(config.enableFromImageFile){
//
//		}
    }

    private fun onInputCode() {
        showInput("请输入编号") {
            if (it.trim().isNotEmpty()) {
                finish()
                onScanText(it.trim())
            }
        }
    }

    fun onLightToggle() {
        val b = lightTextView?.isSelected ?: false
        if (b) {
            barcodeScannerView.setTorchOff()
        } else {
            barcodeScannerView.setTorchOn()
        }
        val newState = !b
        lightTextView?.isSelected = newState
        lightTextView?.text = if (newState) "关灯" else "开灯"
    }

    private fun makeButton(@DrawableRes normal: Int, @DrawableRes pressed: Int): TextView {
        val tv = act.createTextViewC().textColorWhite().clickable()
        val d = ImageStated(normal).pressed(pressed).selected(pressed).value.sized(45)
        tv.topImage(d, 2)
        tv.gravityCenter()
        return tv

    }


    fun onScanResult(result: BarcodeResult) {
        val text = result.text
        Log.d("ScanResult:", result.text)
        onScanText(text)
    }


    override fun onResume() {
        logd("QRPage.OnResume")
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        logd("QRPage.onPause")
        super.onPause()
        capture.onPause()
    }

    override fun onDestroy() {
        logd("QRPage.onDestroy")
        super.onDestroy()
        capture.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return barcodeScannerView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }


    companion object : PageClass<QRPage>()
}