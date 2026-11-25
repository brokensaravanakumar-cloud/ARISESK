package com.jinwoo.ai.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.jinwoo.ai.data.Prefs
import java.util.Locale

/**
 * The JIN WOO voice: Android's built-in text-to-speech, pitched down and slowed slightly for a deep,
 * calm, composed delivery. It is an original personality - not a clone of any real actor.
 */
class JinVoice(context: Context, private val prefs: Prefs) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private var queued: (() -> Unit)? = null
    private var currentDone: (() -> Unit)? = null
    private var lastId = ""
    private var counter = 0

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) {
            if (utteranceId == lastId) finish()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (utteranceId == lastId) finish()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (utteranceId == lastId) finish()
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {}
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.setOnUtteranceProgressListener(listener)
                applySettings()
                queued?.invoke()
                queued = null
            } else {
                failed = true
                finish()
            }
        }
    }

    private fun finish() {
        val cb = currentDone
        currentDone = null
        queued = null
        if (cb != null) main.post(cb)
    }

    fun applySettings() {
        val t = tts ?: return
        try {
            var res = t.setLanguage(Locale.forLanguageTag(prefs.language))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                res = t.setLanguage(Locale.US)
            }
            t.setPitch(prefs.pitch)
            t.setSpeechRate(prefs.rate)
            val wanted = prefs.voiceName
            if (wanted.isNotBlank()) {
                t.voices?.firstOrNull { it.name == wanted }?.let { t.voice = it }
            }
        } catch (e: Exception) {
            // keep the engine defaults
        }
    }

    /** English voices installed on this phone, so you can pick the deepest one. */
    fun englishVoices(): List<Voice> = try {
        (tts?.voices ?: emptySet<Voice>())
            .filter { it.locale.language == "en" }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.name }))
    } catch (e: Exception) {
        emptyList()
    }

    fun speak(text: String, onDone: () -> Unit) {
        val clean = clean(text)
        if (clean.isBlank() || failed) {
            main.post(onDone)
            return
        }
        val run = {
            applySettings()
            currentDone = onDone
            counter++
            val chunks = chunk(clean)
            chunks.forEachIndexed { i, c ->
                val id = "jin-$counter-$i"
                lastId = id
                tts?.speak(c, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
            }
        }
        if (ready) run() else queued = run
    }

    fun stop() {
        currentDone = null
        queued = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            // ignore
        }
        tts = null
    }

    companion object {
        fun clean(text: String): String {
            var s = text
            s = s.replace(Regex("https?://\\S+"), "a link")
            s = s.replace(Regex("[*_`#>~]+"), "")
            s = s.replace(Regex("\\s+"), " ")
            return s.trim()
        }

        fun chunk(text: String, max: Int = 3500): List<String> {
            if (text.length <= max) return listOf(text)
            val out = ArrayList<String>()
            val sb = StringBuilder()
            for (sentence in text.split(Regex("(?<=[.!?])\\s+"))) {
                if (sb.length + sentence.length + 1 > max && sb.isNotEmpty()) {
                    out.add(sb.toString())
                    sb.setLength(0)
                }
                if (sentence.length > max) {
                    sentence.chunked(max).forEach { out.add(it) }
                } else {
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(sentence)
                }
            }
            if (sb.isNotEmpty()) out.add(sb.toString())
            return out
        }
    }
}
