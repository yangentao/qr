package dev.entao.qr.camera

import com.google.zxing.ResultPoint
import dev.entao.qr.camera.BarcodeResult

interface BarcodeCallback {

    fun barcodeResult(result: BarcodeResult)

    fun possibleResultPoints(resultPoints: List<ResultPoint>)
}
