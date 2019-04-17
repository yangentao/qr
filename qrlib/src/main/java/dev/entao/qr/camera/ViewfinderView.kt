/*
 * Copyright (C) 2008 ZXing authors
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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import com.google.zxing.ResultPoint
import dev.entao.appbase.App
import dev.entao.qr.QRConfig
import java.util.*


/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class ViewfinderView(context: Context) : View(context) {

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskColor: Int = QRConfig.viewfinderMask
    private val laserColor: Int = QRConfig.viewfinderLaser
    private val resultPointColor: Int = QRConfig.possibleResultPoints
    private var scannerAlpha: Int = 0
    private var possibleResultPoints = ArrayList<ResultPoint>()
    private var lastPossibleResultPoints: List<ResultPoint>? = null
    private var cameraPreview: CameraPreview? = null

    // Cache the framingRect and previewFramingRect, so that we can still draw it after the preview
    // stopped.
    private var framingRect: Rect? = null
    private var previewFramingRect: Rect? = null

    private val cw = App.dp2px(25)
    private val ch = App.dp2px(5)
    private val cm = -App.dp2px(5)


    fun setPreview(view: CameraPreview) {
        this.cameraPreview = view
        view.addStateListener(object : CameraPreview.StateListener {
            override fun previewSized() {
                refreshSizes()
                invalidate()
            }

            override fun previewStarted() {

            }

            override fun previewStopped() {

            }

            override fun cameraError(error: Exception) {

            }
        })
    }

    private fun refreshSizes() {
        if (cameraPreview == null) {
            return
        }
        val framingRect = cameraPreview!!.framingRect
        val previewFramingRect = cameraPreview!!.previewFramingRect
        if (framingRect != null && previewFramingRect != null) {
            this.framingRect = framingRect
            this.previewFramingRect = previewFramingRect
        }
    }

    private fun drawCorners(canvas: Canvas, frame: Rect) {
        val w = cw
        val h = ch
        val m = cm
        paint.color = Color.rgb(50, 150, 0)
        canvas.drawRect((m + frame.left).toFloat(), (m + frame.top).toFloat(), (m + (w + frame.left)).toFloat(), (m + (h + frame.top)).toFloat(), paint)
        canvas.drawRect((m + frame.left).toFloat(), (m + frame.top).toFloat(), (m + (h + frame.left)).toFloat(), (m + (w + frame.top)).toFloat(), paint)

        canvas.drawRect((-m + (0 - w + frame.right)).toFloat(), (m + frame.top).toFloat(), (-m + (1 + frame.right)).toFloat(), (m + (h + frame.top)).toFloat(), paint)
        canvas.drawRect((-m + (-h + frame.right)).toFloat(), (m + frame.top).toFloat(), (-m + frame.right + 1).toFloat(), (m + (w + frame.top)).toFloat(), paint)

        canvas.drawRect((m + frame.left).toFloat(), (-m + (-(h - 1) + frame.bottom)).toFloat(), (m + (w + frame.left)).toFloat(), (-m + (1 + frame.bottom)).toFloat(), paint)
        canvas.drawRect((m + frame.left).toFloat(), (-m + (0 - w + frame.bottom)).toFloat(), (m + (h + frame.left)).toFloat(), (-m + (1 + frame.bottom)).toFloat(), paint)

        canvas.drawRect((-m + (0 - w + frame.right)).toFloat(), (-m + (-(h - 1) + frame.bottom)).toFloat(), (-m + (1 + frame.right)).toFloat(), (-m + (1 + frame.bottom)).toFloat(), paint)
        canvas.drawRect((-m + (-h + frame.right)).toFloat(), (-m + (0 - w + frame.bottom)).toFloat(), (-m + frame.right + 1).toFloat(), (-m + (w - (w - 1) + frame.bottom)).toFloat(), paint)
    }

    @SuppressLint("DrawAllocation")
    public override fun onDraw(canvas: Canvas) {
        refreshSizes()
        val frame = framingRect ?: return
        val previewFrame = previewFramingRect ?: return

        val width = this.width // canvas.width
        val height = this.height // canvas.height

        // Draw the exterior (i.e. outside the framing rect) darkened
        paint.color = maskColor
        canvas.drawRect(0f, 0f, width.toFloat(), frame.top.toFloat(), paint)
        canvas.drawRect(0f, frame.top.toFloat(), frame.left.toFloat(), (frame.bottom + 1).toFloat(), paint)
        canvas.drawRect((frame.right + 1).toFloat(), frame.top.toFloat(), width.toFloat(), (frame.bottom + 1).toFloat(), paint)
        canvas.drawRect(0f, (frame.bottom + 1).toFloat(), width.toFloat(), height.toFloat(), paint)

        // Draw a red "laser scanner" line through the middle to show decoding is active
        paint.color = laserColor
        paint.alpha = SCANNER_ALPHA[scannerAlpha]
        scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.size
        val middle = frame.height() / 2 + frame.top
        canvas.drawRect((frame.left + 2).toFloat(), (middle - 1).toFloat(), (frame.right - 1).toFloat(), (middle + 2).toFloat(), paint)

        val scaleX = frame.width() / previewFrame.width().toFloat()
        val scaleY = frame.height() / previewFrame.height().toFloat()

        val currentPossible = possibleResultPoints
        val currentLast = lastPossibleResultPoints
        val frameLeft = frame.left
        val frameTop = frame.top
        if (currentPossible.isEmpty()) {
            lastPossibleResultPoints = null
        } else {
            possibleResultPoints = ArrayList(5)
            lastPossibleResultPoints = currentPossible
            paint.alpha = CURRENT_POINT_OPACITY
            paint.color = resultPointColor
            for (point in currentPossible) {
                canvas.drawCircle(
                    (frameLeft + (point.x * scaleX).toInt()).toFloat(),
                    (frameTop + (point.y * scaleY).toInt()).toFloat(),
                    POINT_SIZE.toFloat(), paint
                )
            }
        }
        if (currentLast != null) {
            paint.alpha = CURRENT_POINT_OPACITY / 2
            paint.color = resultPointColor
            val radius = POINT_SIZE / 2.0f
            for (point in currentLast) {
                canvas.drawCircle(
                    (frameLeft + (point.x * scaleX).toInt()).toFloat(),
                    (frameTop + (point.y * scaleY).toInt()).toFloat(),
                    radius, paint
                )
            }
        }

        // Request another update at the animation interval, but only repaint the laser line,
        // not the entire viewfinder mask.
        postInvalidateDelayed(
            ANIMATION_DELAY,
            frame.left - POINT_SIZE,
            frame.top - POINT_SIZE,
            frame.right + POINT_SIZE,
            frame.bottom + POINT_SIZE
        )

        drawCorners(canvas, frame)
    }


    /**
     * Only call from the UI thread.
     *
     * @param point a point to draw, relative to the preview frame
     */
    fun addPossibleResultPoint(point: ResultPoint) {
        val points = possibleResultPoints
        points.add(point)
        val size = points.size
        if (size > MAX_RESULT_POINTS) {
            // trim it
            points.subList(0, size - MAX_RESULT_POINTS / 2).clear()
        }
    }

    companion object {

        private val SCANNER_ALPHA = intArrayOf(0, 64, 128, 192, 255, 192, 128, 64)
        private const val ANIMATION_DELAY = 80L
        private const val CURRENT_POINT_OPACITY = 0xA0
        private const val MAX_RESULT_POINTS = 20
        private const val POINT_SIZE = 6
    }
}
