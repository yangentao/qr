package dev.entao.qr.camera


import com.google.zxing.Result

/**
 * This contains the result of a barcode scan.
 *
 * This class delegate all read-only fields of [com.google.zxing.Result],
 * and adds a bitmap with scanned barcode.
 */
class BarcodeResult(val result: Result) {

    /**
     * @return raw text encoded by the barcode
     * @see Result.getText
     */
    val text: String
        get() = result.text


    override fun toString(): String {
        return result.text
    }


}
