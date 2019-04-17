package com.journeyapps.barcodescanner.camera

import com.journeyapps.barcodescanner.SourceData

/**
 * Callback for camera previews.
 */
interface PreviewDataCallback {
    fun onPreview(sourceData: SourceData)
}
