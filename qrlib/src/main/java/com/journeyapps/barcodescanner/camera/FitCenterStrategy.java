package com.journeyapps.barcodescanner.camera;

import android.graphics.Rect;
import android.util.Log;

import dev.entao.qr.camera.Size;

/**
 * Scales the size so that both dimensions will be greater than or equal to the corresponding
 * dimension of the parent. One of width or height will fit exactly. Aspect ratio is preserved.
 */
public class FitCenterStrategy extends PreviewScalingStrategy {
    private static final String TAG = FitCenterStrategy.class.getSimpleName();


    /**
     * Get a score for our size.
     *
     * Based on heuristics for penalizing scaling and cropping.
     *
     * 1.0 is perfect (exact match).
     * 0.0 means we can't use it at all.
     *
     * @param size the camera preview size (that can be scaled)
     * @param desired the viewfinder size
     * @return the score
     */
    @Override
    protected float getScore(Size size, Size desired) {
        if(size.getWidth() <= 0 || size.getHeight() <= 0) {
            return 0f;
        }
        Size scaled = size.scaleFit(desired);
        // Scaling preserves aspect ratio
        float scaleRatio = scaled.getWidth() * 1.0f / size.getWidth();

        // Treat downscaling as slightly better than upscaling
        float scaleScore;
        if(scaleRatio > 1.0f) {
            // Upscaling
            scaleScore = (float)Math.pow(1.0f / scaleRatio, 1.1);
        } else {
            // Downscaling
            scaleScore = scaleRatio;
        }

        // Ratio of scaledDimension / dimension.
        // Note that with scaleCrop, only one dimension is cropped.
        float cropRatio = (desired.getWidth() * 1.0f / scaled.getWidth()) *
                (desired.getHeight() * 1.0f / scaled.getHeight());

        // Cropping is very bad, since it's used-visible for centerFit
        // 1.0 means no cropping.
        float cropScore = 1.0f / cropRatio / cropRatio / cropRatio;

        return scaleScore * cropScore;
    }

    /**
     * Scale the preview to cover the viewfinder, then center it.
     *
     * Aspect ratio is preserved.
     *
     * @param previewSize the size of the preview (camera), in current display orientation
     * @param viewfinderSize the size of the viewfinder (display), in current display orientation
     * @return a rect placing the preview
     */
    public Rect scalePreview(Size previewSize, Size viewfinderSize) {
        // We avoid scaling if feasible.
        Size scaledPreview = previewSize.scaleFit(viewfinderSize);
        Log.i(TAG, "Preview: " + previewSize + "; Scaled: " + scaledPreview + "; Want: " + viewfinderSize);

        int dx = (scaledPreview.getWidth() - viewfinderSize.getWidth()) / 2;
        int dy = (scaledPreview.getHeight() - viewfinderSize.getHeight()) / 2;

        return new Rect(-dx, -dy, scaledPreview.getWidth() - dx, scaledPreview.getHeight() - dy);
    }
}
