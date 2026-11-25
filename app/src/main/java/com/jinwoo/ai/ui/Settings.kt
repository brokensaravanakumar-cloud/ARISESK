package com.jinwoo.ai.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.Identity
import com.jinwoo.ai.access.JinAccessibilityService
import com.jinwoo.ai.ai.AiClient
import com.jinwoo.ai.ai.AiError
import com.jinwoo.ai.ai.ModelInfo
import com.jinwoo.ai.ai.Providers
import com.jinwoo.ai.audio.SoundPlayer
import com.jinwoo.ai.data.History
import com.jinwoo.ai.data.KeyVault
import com.jinwoo.ai.data.Prefs
import com.jinwoo.ai.gmail.GmailAuth
import com.jinwoo.ai.gmail.GmailToken
import com.jinwoo.ai.voice.JinVoice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

enum class Page(val title: String) {
    HOME("CONFIG"), PROVIDER("AI PROVIDER AND KEYS"), MODELS("SELECT MODEL"), WAKE("WAKE WORD"),
    VOICE("VOICE"), THEME("THEME"), SOUND("ACTIVATION SOUND"), PRIVACY("PRIVACY"),
    HISTORY("CONVERSATION HISTORY"), CONTROL("PHONE CONTROL")
}

@Composable
fun SettingsHost(page: Page, onNavigate: (Page) -> Unit, onBack: () -> Unit, onAccent: (Int) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val vault = remember { KeyVault(ctx) }
    val accent = LocalAccent.current

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().then(Modifier.statusBarsPadding())) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                HudButton("< BACK", onBack)
                Spacer(Modifier.padding(6.dp))
                HudText(page.title, color = accent, size = 14, spacing = 2)
            }
            Spacer(Modifier.height(10.dp))
            when (page) {
                Page.HOME -> HomePage(onNavigate)
                Page.PROVIDER -> ProviderPage(prefs, vault, onModels = { onNavigate(Page.MODELS) })
                Page.MODELS -> ModelsPage(prefs, vault, onDone = onBack)
                Page.WAKE -> WakePage(prefs)
                Page.VOICE -> VoicePage(prefs)
                Page.THEME -> ThemePage(prefs, onAccent)
                Page.SOUND -> SoundPage(prefs)
                Page.PRIVACY -> PrivacyPage(prefs, vault)
                Page.HISTORY -> HistoryPage()
                Page.CONTROL -> ControlPage(prefs)
            }
        }
    }
}

