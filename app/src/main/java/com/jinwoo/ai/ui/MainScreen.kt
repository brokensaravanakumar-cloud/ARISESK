package com.jinwoo.ai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinwoo.ai.core.AssistantState
import com.jinwoo.ai.core.AssistantState.Phase

private fun phaseLabel(p: Phase): String = when (p) {
    Phase.OFF -> "OFFLINE - TAP AWAKEN"
    Phase.WAKE -> "ONLINE - SAY \"ARISE\""
    Phase.ACTIVATED -> "SYSTEM ACTIVATED"
    Phase.LISTENING -> "LISTENING..."
    Phase.THINKING -> "PROCESSING..."
    Phase.SPEAKING -> "SPEAKING"
    Phase.CONFIRM -> "AWAITING CONFIRMATION"
}

private fun phaseEnergy(p: Phase): Float = when (p) {
    Phase.OFF -> 0.12f
    Phase.WAKE -> 0.4f
    Phase.ACTIVATED -> 1f
    Phase.LISTENING -> 0.9f
    Phase.THINKING -> 0.7f
    Phase.SPEAKING -> 0.8f
    Phase.CONFIRM -> 0.85f
}

@Composable
fun MainScreen(
    state: AssistantState.Ui,
    accent: Color,
    particles: Boolean,
    particleCount: Int,
    cinematic: Boolean,
    onAwaken: () -> Unit,
    onSleep: () -> Unit,
    onTalk: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSend: (String) -> Unit,
    onConfirm: (Boolean) -> Unit,
    onSettings: () -> Unit
) {
    val online = state.phase != Phase.OFF
    var typed by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(Void)) {
        if (particles) ParticleField(Modifier.fillMaxSize(), particleCount, accent)

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    HudText("ARISE", color = accent, size = 26, spacing = 8)
                    HudText("SHADOW MONARCH SYSTEM", color = TextDim, size = 10, spacing = 3)
                }
                HudButton("CONFIG", onSettings)
            }

            ShadowEyes(Modifier.fillMaxWidth().height(230.dp), phaseEnergy(state.phase), accent)
            HudText(phaseLabel(state.phase), color = accent, size = 14, spacing = 3)
            Spacer(Modifier.height(10.dp))

            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val heard = if (state.partial.isNotBlank()) state.partial else state.transcript
                if (heard.isNotBlank()) HudPanel("MASTER") { HudText(heard, size = 14) }
                if (state.reply.isNotBlank()) HudPanel("ARISE") { HudText(state.reply, size = 14) }
                if (state.confirmQuestion != null) {
                    HudPanel("CONFIRM ACTION") {
                        HudText(state.confirmQuestion, size = 14)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HudButton("YES", { onConfirm(true) }, Modifier.weight(1f))
                            HudButton("NO", { onConfirm(false) }, Modifier.weight(1f), danger = true)
                        }
                    }
                }
                if (state.transcript.isBlank() && state.reply.isBlank() && state.confirmQuestion == null) {
                    HudPanel("SYSTEM") {
                        HudText(
                            if (online) "Say \"Arise\" then your command. Try: \"Arise, play Believer on YouTube\", " +
                                "\"call Mom\", \"WhatsApp Rahul saying I am late\", \"set an alarm for 6 AM\", \"tell me a story\"."
                            else "Press AWAKEN to start the always-on listener. Add your Gemini key in CONFIG first.",
                            color = TextDim, size = 12
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it },
                    placeholder = { Text("Type a command", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    singleLine = true, modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent, unfocusedBorderColor = accent.copy(alpha = 0.4f),
                        focusedTextColor = TextMain, unfocusedTextColor = TextMain, cursorColor = accent,
                        focusedPlaceholderColor = TextDim, unfocusedPlaceholderColor = TextDim
                    )
                )
                HudButton("SEND", { if (typed.isNotBlank()) { onSend(typed); typed = "" } })
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                HudButton("HUSH", onStopSpeaking, enabled = online)
                TalkButton(accent, online, onTalk)
                if (online) HudButton("SLEEP", onSleep, danger = true) else HudButton("AWAKEN", onAwaken)
            }
        }
    }
}

@Composable
private fun TalkButton(accent: Color, enabled: Boolean, onClick: () -> Unit) {
    val c = if (enabled) accent else accent.copy(alpha = 0.3f)
    Box(
        Modifier.size(78.dp).clip(CircleShape).background(c.copy(alpha = 0.12f))
            .border(BorderStroke(2.dp, c), CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(34.dp)) {
            val w = size.width
            val h = size.height
            drawRoundRect(c, Offset(w * 0.34f, 0f), Size(w * 0.32f, h * 0.58f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f))
            drawArc(c, 0f, 180f, false, Offset(w * 0.16f, h * 0.18f), Size(w * 0.68f, h * 0.55f), style = Stroke(width = 4f))
            drawLine(c, Offset(w / 2, h * 0.72f), Offset(w / 2, h * 0.95f), strokeWidth = 4f)
        }
    }
}
