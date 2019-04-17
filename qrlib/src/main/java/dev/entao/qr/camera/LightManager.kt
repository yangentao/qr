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

package dev.entao.qr.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import com.journeyapps.barcodescanner.camera.CameraInstance


class LightManager(private val context: Context, private val cameraManager: CameraInstance) : SensorEventListener {
    private val handler: Handler = Handler()
    private var lightSensor: Sensor? = null

    fun start() {
        val mgr = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = mgr.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sensor != null) {
            mgr.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        lightSensor = sensor
    }

    fun stop() {
        if (lightSensor != null) {
            val mgr = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            mgr.unregisterListener(this)
        }
        lightSensor = null
    }

    private fun setTorch(on: Boolean) {
        handler.post { cameraManager.setTorch(on) }
    }

    override fun onSensorChanged(sensorEvent: SensorEvent) {
        val ambientLightLux = sensorEvent.values[0]
        if (ambientLightLux <= TOO_DARK_LUX) {
            setTorch(true)
        } else if (ambientLightLux >= BRIGHT_ENOUGH_LUX) {
            setTorch(false)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // do nothing
    }

    companion object {

        private val TOO_DARK_LUX = 45.0f
        private val BRIGHT_ENOUGH_LUX = 450.0f
    }
}
