package dev.entao.qr

import com.google.zxing.BarcodeFormat

/**
 * Created by entaoyang@163.com on 2016-10-29.
 */

class ScanConfig {
	var decodeSet: Set<BarcodeFormat> = setOf(BarcodeFormat.QR_CODE)
	var tips: String = "请将条码置于取景框内扫描。"
	var charset: String = "UTF-8"
	var beep: Boolean = true
	var timeout: Int = 60 * 1000

	var returnImage: Boolean = false

	var enableManualInput: Boolean = true
	var enableLight: Boolean = true
	var enableFromImageFile: Boolean = true
}