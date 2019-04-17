/*
 * Copyright (C) 2012 ZXing authors
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

@file:Suppress("PrivatePropertyName")

package dev.entao.qr.camera

import android.hardware.Camera
import android.os.Handler
import android.os.Looper
import dev.entao.log.loge

class AutoFocusManager(private val camera: Camera) {
    private val INTERVAL_MS = 2000L
    private val modeSet = hashSetOf(Camera.Parameters.FOCUS_MODE_AUTO, Camera.Parameters.FOCUS_MODE_MACRO)

    private var stopped: Boolean = false
    private var focusing: Boolean = false
    private val useAutoFocus: Boolean

    private val handler: Handler = Handler(Looper.getMainLooper())

    init {
        useAutoFocus = modeSet.contains(camera.parameters.focusMode)
        start()
    }

    fun start() {
        if (!useAutoFocus) {
            return
        }
        stopped = false
        focus()
    }

    private fun focus() {
        if ((!useAutoFocus) || stopped || focusing) {
            return
        }
        try {
            camera.autoFocus(autoFocusCallback)
            focusing = true
        } catch (re: RuntimeException) {
            loge(re)
        }
    }

    private val autoFocusCallback = Camera.AutoFocusCallback { ok, _ ->
        focusing = false
        handler.postDelayed({
            focus()
        }, INTERVAL_MS)

    }

    fun stop() {
        stopped = true
        focusing = false
        if (useAutoFocus) {
            try {
                camera.cancelAutoFocus()
            } catch (re: RuntimeException) {
                loge(re)
            }

        }
    }

}
