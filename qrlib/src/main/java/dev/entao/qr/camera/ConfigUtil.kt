/*
 * Copyright (C) 2014 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.entao.qr.camera

import android.annotation.TargetApi
import android.graphics.Rect
import android.hardware.Camera
import android.os.Build
import android.util.Log
import java.util.*
import java.util.regex.Pattern

/**
 * Utility methods for configuring the Android camera.
 *
 * @author Sean Owen
 */
object ConfigUtil {
    private const val TAG = "CameraConfiguration"
    private val SEMICOLON = Pattern.compile(";")
    private const val MAX_EXPOSURE_COMPENSATION = 1.5f
    private const val MIN_EXPOSURE_COMPENSATION = 0.0f
    private const val MIN_FPS = 10
    private const val MAX_FPS = 20
    private const val AREA_PER_1000 = 400

    fun findFocus(parameters: Camera.Parameters): String? {
        return findSettableValue(
            parameters.supportedFocusModes,
            Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
            Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
            Camera.Parameters.FOCUS_MODE_MACRO,
            Camera.Parameters.FOCUS_MODE_AUTO
        )
    }

    fun setTorch(parameters: Camera.Parameters, on: Boolean) {
        val supportedFlashModes = parameters.supportedFlashModes
        val flashMode: String?
        if (on) {
            flashMode = findSettableValue(
                supportedFlashModes,
                Camera.Parameters.FLASH_MODE_TORCH,
                Camera.Parameters.FLASH_MODE_ON
            )
        } else {
            flashMode = findSettableValue(
                supportedFlashModes,
                Camera.Parameters.FLASH_MODE_OFF
            )
        }
        if (flashMode != null) {
            if (flashMode == parameters.flashMode) {
                Log.i(TAG, "Flash mode already set to $flashMode")
            } else {
                Log.i(TAG, "Setting flash mode to $flashMode")
                parameters.flashMode = flashMode
            }
        }
    }

    fun setBestExposure(parameters: Camera.Parameters, lightOn: Boolean) {
        val minExposure = parameters.minExposureCompensation
        val maxExposure = parameters.maxExposureCompensation
        val step = parameters.exposureCompensationStep
        if ((minExposure != 0 || maxExposure != 0) && step > 0.0f) {
            // Set low when light is on
            val targetCompensation = if (lightOn) MIN_EXPOSURE_COMPENSATION else MAX_EXPOSURE_COMPENSATION
            var compensationSteps = Math.round(targetCompensation / step)
            val actualCompensation = step * compensationSteps
            // Clamp value:
            compensationSteps = Math.max(Math.min(compensationSteps, maxExposure), minExposure)
            if (parameters.exposureCompensation == compensationSteps) {
                Log.i(TAG, "Exposure compensation already set to $compensationSteps / $actualCompensation")
            } else {
                Log.i(TAG, "Setting exposure compensation to $compensationSteps / $actualCompensation")
                parameters.exposureCompensation = compensationSteps
            }
        } else {
            Log.i(TAG, "Camera does not support exposure compensation")
        }
    }

