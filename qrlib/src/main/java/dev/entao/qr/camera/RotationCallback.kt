package dev.entao.qr.camera

/**
 *
 */
interface RotationCallback {
    /**
     * Rotation changed.
     *
     * @param rotation the current value of windowManager.getDefaultDisplay().getRotation()
     */
    fun onRotationChanged(rotation: Int)
}
