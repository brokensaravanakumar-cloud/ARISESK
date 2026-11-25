package com.jinwoo.ai.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.jinwoo.ai.JinApp
import com.jinwoo.ai.MainActivity
import com.jinwoo.ai.R
import com.jinwoo.ai.ai.AiClient
import com.jinwoo.ai.audio.SoundPlayer
import com.jinwoo.ai.core.AssistantState
import com.jinwoo.ai.core.AssistantState.Phase
import com.jinwoo.ai.core.Cmd
import com.jinwoo.ai.core.CommandParser
import com.jinwoo.ai.core.Executor
import com.jinwoo.ai.core.Outcome
import com.jinwoo.ai.core.OverlayAnchor
import com.jinwoo.ai.core.WakeMatcher
import com.jinwoo.ai.data.History
import com.jinwoo.ai.data.KeyVault
import com.jinwoo.ai.data.Prefs
import com.jinwoo.ai.voice.JinVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * The always-on listener. Flow:
 *   wake phrase -> activation sound + animation -> listen -> local command or AI -> spoken reply -> listen again.
 *
 * Wake detection uses Android's speech recognizer (on-device when the language pack is installed).
 * See README for upgrading to sherpa-onnx, a dedicated offline keyword spotter.
 */
class AssistantService : Service() {

    companion object {
        const val ACTION_START = "com.jinwoo.ai.START"
        const val ACTION_STOP = "com.jinwoo.ai.STOP"
        const val ACTION_TALK = "com.jinwoo.ai.TALK"
        const val ACTION_STOP_SPEAKING = "com.jinwoo.ai.STOP_SPEAKING"
        const val ACTION_CONFIRM = "com.jinwoo.ai.CONFIRM"
        const val ACTION_TEXT = "com.jinwoo.ai.TEXT"
        const val EXTRA_TEXT = "text"
        const val EXTRA_YES = "yes"
        private const val NOTIF_ID = 7

        @Volatile
        var running: Boolean = false

        fun intent(ctx: Context, action: String): Intent =
            Intent(ctx, AssistantService::class.java).setAction(action)
    }

    private enum class Purpose { WAKE, COMMAND, FOLLOWUP, CONFIRM }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var prefs: Prefs
    private lateinit var history: History
    private lateinit var voice: JinVoice
    private lateinit var sounds: SoundPlayer
    private lateinit var exec: Executor

    private var recognizer: SpeechRecognizer? = null
    private var purpose = Purpose.WAKE
    private var listenGen = 0
    private var errorStreak = 0
    private var forceOnline = false
    private var pending: Outcome.Confirm? = null
    private var confirmRetries = 0
    private var wakeRunnable: Runnable? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        history = History(this)
        voice = JinVoice(this, prefs)
        sounds = SoundPlayer(this, prefs)
        exec = Executor(this, prefs, AiClient(prefs, KeyVault(this)))
        running = true
        OverlayAnchor.show(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification("[ SYSTEM ] Listening for ARISE"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } catch (e: Exception) {
            AssistantState.notice("Could not start listening: ${e.message ?: "permission missing"}")
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_TALK -> onWake("")
            ACTION_STOP_SPEAKING -> {
                voice.stop()
                sounds.stop()
                returnToWake()
            }
            ACTION_CONFIRM -> answerConfirm(intent?.getBooleanExtra(EXTRA_YES, false) ?: false)
            ACTION_TEXT -> handleTyped(intent?.getStringExtra(EXTRA_TEXT) ?: "")
            else -> if (!started) {
                started = true
                returnToWake()
            }
        }
        started = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        stopListening()
        voice.shutdown()
        sounds.stop()
        OverlayAnchor.hide(this)
        AssistantState.update { AssistantState.Ui() }
        super.onDestroy()
    }

