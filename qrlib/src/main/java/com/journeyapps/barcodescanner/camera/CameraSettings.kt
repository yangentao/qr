package com.journeyapps.barcodescanner.camera

/**
 *
 */
class CameraSettings {

    /**
     * Default to false.
     *
     *
     * Inverted means dark & light colors are inverted.
     *
     * @return true if scan is inverted
     */
    val isScanInverted = false
    /**
     * Default to false.
     *
     * @return true if barcode scene mode is enabled
     */
    val isBarcodeSceneModeEnabled = false
    /**
     * Default to false.
     *
     *
     * If enabled, metering is performed to determine focus area.
     *
     * @return true if metering is enabled
     */
    val isMeteringEnabled = false
    /**
     * Default to false.
     *
     * @return true if exposure is enabled.
     */
    val isExposureEnabled = false
    /**
     * Default to false.
     *
     * @return true if the torch is automatically controlled based on ambient light.
     */
    val isAutoTorchEnabled = false

}