    @JvmOverloads
    fun setBestPreviewFPS(parameters: Camera.Parameters, minFPS: Int = MIN_FPS, maxFPS: Int = MAX_FPS) {
        val supportedPreviewFpsRanges = parameters.supportedPreviewFpsRange
        Log.i(TAG, "Supported FPS ranges: " + toString(supportedPreviewFpsRanges))
        if (supportedPreviewFpsRanges != null && !supportedPreviewFpsRanges.isEmpty()) {
            var suitableFPSRange: IntArray? = null
            for (fpsRange in supportedPreviewFpsRanges) {
                val thisMin = fpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX]
                val thisMax = fpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
                if (thisMin >= minFPS * 1000 && thisMax <= maxFPS * 1000) {
                    suitableFPSRange = fpsRange
                    break
                }
            }
            if (suitableFPSRange == null) {
                Log.i(TAG, "No suitable FPS range?")
            } else {
                val currentFpsRange = IntArray(2)
                parameters.getPreviewFpsRange(currentFpsRange)
                if (Arrays.equals(currentFpsRange, suitableFPSRange)) {
                    Log.i(TAG, "FPS range already set to " + Arrays.toString(suitableFPSRange))
                } else {
                    Log.i(TAG, "Setting FPS range to " + Arrays.toString(suitableFPSRange))
                    parameters.setPreviewFpsRange(
                        suitableFPSRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
                        suitableFPSRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]
                    )
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    fun setFocusArea(parameters: Camera.Parameters) {
        if (parameters.maxNumFocusAreas > 0) {
            Log.i(TAG, "Old focus areas: " + toString(parameters.focusAreas)!!)
            val middleArea = buildMiddleArea(AREA_PER_1000)
            Log.i(TAG, "Setting focus area to : " + toString(middleArea)!!)
            parameters.focusAreas = middleArea
        } else {
            Log.i(TAG, "Device does not support focus areas")
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    fun setMetering(parameters: Camera.Parameters) {
        if (parameters.maxNumMeteringAreas > 0) {
            Log.i(TAG, "Old metering areas: " + parameters.meteringAreas)
            val middleArea = buildMiddleArea(AREA_PER_1000)
            Log.i(TAG, "Setting metering area to : " + toString(middleArea)!!)
            parameters.meteringAreas = middleArea
        } else {
            Log.i(TAG, "Device does not support metering areas")
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private fun buildMiddleArea(areaPer1000: Int): List<Camera.Area> {
        return listOf(Camera.Area(Rect(-areaPer1000, -areaPer1000, areaPer1000, areaPer1000), 1))
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    fun setVideoStabilization(parameters: Camera.Parameters) {
        if (parameters.isVideoStabilizationSupported) {
            if (parameters.videoStabilization) {
                Log.i(TAG, "Video stabilization already enabled")
            } else {
                Log.i(TAG, "Enabling video stabilization...")
                parameters.videoStabilization = true
            }
        } else {
            Log.i(TAG, "This device does not support video stabilization")
        }
    }

    fun setBarcodeSceneMode(parameters: Camera.Parameters) {
        if (Camera.Parameters.SCENE_MODE_BARCODE == parameters.sceneMode) {
            Log.i(TAG, "Barcode scene mode already set")
            return
        }
        val sceneMode = findSettableValue(
            parameters.supportedSceneModes,
            Camera.Parameters.SCENE_MODE_BARCODE
        )
        if (sceneMode != null) {
            parameters.sceneMode = sceneMode
        }
    }

    fun setZoom(parameters: Camera.Parameters, targetZoomRatio: Double) {
        if (parameters.isZoomSupported) {
            val zoom = indexOfClosestZoom(parameters, targetZoomRatio) ?: return
            if (parameters.zoom == zoom) {
                Log.i(TAG, "Zoom is already set to $zoom")
            } else {
                Log.i(TAG, "Setting zoom to $zoom")
                parameters.zoom = zoom
            }
        } else {
            Log.i(TAG, "Zoom is not supported")
        }
    }

    private fun indexOfClosestZoom(parameters: Camera.Parameters, targetZoomRatio: Double): Int? {
        val ratios = parameters.zoomRatios
        Log.i(TAG, "Zoom ratios: " + ratios!!)
        val maxZoom = parameters.maxZoom
        if (ratios == null || ratios.isEmpty() || ratios.size != maxZoom + 1) {
            Log.w(TAG, "Invalid zoom ratios!")
            return null
        }
        val target100 = 100.0 * targetZoomRatio
        var smallestDiff = java.lang.Double.POSITIVE_INFINITY
        var closestIndex = 0
        for (i in ratios.indices) {
            val diff = Math.abs(ratios[i] - target100)
            if (diff < smallestDiff) {
                smallestDiff = diff
                closestIndex = i
            }
        }
        Log.i(TAG, "Chose zoom ratio of " + ratios[closestIndex] / 100.0)
        return closestIndex
    }

    fun setInvertColor(parameters: Camera.Parameters) {
        if (Camera.Parameters.EFFECT_NEGATIVE == parameters.colorEffect) {
            Log.i(TAG, "Negative effect already set")
            return
        }
        val colorMode = findSettableValue(
            parameters.supportedColorEffects,
            Camera.Parameters.EFFECT_NEGATIVE
        )
        if (colorMode != null) {
            parameters.colorEffect = colorMode
        }
    }

    private fun findSettableValue(supportedValues: Collection<String>?, vararg desiredValues: String): String? {
        val all = supportedValues ?: return null
        for (v in desiredValues) {
            if (all.contains(v)) {
                return v
            }
        }
        return null
    }

    private fun toString(arrays: Collection<IntArray>?): String {
        if (arrays == null || arrays.isEmpty()) {
            return "[]"
        }
        val buffer = StringBuilder()
        buffer.append('[')
        val it = arrays.iterator()
        while (it.hasNext()) {
            buffer.append(Arrays.toString(it.next()))
            if (it.hasNext()) {
                buffer.append(", ")
            }
        }
        buffer.append(']')
        return buffer.toString()
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private fun toString(areas: Iterable<Camera.Area>?): String? {
        if (areas == null) {
            return null
        }
        val result = StringBuilder()
        for (area in areas) {
            result.append(area.rect).append(':').append(area.weight).append(' ')
        }
        return result.toString()
    }

    fun collectStats(parameters: Camera.Parameters): String {
        return collectStats(parameters.flatten())
    }

    fun collectStats(flattenedParams: CharSequence?): String {
        val result = StringBuilder(1000)

        result.append("BOARD=").append(Build.BOARD).append('\n')
        result.append("BRAND=").append(Build.BRAND).append('\n')
        result.append("CPU_ABI=").append(Build.CPU_ABI).append('\n')
        result.append("DEVICE=").append(Build.DEVICE).append('\n')
        result.append("DISPLAY=").append(Build.DISPLAY).append('\n')
        result.append("FINGERPRINT=").append(Build.FINGERPRINT).append('\n')
        result.append("HOST=").append(Build.HOST).append('\n')
        result.append("ID=").append(Build.ID).append('\n')
        result.append("MANUFACTURER=").append(Build.MANUFACTURER).append('\n')
        result.append("MODEL=").append(Build.MODEL).append('\n')
        result.append("PRODUCT=").append(Build.PRODUCT).append('\n')
        result.append("TAGS=").append(Build.TAGS).append('\n')
        result.append("TIME=").append(Build.TIME).append('\n')
        result.append("TYPE=").append(Build.TYPE).append('\n')
        result.append("USER=").append(Build.USER).append('\n')
        result.append("VERSION.CODENAME=").append(Build.VERSION.CODENAME).append('\n')
        result.append("VERSION.INCREMENTAL=").append(Build.VERSION.INCREMENTAL).append('\n')
        result.append("VERSION.RELEASE=").append(Build.VERSION.RELEASE).append('\n')
        result.append("VERSION.SDK_INT=").append(Build.VERSION.SDK_INT).append('\n')

        if (flattenedParams != null) {
            val params = SEMICOLON.split(flattenedParams)
            Arrays.sort(params)
            for (param in params) {
                result.append(param).append('\n')
            }
        }

        return result.toString()
    }
}
