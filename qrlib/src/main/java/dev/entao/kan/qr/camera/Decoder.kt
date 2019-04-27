package dev.entao.kan.qr.camera

import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.*

/**
 * A class for decoding images.
 *
 * A decoder contains all the configuration required for the binarization and decoding process.
 *
 * The actual decoding should happen on a dedicated thread.
 */
class Decoder(private val reader: Reader) : ResultPointCallback {

    val possibleResultPoints = ArrayList<ResultPoint>()

    fun decode(source: LuminanceSource): Result? {
        return decode(BinaryBitmap(HybridBinarizer(source)))
    }

    private fun decode(bitmap: BinaryBitmap): Result? {
        possibleResultPoints.clear()
        return try {
            if (reader is MultiFormatReader) {
                reader.decodeWithState(bitmap)
            } else {
                reader.decode(bitmap)
            }
        } catch (e: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    override fun foundPossibleResultPoint(point: ResultPoint) {
        possibleResultPoints.add(point)
    }
}
