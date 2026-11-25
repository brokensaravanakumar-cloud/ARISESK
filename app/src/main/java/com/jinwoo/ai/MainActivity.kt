package com.jinwoo.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.jinwoo.ai.core.AssistantState
import com.jinwoo.ai.core.Visibility
import com.jinwoo.ai.data.Prefs
import com.jinwoo.ai.service.AssistantService
import com.jinwoo.ai.ui.ActivationOverlay
import com.jinwoo.ai.ui.AccentColors
import com.jinwoo.ai.ui.AriseTheme
import com.jinwoo.ai.ui.MainScreen
import com.jinwoo.ai.ui.Page
import com.jinwoo.ai.ui.SettingsHost
import com.jinwoo.ai.ui.SystemNotice
import com.jinwoo.ai.ui.Void

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    override fun onStart() {
        super.onStart()
        Visibility.appVisible = true
    }

    override fun onStop() {
        Visibility.appVisible = false
        super.onStop()
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var accentIdx by remember { mutableStateOf(prefs.accent.coerceIn(0, AccentColors.size - 1)) }
    var page by remember { mutableStateOf<Page?>(null) }
    var showWarning by remember { mutableStateOf(!prefs.keyWarningAccepted) }
    val state by AssistantState.flow.collectAsState()
    val accent = AccentColors[accentIdx]

    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.CALL_PHONE)
        }.toTypedArray()
    }
    fun micGranted() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (micGranted()) afterPermission?.invoke()
        else Toast.makeText(ctx, "ARISE needs the microphone to hear you.", Toast.LENGTH_LONG).show()
        afterPermission = null
    }
    fun withPermissions(action: () -> Unit) {
        if (micGranted()) action() else { afterPermission = action; launcher.launch(permissions) }
    }
    fun send(action: String, text: String? = null, yes: Boolean? = null) {
        val i = AssistantService.intent(ctx, action)
        if (text != null) i.putExtra(AssistantService.EXTRA_TEXT, text)
        if (yes != null) i.putExtra(AssistantService.EXTRA_YES, yes)
        ContextCompat.startForegroundService(ctx, i)
    }

    BackHandler(enabled = page != null) {
        page = if (page == Page.MODELS) Page.PROVIDER else if (page == Page.HOME) null else Page.HOME
    }

    AriseTheme(accent) {
        Box(Modifier.fillMaxSize().background(Void)) {
            val p = page
            if (p == null) {
                MainScreen(
                    state = state, accent = accent,
                    particles = prefs.particles, particleCount = prefs.particleCount, cinematic = prefs.cinematic,
                    onAwaken = { withPermissions { send(AssistantService.ACTION_START) } },
                    onSleep = { send(AssistantService.ACTION_STOP) },
                    onTalk = { withPermissions { send(AssistantService.ACTION_TALK) } },
                    onStopSpeaking = { send(AssistantService.ACTION_STOP_SPEAKING) },
                    onSend = { t -> withPermissions { send(AssistantService.ACTION_TEXT, text = t) } },
                    onConfirm = { yes -> send(AssistantService.ACTION_CONFIRM, yes = yes) },
                    onSettings = { page = Page.HOME }
                )
            } else {
                SettingsHost(
                    page = p,
                    onNavigate = { page = it },
                    onBack = { page = if (p == Page.MODELS) Page.PROVIDER else if (p == Page.HOME) null else Page.HOME },
                    onAccent = { accentIdx = it }
                )
            }
            ActivationOverlay(state.activationId, prefs.cinematic, accent)
            SystemNotice(state.notice, state.noticeId, accent)
        }

        if (showWarning) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Before you add an API key") },
                text = {
                    Text(
                        "Your API key is stored on this phone, encrypted with the Android Keystore. That is safer than plain text " +
                            "but not completely safe: anyone with root access or a malicious app on this phone could still misuse it. " +
                            "Use a key with a spending limit, and never share a screenshot of it."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { prefs.keyWarningAccepted = true; showWarning = false }) { Text("I UNDERSTAND") }
                }
            )
        }
    }
}
