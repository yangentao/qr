package com.journeyapps.barcodescanner.camera;

import android.graphics.Rect;
import android.util.Log;

import dev.entao.qr.camera.Size;

/**
 * Scales the dimensions so that it fits entirely inside the parent.One of width or height will
 * fit exactly. Aspect ratio is preserved.
 */
public class CenterCropStrategy {
    private static final String TAG = CenterCropStrategy.class.getSimpleName();

    /**
     * Scale the preview to cover the viewfinder, then center it.
     * <p>
     * Aspect ratio is preserved.
     *
     * @param previewSize    the size of the preview (camera), in current display orientation
     * @param viewfinderSize the size of the viewfinder (display), in current display orientation
     * @return a rect placing the preview
     */
    public Rect scalePreview(Size previewSize, Size viewfinderSize) {
        // We avoid scaling if feasible.
        Size scaledPreview = previewSize.scaleCrop(viewfinderSize);
        Log.i(TAG, "Preview: " + previewSize + "; Scaled: " + scaledPreview + "; Want: " + viewfinderSize);

        int dx = (scaledPreview.getWidth() - viewfinderSize.getWidth()) / 2;
        int dy = (scaledPreview.getHeight() - viewfinderSize.getHeight()) / 2;

        return new Rect(-dx, -dy, scaledPreview.getWidth() - dx, scaledPreview.getHeight() - dy);
    }
}
