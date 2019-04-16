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

package com.journeyapps.barcodescanner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.View
import com.google.zxing.ResultPoint
import dev.entao.appbase.App
import dev.entao.qr.ZColor
import java.util.*


/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class ViewfinderView(context: Context) : View(context) {

    protected val paint: Paint
    protected var resultBitmap: Bitmap? = null
    protected val maskColor: Int
    protected val resultColor: Int
    protected val laserColor: Int
    protected val resultPointColor: Int
    protected var scannerAlpha: Int = 0
    protected var possibleResultPoints: MutableList<ResultPoint>
    protected var lastPossibleResultPoints: List<ResultPoint>? = null
    protected var cameraPreview: CameraPreview? = null

    // Cache the framingRect and previewFramingRect, so that we can still draw it after the preview
    // stopped.
    protected var framingRect: Rect? = null
    protected var previewFramingRect: Rect? = null

    private val cw = App.dp2px(25)
    private val ch = App.dp2px(5)
    private val cm = -App.dp2px(5)

    init {

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = Paint(Paint.ANTI_ALIAS_FLAG)

        this.maskColor = ZColor.viewfinder_mask
        this.resultColor = ZColor.result_view
        this.laserColor = ZColor.viewfinder_laser
        this.resultPointColor = ZColor.possible_result_points


        scannerAlpha = 0
        possibleResultPoints = ArrayList(5)
        lastPossibleResultPoints = null
    }

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

    protected fun refreshSizes() {
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
        if (framingRect == null || previewFramingRect == null) {
            return
        }

        val frame = framingRect
        val previewFrame = previewFramingRect

        val width = canvas.width
        val height = canvas.height

        // Draw the exterior (i.e. outside the framing rect) darkened
        paint.color = if (resultBitmap != null) resultColor else maskColor
        canvas.drawRect(0f, 0f, width.toFloat(), frame!!.top.toFloat(), paint)
        canvas.drawRect(0f, frame.top.toFloat(), frame.left.toFloat(), (frame.bottom + 1).toFloat(), paint)
        canvas.drawRect((frame.right + 1).toFloat(), frame.top.toFloat(), width.toFloat(), (frame.bottom + 1).toFloat(), paint)
        canvas.drawRect(0f, (frame.bottom + 1).toFloat(), width.toFloat(), height.toFloat(), paint)

        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            paint.alpha = CURRENT_POINT_OPACITY
            canvas.drawBitmap(resultBitmap!!, null, frame, paint)
        } else {
            // Draw a red "laser scanner" line through the middle to show decoding is active
            paint.color = laserColor
            paint.alpha = SCANNER_ALPHA[scannerAlpha]
            scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.size
            val middle = frame.height() / 2 + frame.top
            canvas.drawRect((frame.left + 2).toFloat(), (middle - 1).toFloat(), (frame.right - 1).toFloat(), (middle + 2).toFloat(), paint)

            val scaleX = frame.width() / previewFrame!!.width().toFloat()
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
        }
        drawCorners(canvas, frame)
    }

    fun drawViewfinder() {
        val resultBitmap = this.resultBitmap
        this.resultBitmap = null
        resultBitmap?.recycle()
        invalidate()
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live scanning display.
     *
     * @param result An image of the result.
     */
    fun drawResultBitmap(result: Bitmap) {
        resultBitmap = result
        invalidate()
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

        protected val SCANNER_ALPHA = intArrayOf(0, 64, 128, 192, 255, 192, 128, 64)
        protected val ANIMATION_DELAY = 80L
        protected val CURRENT_POINT_OPACITY = 0xA0
        protected val MAX_RESULT_POINTS = 20
        protected val POINT_SIZE = 6
    }
}