    private fun shutdown() {
        running = false
        stopListening()
        voice.stop()
        sounds.stop()
        AssistantState.update { AssistantState.Ui() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ------------------------------------------------------------------ listening

    private fun returnToWake() {
        pending = null
        confirmRetries = 0
        AssistantState.update { it.copy(phase = Phase.WAKE, partial = "", confirmQuestion = null) }
        updateNotification("[ SYSTEM ] Listening for ARISE")
        startListening(Purpose.WAKE, 0)
    }

    private fun startListening(p: Purpose, delayMs: Long) {
        stopListening()
        purpose = p
        val gen = ++listenGen
        main.postDelayed({
            if (gen == listenGen && running) doListen(p, gen)
        }, delayMs)
    }

    private fun stopListening() {
        listenGen++
        wakeRunnable?.let { main.removeCallbacks(it) }
        wakeRunnable = null
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            // ignore
        }
        recognizer = null
    }

    private fun doListen(p: Purpose, gen: Int) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            AssistantState.notice("Speech recognition is not available. Install or enable the Google app, then restart.")
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                if (gen != listenGen) return
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                onPartial(p, text, gen)
            }

            override fun onResults(results: Bundle?) {
                if (gen != listenGen) return
                errorStreak = 0
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                onFinal(p, text)
            }

