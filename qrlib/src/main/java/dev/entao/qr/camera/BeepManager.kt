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

package dev.entao.qr.camera

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Vibrator
import dev.entao.qr.R
import java.io.Closeable
import java.io.IOException

/**
 * Manages beeps and vibrations.
 */
class BeepManager(private val activity: Activity) : MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, Closeable {
    private val BEEP_VOLUME = 0.10f
    private val VIBRATE_DURATION = 200L

    private var mediaPlayer: MediaPlayer? = null
    private var playBeep: Boolean = false

    var isBeepEnabled = true
    var isVibrateEnabled = false

    init {
        updatePrefs()
    }

    @Synchronized
    fun updatePrefs() {
        playBeep = shouldBeep(isBeepEnabled)
        if (playBeep && mediaPlayer == null) {
            activity.volumeControlStream = AudioManager.STREAM_MUSIC
            mediaPlayer = buildMediaPlayer(activity)
        }
    }

    private fun shouldBeep(beep: Boolean): Boolean {
        if (beep) {
            val audioService = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audioService.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                return false
            }
        }
        return beep
    }

    @Synchronized
    fun playBeepSoundAndVibrate() {
        if (playBeep && mediaPlayer != null) {
            mediaPlayer?.start()
        }
        if (isVibrateEnabled) {
            val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VIBRATE_DURATION)
        }
    }

    private fun buildMediaPlayer(activity: Context): MediaPlayer? {
        val player = MediaPlayer()
        player.setAudioStreamType(AudioManager.STREAM_MUSIC)
        player.setOnCompletionListener(this)
        player.setOnErrorListener(this)
        try {
            val file = activity.resources.openRawResourceFd(R.raw.zxing_beep)
            file.use { f ->
                player.setDataSource(f.fileDescriptor, f.startOffset, f.length)
            }
            player.setVolume(BEEP_VOLUME, BEEP_VOLUME)
            player.prepare()
            return player
        } catch (ioe: IOException) {
            player.release()
        }
        return null
    }

    override fun onCompletion(mp: MediaPlayer) {
        mp.seekTo(0)
    }

    @Synchronized
    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            activity.finish()
        } else {
            mp.release()
            mediaPlayer = null
            updatePrefs()
        }
        return true
    }

    @Synchronized
    override fun close() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

}
