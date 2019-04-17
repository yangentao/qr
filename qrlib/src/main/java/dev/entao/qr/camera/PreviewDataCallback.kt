package dev.entao.qr.camera

import com.journeyapps.barcodescanner.SourceData

/**
 * Callback for camera previews.
 */
interface PreviewDataCallback {
    fun onPreview(sourceData: SourceData)
}
