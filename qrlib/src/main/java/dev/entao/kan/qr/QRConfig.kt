package dev.entao.kan.qr

import com.google.zxing.BarcodeFormat
import dev.entao.kan.appbase.ex.color
import dev.entao.kan.appbase.ex.dp

/**
 * Created by entaoyang@163.com on 2016-10-29.
 */

object QRConfig {
    var decodeSet: Set<BarcodeFormat> = setOf(BarcodeFormat.QR_CODE)
    var tips: String = "请将条码置于取景框内扫描。"
    var charset: String = "UTF-8"
    var beep: Boolean = true
    var timeout: Int = 60 * 1000


    var enableManualInput: Boolean = true
    var enableLight: Boolean = true
    var enableFromImageFile: Boolean = true


    var possibleResultPoints = 0XC0FFBD21L.color
    var viewfinderLaser = 0XFFCC0000L.color
    var viewfinderMask = 0x60000000L.color

    var isExposureEnabled = false

    //分辨率, 720, 960, 1080,
    var resolution:Int = 720

    //中间方框的高宽
    var width = 240.dp  //-1;
    var height = 240.dp //-1;

    //中间方框占屏幕(控件大小)的比例, 当width或height是0时有效
    var percentEdge: Double = 0.6
        set(value) {
            field = when {
                value < 0.1 -> 0.1
                value > 1.0 -> 1.0
                else -> value
            }
        }

}