            override fun onError(error: Int) {
                if (gen != listenGen) return
                onRecognizerError(p, error)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.language)
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, prefs.preferOffline && !forceOnline)
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        try {
            r.startListening(i)
        } catch (e: Exception) {
            startListening(p, 1500)
        }
    }

    private fun extraPhrases(): List<String> =
        prefs.extraWakePhrases.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun onPartial(p: Purpose, text: String, gen: Int) {
        if (p != Purpose.WAKE) {
            AssistantState.update { it.copy(partial = text) }
            return
        }
        val m = WakeMatcher.match(text, prefs.requireHey, extraPhrases()) ?: return
        // Fire shortly after the phrase unless more words are still arriving ("Jin Woo, open YouTube").
        wakeRunnable?.let { main.removeCallbacks(it) }
        val r = Runnable {
            if (gen == listenGen) onWake(m.remaining)
        }
        wakeRunnable = r
        main.postDelayed(r, 900)
    }

    private fun onFinal(p: Purpose, text: String) {
        wakeRunnable?.let { main.removeCallbacks(it) }
        wakeRunnable = null
        when (p) {
            Purpose.WAKE -> {
                val m = WakeMatcher.match(text, prefs.requireHey, extraPhrases())
                if (m != null) onWake(m.remaining) else startListening(Purpose.WAKE, 150)
            }
            Purpose.COMMAND -> {
                if (text.isBlank()) speakThen("I did not catch that, Master.") { returnToWake() }
                else process(stripWake(text))
            }
            Purpose.FOLLOWUP -> {
                if (text.isBlank()) returnToWake() else process(stripWake(text))
            }
            Purpose.CONFIRM -> onConfirmSpeech(text)
        }
    }

    private fun stripWake(text: String): String {
        val m = WakeMatcher.match(text, false, extraPhrases())
        return if (m != null && m.remaining.isNotBlank()) m.remaining else text
    }

    private fun onRecognizerError(p: Purpose, error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                AssistantState.notice("Microphone permission is missing. Open the app and allow it.")
                shutdown()
            }
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> when (p) {
                Purpose.WAKE -> startListening(Purpose.WAKE, 200)
                Purpose.COMMAND -> speakThen("I did not hear anything, Master.") { returnToWake() }
                Purpose.FOLLOWUP -> returnToWake()
                Purpose.CONFIRM -> {
                    pending = null
                    speakThen("No answer, so I cancelled it, Master.") { returnToWake() }
                }
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> startListening(p, 1200)
            SpeechRecognizer.ERROR_CLIENT -> startListening(p, 500)
            12, 13 -> { // language pack not installed / unavailable offline
                if (!forceOnline) {
                    forceOnline = true
                    AssistantState.notice("Offline speech pack missing - using online recognition instead.")
                }
                startListening(p, 800)
            }
            else -> {
                errorStreak++
                if (errorStreak == 6) AssistantState.notice("Speech engine keeps failing. Check internet, the Google app and microphone.")
                startListening(p, minOf(1000L * errorStreak, 8000L))
            }
        }
    }

    // ------------------------------------------------------------------ activation + commands

    private fun onWake(remaining: String) {
        stopListening()
        voice.stop()
        val id = System.currentTimeMillis()
        AssistantState.update {
            it.copy(
                phase = Phase.ACTIVATED, activationId = id, transcript = "", reply = "", partial = "",
                notice = "SYSTEM ACTIVATED", noticeId = it.noticeId + 1, confirmQuestion = null
            )
        }
        updateNotification("[ SYSTEM ACTIVATED ]")
        sounds.playActivation {
            if (remaining.isNotBlank()) {
                process(remaining)
            } else {
                AssistantState.update { it.copy(phase = Phase.SPEAKING) }
                voice.speak("Yes, Master.") {
                    AssistantState.update { it.copy(phase = Phase.LISTENING) }
                    startListening(Purpose.COMMAND, 100)
                }
            }
        }
    }

    private fun handleTyped(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        voice.stop()
        sounds.stop()
        if (pending != null) {
            val yn = WakeMatcher.yesNo(t)
            if (yn != null) {
                answerConfirm(yn)
                return
            }
        }
        process(t)
    }

    private fun process(text: String) {
        stopListening()
        val clean = text.trim()
        AssistantState.update { it.copy(phase = Phase.THINKING, transcript = clean, partial = "") }
        if (prefs.historyEnabled) history.add("user", clean)
        scope.launch {
            val now = LocalTime.now()
            val cmd = CommandParser.parse(clean, now.hour, now.minute)
            if (cmd is Cmd.Stop) {
                voice.stop()
                returnToWake()
                return@launch
            }
            if (cmd is Cmd.Sleep) {
                respond("Standing down, Master.") { shutdown() }
                return@launch
            }
            val outcome = try {
                exec.run(cmd)
            } catch (e: Exception) {
                Outcome.Say("Something went wrong, Master. ${e.message ?: ""}".trim())
            }
            when (outcome) {
                is Outcome.Say -> respond(outcome.text, null)
                is Outcome.Confirm -> askConfirm(outcome)
            }
        }
    }

    private fun respond(text: String, after: (() -> Unit)?) {
        if (prefs.historyEnabled) history.add("assistant", text)
        AssistantState.update { it.copy(phase = Phase.SPEAKING, reply = text) }
        updateNotification("[ SYSTEM ] " + text.take(70))
        val finish: () -> Unit = {
            if (after != null) after() else afterReply()
        }
        if (prefs.speakReplies) voice.speak(text, finish) else finish()
    }

    private fun afterReply() {
        if (prefs.followUp) {
            AssistantState.update { it.copy(phase = Phase.LISTENING) }
            startListening(Purpose.FOLLOWUP, 250)
        } else {
            returnToWake()
        }
    }

    private fun speakThen(text: String, then: () -> Unit) {
        AssistantState.update { it.copy(phase = Phase.SPEAKING, reply = text) }
        if (prefs.speakReplies) voice.speak(text, then) else then()
    }

    // ------------------------------------------------------------------ confirmation

    private fun askConfirm(c: Outcome.Confirm) {
        pending = c
        confirmRetries = 0
        AssistantState.update { it.copy(phase = Phase.CONFIRM, confirmQuestion = c.question, reply = c.question) }
        if (prefs.historyEnabled) history.add("assistant", c.question)
        voice.speak(c.question) {
            if (pending != null) {
                startListening(Purpose.CONFIRM, 100)
            }
        }
    }

    private fun onConfirmSpeech(text: String) {
        val yn = WakeMatcher.yesNo(text)
        when {
            yn != null -> answerConfirm(yn)
            confirmRetries < 1 -> {
                confirmRetries++
                voice.speak("Please say yes or no, Master.") { startListening(Purpose.CONFIRM, 100) }
            }
            else -> {
                pending = null
                speakThen("I will take that as a no, Master.") { returnToWake() }
            }
        }
    }

    private fun answerConfirm(yes: Boolean) {
        val c = pending ?: return
        pending = null
        stopListening()
        voice.stop()
        AssistantState.update { it.copy(phase = Phase.THINKING, confirmQuestion = null) }
        if (!yes) {
            respond("Cancelled, Master.", null)
            return
        }
        scope.launch {
            val result = try {
                c.action()
            } catch (e: Exception) {
                "That failed, Master. ${e.message ?: ""}".trim()
            }
            respond(result, null)
        }
    }

    // ------------------------------------------------------------------ notification

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val talk = PendingIntent.getService(this, 2, intent(this, ACTION_TALK), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, intent(this, ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, JinApp.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_jin)
            .setContentTitle("ARISE")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFF19D3FF.toInt())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "TALK", talk)
            .addAction(0, "STOP", stop)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, buildNotification(text))
        } catch (e: Exception) {
            // notifications blocked - listening still works
        }
    }
}