@Composable
private fun Scroll(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomePage(go: (Page) -> Unit) {
    Scroll {
        listOf(
            Page.PROVIDER to "Gemini / OpenAI / OpenRouter / Groq / custom - keys, base URL, model, test",
            Page.CONTROL to "Calls, WhatsApp, Gmail, screen control, 24-hour listening permissions",
            Page.WAKE to "Wake phrases, language, follow-up conversation",
            Page.VOICE to "Pitch, speed, voice choice",
            Page.SOUND to "Activation sound - built-in or import your own",
            Page.THEME to "Accent colour, particles, cinematic animation",
            Page.PRIVACY to "History, AI context, confirmations, wipe data",
            Page.HISTORY to "Past conversations"
        ).forEach { (p, desc) ->
            HudPanel(p.title, Modifier.clickable { go(p) }) { HudText(desc, color = TextDim, size = 12) }
        }
    }
}

// ---------------------------------------------------------------------------------------- provider

@Composable
private fun ProviderPage(prefs: Prefs, vault: KeyVault, onModels: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var idx by remember { mutableIntStateOf(Providers.all.indexOfFirst { it.id == prefs.providerId }.coerceAtLeast(0)) }
    val spec = Providers.all[idx]
    var key by remember(spec.id) { mutableStateOf("") }
    var saved by remember(spec.id) { mutableStateOf(vault.masked(spec.id)) }
    var base by remember(spec.id) { mutableStateOf(prefs.baseUrl(spec.id, spec.defaultBase)) }
    var model by remember(spec.id) { mutableStateOf(prefs.model(spec.id, spec.defaultModel)) }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Scroll {
        HudPanel("PROVIDER") {
            Chips(Providers.all.map { it.label }, idx) {
                idx = it
                prefs.providerId = Providers.all[it].id
                result = ""
            }
            Spacer(Modifier.height(8.dp))
            HudText(spec.pricingNote, color = TextDim, size = 11)
            if (spec.keyUrl.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                HudButton("GET A KEY", { uri.openUri(spec.keyUrl) })
            }
        }
        HudPanel("API KEY") {
            HudText("Saved: $saved", color = TextDim, size = 12)
            HudField("Paste ${spec.label} key (${spec.keyHint})", key, { key = it }, secret = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton("SAVE KEY", {
                    vault.set(spec.id, key)
                    key = ""
                    saved = vault.masked(spec.id)
                })
                HudButton("REMOVE", { vault.set(spec.id, ""); saved = vault.masked(spec.id) }, danger = true)
            }
            Spacer(Modifier.height(8.dp))
            HudText(
                "WARNING: a key stored inside a phone app can never be fully protected. It is encrypted with the Android Keystore, " +
                    "but root access or malware could still use it. Use a key with a spending limit and never share this phone's app data.",
                color = TextDim, size = 11
            )
        }
        HudPanel("ENDPOINT AND MODEL") {
            HudField("API base URL", base, { base = it; prefs.setBaseUrl(spec.id, it) })
            HudField("Model ID", model, { model = it; prefs.setModel(spec.id, it) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton("SELECT MODEL", onModels)
                HudButton("RESET", {
                    base = spec.defaultBase; model = spec.defaultModel
                    prefs.setBaseUrl(spec.id, ""); prefs.setModel(spec.id, "")
                })
            }
        }
        HudPanel("TEST CONNECTION") {
            HudButton(if (busy) "TESTING..." else "TEST CONNECTION", {
                scope.launch {
                    busy = true
                    if (key.isNotBlank()) {
                        vault.set(spec.id, key); key = ""; saved = vault.masked(spec.id)
                    }
                    result = try {
                        "OK: " + AiClient(prefs, vault).testConnection()
                    } catch (e: AiError) {
                        "FAILED: " + (e.message ?: "unknown error")
                    } catch (e: Exception) {
                        "FAILED: " + (e.message ?: "unknown error")
                    }
                    busy = false
                }
            }, enabled = !busy)
            if (result.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                HudText(result, size = 12)
            }
        }
    }
}

@Composable
private fun ModelsPage(prefs: Prefs, vault: KeyVault, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var status by remember { mutableStateOf("Loading models...") }
    var filter by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }
    val spec = Providers.byId(prefs.providerId)

    fun load() {
        scope.launch {
            status = "Loading models..."
            try {
                val loaded = AiClient(prefs, vault).listModels()
                models = loaded
                status = "${loaded.size} models from ${spec.label}"
            } catch (e: AiError) {
                status = "FAILED: " + (e.message ?: "")
            } catch (e: Exception) {
                status = "FAILED: " + (e.message ?: "could not load models")
            }
        }
    }
    LaunchedEffect(spec.id) { load() }

    val shown = models.filter {
        (filter.isBlank() || it.id.contains(filter, true) || it.label.contains(filter, true)) &&
            (!freeOnly || it.free == true)
    }
    Column(Modifier.fillMaxSize()) {
        HudText(status, color = TextDim, size = 12)
        HudField("Filter", filter, { filter = it })
        if (spec.id == "openrouter") HudSwitch("Free models only", "Models OpenRouter currently lists as free", freeOnly) { freeOnly = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { HudButton("RELOAD", { load() }) }
        Spacer(Modifier.height(8.dp))
        HudText("Current: " + prefs.model(spec.id, spec.defaultModel), size = 12)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { m ->
                HudPanel(
                    if (m.free == true) "FREE" else if (m.free == false) "PAID" else "MODEL",
                    Modifier.clickable { prefs.setModel(spec.id, m.id); onDone() }
                ) {
                    HudText(m.id, size = 12)
                    if (m.label != m.id) HudText(m.label, color = TextDim, size = 11)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- wake / voice / theme / sound

@Composable
private fun WakePage(prefs: Prefs) {
    var requireHey by remember { mutableStateOf(prefs.requireHey) }
    var offline by remember { mutableStateOf(prefs.preferOffline) }
    var follow by remember { mutableStateOf(prefs.followUp) }
    var extra by remember { mutableStateOf(prefs.extraWakePhrases) }
    val langs = listOf("en-IN", "en-US", "en-GB")
    var lang by remember { mutableIntStateOf(langs.indexOf(prefs.language).coerceAtLeast(0)) }
    Scroll {
        HudPanel("WAKE PHRASES") {
            HudText("ARISE wakes on: \"Arise\", \"Hey Arise\", and also \"Jin Woo\" / \"Hey Jin Woo\".", size = 12)
            HudSwitch("Require \"Hey\"", "Stops false triggers when the word \"arise\" appears in normal talk", requireHey) {
                requireHey = it; prefs.requireHey = it
            }
            HudField("Extra wake phrases (comma separated)", extra, { extra = it; prefs.extraWakePhrases = it })
        }
        HudPanel("LISTENING") {
            HudSwitch("Prefer on-device recognition", "Works offline when the language pack is installed", offline) {
                offline = it; prefs.preferOffline = it
            }
            HudSwitch("Follow-up conversation", "After a reply, keep listening a few seconds without the wake word", follow) {
                follow = it; prefs.followUp = it
            }
            HudText("Language", color = TextDim, size = 12)
            Chips(langs, lang) { lang = it; prefs.language = langs[it] }
        }
        HudPanel("HOW DETECTION WORKS") {
            HudText(
                "This build listens with Android's speech recogniser, which is easy to install but uses more battery than a " +
                    "dedicated keyword spotter. The README explains how to swap in sherpa-onnx for a true offline wake word.",
                color = TextDim, size = 11
            )
        }
    }
}

@Composable
private fun VoicePage(prefs: Prefs) {
    val ctx = LocalContext.current
    val voice = remember { JinVoice(ctx, prefs) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }
    var pitch by remember { mutableStateOf(prefs.pitch) }
    var rate by remember { mutableStateOf(prefs.rate) }
    var speak by remember { mutableStateOf(prefs.speakReplies) }
    var chosen by remember { mutableStateOf(prefs.voiceName) }
    var voices by remember { mutableStateOf(listOf<android.speech.tts.Voice>()) }
    LaunchedEffect(Unit) {
        delay(1500)
        voices = voice.englishVoices()
    }
    Scroll {
        HudPanel("DEEP VOICE") {
            HudSwitch("Speak replies", null, speak) { speak = it; prefs.speakReplies = it }
            HudSlider("Pitch (lower = deeper)", pitch, 0.4f..1.3f, { pitch = it; prefs.pitch = it }) { "%.2f".format(it) }
            HudSlider("Speed", rate, 0.6f..1.4f, { rate = it; prefs.rate = it }) { "%.2f".format(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudButton("TEST", { voice.speak("System online. I am Arise. Command me, Master.") {} })
                HudButton("STOP", { voice.stop() }, danger = true)
            }
        }
        HudPanel("VOICE ENGINE") {
            HudText("Pick the deepest male-sounding English voice on your phone. Install more in Android Settings, Text-to-speech.", color = TextDim, size = 11)
            Spacer(Modifier.height(6.dp))
            HudButton(if (chosen.isBlank()) "DEFAULT (selected)" else "DEFAULT", { chosen = ""; prefs.voiceName = "" })
            voices.take(14).forEach { v ->
                Spacer(Modifier.height(6.dp))
                HudButton((if (v.name == chosen) "> " else "") + v.name, {
                    chosen = v.name; prefs.voiceName = v.name
                    voice.speak("Arise. I am ready, Master.") {}
                })
            }
            if (voices.isEmpty()) HudText("Loading voices...", color = TextDim, size = 11)
        }
    }
}

@Composable
private fun ThemePage(prefs: Prefs, onAccent: (Int) -> Unit) {
    var accent by remember { mutableIntStateOf(prefs.accent) }
    var particles by remember { mutableStateOf(prefs.particles) }
    var count by remember { mutableStateOf(prefs.particleCount.toFloat()) }
    var cinematic by remember { mutableStateOf(prefs.cinematic) }
    Scroll {
        HudPanel("SYSTEM COLOUR") {
            Chips(AccentNames, accent) { accent = it; prefs.accent = it; onAccent(it) }
        }
        HudPanel("EFFECTS") {
            HudSwitch("Animated particles", "Turn off to save battery", particles) { particles = it; prefs.particles = it }
            HudSlider("Particle count", count, 10f..120f, { count = it; prefs.particleCount = it.toInt() }) { it.toInt().toString() }
            HudSwitch("Cinematic activation", "SYSTEM ACTIVATED - WELCOME BACK, MASTER", cinematic) { cinematic = it; prefs.cinematic = it }
        }
    }
}

@Composable
private fun SoundPage(prefs: Prefs) {
    val ctx = LocalContext.current
    val player = remember { SoundPlayer(ctx, prefs) }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    var mode by remember { mutableIntStateOf(prefs.soundMode) }
    var vol by remember { mutableStateOf(prefs.soundVolume) }
    var custom by remember { mutableStateOf(prefs.customSoundUri) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { /* some providers do not allow it */ }
            prefs.customSoundUri = uri.toString()
            prefs.soundMode = 1
            custom = uri.toString()
            mode = 1
        }
    }
    Scroll {
        HudPanel("ACTIVATION SOUND") {
            Chips(listOf("Built-in", "My audio", "Silent"), mode) { mode = it; prefs.soundMode = it }
            Spacer(Modifier.height(8.dp))
            HudButton("IMPORT MY AUDIO THEME", { picker.launch(arrayOf("audio/*")) })
            if (custom.isNotBlank()) HudText("Imported: " + (Uri.parse(custom).lastPathSegment ?: custom), color = TextDim, size = 11)
            HudText("Imported audio is cut off after 6 seconds.", color = TextDim, size = 11)
            HudSlider("Volume", vol, 0f..1f, { vol = it; prefs.soundVolume = it }) { "${(it * 100).toInt()}%" }
            HudButton("PLAY", { player.preview() })
        }
    }
}

// ---------------------------------------------------------------------------------------- privacy / history

@Composable
private fun PrivacyPage(prefs: Prefs, vault: KeyVault) {
    val ctx = LocalContext.current
    var hist by remember { mutableStateOf(prefs.historyEnabled) }
    var context by remember { mutableStateOf(prefs.sendContext) }
    var confirm by remember { mutableStateOf(prefs.confirmSensitive) }
    var done by remember { mutableStateOf("") }
    Scroll {
        HudPanel("DATA") {
            HudSwitch("Save conversation history", "Stored only on this phone", hist) { hist = it; prefs.historyEnabled = it }
            HudSwitch("Send recent messages to the AI", "Lets ARISE remember the last few turns. Off = every question is separate", context) {
                context = it; prefs.sendContext = it
            }
            HudSwitch("Confirm sensitive actions", "Ask yes/no before calls, messages, installs and screen control", confirm) {
                confirm = it; prefs.confirmSensitive = it
            }
        }
        HudPanel("WHERE YOUR DATA GOES") {
            HudText(
                "Your spoken words are converted to text by Android's speech service (Google, unless your phone uses another). " +
                    "The text of your questions goes to the AI provider you chose. Contacts are only searched on the phone. " +
                    "Gmail subjects are read aloud and never sent to the AI. There is no ARISE server.",
                color = TextDim, size = 11
            )
        }
        HudPanel("WIPE") {
            HudButton("DELETE KEYS, HISTORY AND SETTINGS", {
                vault.clearAll(); History(ctx).clear(); prefs.clearAll()
                done = "Everything was deleted."
            }, danger = true)
            if (done.isNotBlank()) HudText(done, size = 12)
        }
    }
}

@Composable
private fun HistoryPage() {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(History(ctx).all().reversed()) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Column(Modifier.fillMaxSize()) {
        HudButton("CLEAR HISTORY", { History(ctx).clear(); items = emptyList() }, danger = true)
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) HudText("No history yet.", color = TextDim, size = 12)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { e ->
                HudPanel((if (e.role == "user") "MASTER" else "ARISE") + "  " + fmt.format(Date(e.time))) {
                    HudText(e.text, size = 12)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- phone control

@Composable
private fun ControlPage(prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    var gmailMsg by remember { mutableStateOf(if (prefs.gmailConnected) "Gmail connected." else "Gmail not connected.") }

    val gmailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        gmailMsg = try {
            Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(r.data)
            prefs.gmailConnected = true
            "Gmail connected."
        } catch (e: Exception) {
            if (r.resultCode == Activity.RESULT_CANCELED) "Cancelled." else "Gmail connection failed: ${e.message}"
        }
    }

    val a11y = tick >= 0 && JinAccessibilityService.isRunning
    val overlay = tick >= 0 && Settings.canDrawOverlays(ctx)
    val battery = tick >= 0 && (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

    Scroll {
        HudPanel("24-HOUR LISTENING") {
            HudText(
                "ARISE listens through a foreground service with a permanent notification. To keep it alive all day, " +
                    "exempt it from battery optimisation, and on Xiaomi, Oppo, Vivo, Samsung and similar phones also set the app to " +
                    "\"Unrestricted\" / \"Autostart\" in the phone's own battery settings.",
                color = TextDim, size = 11
            )
            Spacer(Modifier.height(6.dp))
            HudText("Battery exemption: " + if (battery) "ON" else "OFF", color = if (battery) LocalAccent.current else Danger, size = 12)
            HudButton("OPEN BATTERY SETTINGS", {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
        HudPanel("OPEN APPS FROM THE BACKGROUND") {
            HudText(
                "Android 10+ blocks background apps from opening other apps. Allowing \"Display over other apps\" lets ARISE open " +
                    "YouTube, WhatsApp and the dialer while the screen is off or another app is open. Without it you get a tap-to-continue notification.",
                color = TextDim, size = 11
            )
            Spacer(Modifier.height(6.dp))
            HudText("Display over other apps: " + if (overlay) "ON" else "OFF", color = if (overlay) LocalAccent.current else Danger, size = 12)
            HudButton("ALLOW", {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            })
        }
        HudPanel("SCREEN CONTROL (ACCESSIBILITY)") {
            HudText(
                "Lets ARISE press Install in the Play Store, play a YouTube result, press Send in WhatsApp and Gmail, and follow your " +
                    "step-by-step commands in Chrome and BookMyShow. Limited to those six apps. It never taps payment buttons and never types " +
                    "into password, PIN or OTP fields. Android may show a scary warning - that is normal for any accessibility service. " +
                    "On Android 13+ you may first need App info, the three dots, Allow restricted settings.",
                color = TextDim, size = 11
            )
            Spacer(Modifier.height(6.dp))
            HudText("Screen control: " + if (a11y) "ON" else "OFF", color = if (a11y) LocalAccent.current else Danger, size = 12)
            HudButton("OPEN ACCESSIBILITY SETTINGS", {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
        HudPanel("GMAIL") {
            HudText(
                "Read-only access to your unread mail through Google's own consent screen. Needs a one-time Google Cloud setup " +
                    "described in the README. Sending is never done silently: ARISE opens a draft in Gmail and you confirm.",
                color = TextDim, size = 11
            )
            Spacer(Modifier.height(6.dp))
            HudText(gmailMsg, size = 12)
            HudButton("CONNECT GMAIL", {
                scope.launch {
                    when (val t = GmailAuth.token(ctx)) {
                        is GmailToken.Ok -> { prefs.gmailConnected = true; gmailMsg = "Gmail connected." }
                        is GmailToken.NeedsConsent -> gmailLauncher.launch(IntentSenderRequest.Builder(t.pending).build())
                        is GmailToken.Fail -> gmailMsg = "Failed: " + t.message
                    }
                }
            })
        }
        HudPanel("COMMANDS TO TRY") {
            HudText(
                "Say \"Arise\" first, then:\n" +
                    "- play Believer on YouTube\n- call Mom\n- WhatsApp Rahul saying I will be late\n" +
                    "- set an alarm for 6:30 AM / set a timer for 10 minutes\n- open Instagram\n- install Telegram\n" +
                    "- tell me a story about a dragon\n- do I have new emails\n- book tickets for Kalki on BookMyShow, then tap search, type the name, press enter, tap book tickets\n" +
                    "- what time is it / what is 12 times 5\n- stop (silence) or go to sleep (turn off)",
                color = TextDim, size = 11
            )
        }
    }
}
