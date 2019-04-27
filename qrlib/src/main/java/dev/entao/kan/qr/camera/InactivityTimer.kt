/*
 * Copyright (C) 2010 ZXing authors
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

package dev.entao.kan.qr.camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler

/**
 * Finishes an context after a period of inactivity if the device is on battery power.
 */
class InactivityTimer(private val context: Context, private val callback: Runnable) {
    private val INACTIVITY_DELAY_MS = 5 * 60 * 1000L
    private val powerStatusReceiver: BroadcastReceiver = PowerStatusReceiver()
    private val handler: Handler = Handler()
    private var onBattery: Boolean = false
    private var registered = false


    private fun activity() {
        cancelCallback()
        if (onBattery) {
            handler.postDelayed(callback, INACTIVITY_DELAY_MS)
        }
    }

    fun start() {
        registerReceiver()
        activity()
    }

    fun cancel() {
        cancelCallback()
        unregisterReceiver()
    }

    private fun unregisterReceiver() {
        if (registered) {
            context.unregisterReceiver(powerStatusReceiver)
            registered = false
        }
    }

    private fun registerReceiver() {
        if (!registered) {
            context.registerReceiver(powerStatusReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            registered = true
        }
    }

    private fun cancelCallback() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun onBattery(onBattery: Boolean) {
        this.onBattery = onBattery
        if (registered) {
            activity()
        }
    }

    private inner class PowerStatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_BATTERY_CHANGED == intent.action) {
                // 0 indicates that we're on battery
                val onBatteryNow = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) <= 0
                // post on handler to run in main thread
                handler.post {
                    onBattery(onBatteryNow)
                }
            }
        }
    }

}
