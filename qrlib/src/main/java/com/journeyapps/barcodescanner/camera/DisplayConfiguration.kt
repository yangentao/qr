package com.journeyapps.barcodescanner.camera

import android.graphics.Rect

import com.journeyapps.barcodescanner.Size

/**
 *
 */
class DisplayConfiguration(val rotation: Int, val viewfinderSize: Size) {
    var previewScalingStrategy: PreviewScalingStrategy = CenterCropStrategy() //FitCenterStrategy()

    /**
     * Choose the best preview size, based on our display size.
     *
     * We prefer:
     * 1. no scaling
     * 2. least downscaling
     * 3. least upscaling
     *
     * We do not care much about aspect ratio, since we just crop away extra pixels. We only choose
     * the size to minimize scaling.
     *
     * In the future we may consider choosing the biggest possible preview size, to maximize the
     * resolution we have for decoding. We need more testing to see whether or not that is feasible.
     *
     * @param sizes supported preview sizes, containing at least one size. Sizes are in natural camera orientation.
     * @param isRotated true if the camera is rotated perpendicular to the current display orientation
     * @return the best preview size, never null
     */
    fun getBestPreviewSize(sizes: List<Size>, isRotated: Boolean): Size {
        val desired = if (isRotated) {
            viewfinderSize.rotate()
        } else {
            viewfinderSize
        }
        return previewScalingStrategy.getBestPreviewSize(sizes, desired)
    }

    /**
     * Scale the preview to cover the viewfinder, then center it.
     *
     * Aspect ratio is preserved.
     *
     * @param previewSize the size of the preview (camera), in current display orientation
     * @return a rect placing the preview
     */
    fun scalePreview(previewSize: Size): Rect {
        return previewScalingStrategy.scalePreview(previewSize, viewfinderSize)
    }

}
