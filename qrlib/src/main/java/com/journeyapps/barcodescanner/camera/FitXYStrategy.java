package com.journeyapps.barcodescanner.camera;

import android.graphics.Rect;

import dev.entao.qr.camera.Size;

/**
 * Scales the size so that it fits exactly. Aspect ratio is NOT preserved.
 */
public class FitXYStrategy extends PreviewScalingStrategy {
    private static final String TAG = FitXYStrategy.class.getSimpleName();


    private static float absRatio(float ratio) {
        if(ratio < 1.0f) {
            return 1.0f / ratio;
        } else {
            return ratio;
        }
    }

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
        float scaleX = absRatio(size.getWidth() * 1.0f / desired.getWidth());
        float scaleY = absRatio(size.getHeight() * 1.0f / desired.getHeight());

        float scaleScore = 1.0f / scaleX / scaleY;

        float distortion = absRatio((1.0f * size.getWidth() / size.getHeight()) / (1.0f * desired.getWidth() / desired.getHeight()));

        // Distortion is bad!
        float distortionScore = 1.0f / distortion / distortion / distortion;

        return scaleScore * distortionScore;
    }

    /**
     * Scale the preview to match the viewfinder exactly.
     *
     * @param previewSize the size of the preview (camera), in current display orientation
     * @param viewfinderSize the size of the viewfinder (display), in current display orientation
     * @return a rect placing the preview
     */
    public Rect scalePreview(Size previewSize, Size viewfinderSize) {
        return new Rect(0, 0, viewfinderSize.getWidth(), viewfinderSize.getHeight());
    }
}
