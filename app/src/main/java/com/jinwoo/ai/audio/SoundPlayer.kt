package com.jinwoo.ai.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.jinwoo.ai.R
import com.jinwoo.ai.data.Prefs

/** Plays the activation sound: built-in original, your own imported file, or silence. */
class SoundPlayer(private val ctx: Context, private val prefs: Prefs) {
    private val main = Handler(Looper.getMainLooper())
    private var mp: MediaPlayer? = null
    private var token = 0

    fun playActivation(onDone: () -> Unit) {
        play(R.raw.activation, honorMode = true, onDone = onDone)
    }

    fun playNotice() {
        play(R.raw.notify, honorMode = false, onDone = {})
    }

    /** Used by the settings screen to preview the chosen sound. */
    fun preview(onDone: () -> Unit = {}) = playActivation(onDone)

    private fun play(rawRes: Int, honorMode: Boolean, onDone: () -> Unit) {
        stop()
        if (honorMode && prefs.soundMode == 2) {
            onDone()
            return
        }
        val my = ++token
        var finished = false
        fun done() {
            if (finished || my != token) return
            finished = true
            release()
            onDone()
        }
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            if (honorMode && prefs.soundMode == 1 && prefs.customSoundUri.isNotBlank()) {
                p.setDataSource(ctx, Uri.parse(prefs.customSoundUri))
            } else {
                val afd = ctx.resources.openRawResourceFd(rawRes)
                p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }
            val v = prefs.soundVolume.coerceIn(0f, 1f)
            p.setVolume(v, v)
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { done() }
            p.setOnErrorListener { _, _, _ -> done(); true }
            mp = p
            p.prepareAsync()
            // Safety cap so a very long imported track can never block the assistant.
            main.postDelayed({ done() }, 6000)
        } catch (e: Exception) {
            done()
        }
    }

    private fun release() {
        try {
            mp?.release()
        } catch (e: Exception) {
            // ignore
        }
        mp = null
    }

    fun stop() {
        token++
        release()
    }
}
