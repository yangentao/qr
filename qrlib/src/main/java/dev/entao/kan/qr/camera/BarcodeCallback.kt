package dev.entao.kan.qr.camera

import com.google.zxing.ResultPoint

interface BarcodeCallback {

    fun barcodeResult(result: BarcodeResult)

    fun possibleResultPoints(resultPoints: List<ResultPoint>)
}
