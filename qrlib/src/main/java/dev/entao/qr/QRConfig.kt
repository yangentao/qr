package dev.entao.qr

import com.google.zxing.BarcodeFormat
import dev.entao.appbase.ex.color
import dev.entao.appbase.ex.dp

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


    var width = 250.dp  //-1;
    var height = 250.dp //-1;

    var possibleResultPoints = 0XC0FFBD21L.color
    var viewfinderLaser = 0XFFCC0000L.color
    var viewfinderMask = 0x60000000L.color

    var isExposureEnabled = false
    var isAutoTorchEnabled = false

}