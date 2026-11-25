package com.jinwoo.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private class Particle(
    val x: Float, val y: Float, val speed: Float, val size: Float, val phase: Float, val purple: Boolean
)

/** Drifting blue and purple motes. Turn off in Theme settings to save battery. */
@Composable
fun ParticleField(modifier: Modifier, count: Int, accent: Color) {
    val particles = remember(count) {
        List(count) {
            Particle(
                Random.nextFloat(), Random.nextFloat(), 0.015f + Random.nextFloat() * 0.05f,
                1f + Random.nextFloat() * 2.6f, Random.nextFloat() * 6.28f, Random.nextFloat() < 0.35f
            )
        }
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(count) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) t += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    Canvas(modifier) {
        val d = density
        particles.forEach { p ->
            val y = (((p.y - t * p.speed) % 1f) + 1f) % 1f
            val x = p.x + sin(t * 0.5f + p.phase) * 0.02f
            val a = 0.2f + 0.55f * abs(sin(t * 0.8f + p.phase))
            val c = if (p.purple) Violet else accent
            val center = Offset(x * size.width, y * size.height)
            drawCircle(c.copy(alpha = a * 0.18f), radius = p.size * d * 3.2f, center = center)
            drawCircle(c.copy(alpha = a), radius = p.size * d, center = center)
        }
    }
}

/** The glowing Shadow Monarch eyes inside rotating rune rings. energy 0..1 controls brightness. */
@Composable
fun ShadowEyes(modifier: Modifier, energy: Float, accent: Color) {
    val inf = rememberInfiniteTransition(label = "eyes")
    val pulse by inf.animateFloat(
        0.72f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val spin by inf.animateFloat(
        0f, 360f, infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "spin"
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val k = (0.3f + 0.7f * energy) * pulse
        val c = Offset(cx, cy)

        rotate(spin, c) {
            drawCircle(
                accent.copy(alpha = 0.3f * k), radius = w * 0.46f,
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 18f)))
            )
        }
        rotate(-spin * 0.6f, c) {
            drawCircle(
                Violet.copy(alpha = 0.45f * k), radius = w * 0.39f,
                style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(46f, 30f)))
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.38f * k), DeepPurple.copy(alpha = 0.25f * k), Color.Transparent),
                center = c, radius = w * 0.45f
            ),
            radius = w * 0.45f, center = c
        )

        fun eye(side: Float): Path = Path().apply {
            moveTo(cx + side * w * 0.30f, cy - h * 0.085f)
            lineTo(cx + side * w * 0.05f, cy + h * 0.035f)
            lineTo(cx + side * w * 0.075f, cy + h * 0.085f)
            lineTo(cx + side * w * 0.27f, cy - h * 0.02f)
            close()
        }
        listOf(-1f, 1f).forEach { side ->
            val p = eye(side)
            drawPath(p, accent.copy(alpha = 0.18f * k), style = Stroke(width = 30f, join = StrokeJoin.Round, cap = StrokeCap.Round))
            drawPath(p, accent.copy(alpha = 0.40f * k), style = Stroke(width = 14f, join = StrokeJoin.Round, cap = StrokeCap.Round))
            drawPath(p, Color(0xFFE8FBFF).copy(alpha = (0.55f + 0.45f * energy).coerceAtMost(1f)), style = Fill)
        }
    }
}

/** Cinematic "SYSTEM ACTIVATED / WELCOME BACK, MASTER" overlay, played when ARISE wakes. */
@Composable
fun ActivationOverlay(activationId: Long, enabled: Boolean, accent: Color) {
    var visible by remember { mutableStateOf(false) }
    var line1 by remember { mutableStateOf("") }
    var line2 by remember { mutableStateOf("") }
    LaunchedEffect(activationId) {
        if (activationId == 0L || !enabled) return@LaunchedEffect
        line1 = ""
        line2 = ""
        visible = true
        for (ch in "SYSTEM ACTIVATED") {
            line1 += ch
            delay(45)
        }
        delay(250)
        for (ch in "WELCOME BACK, MASTER") {
            line2 += ch
            delay(40)
        }
        delay(1100)
        visible = false
    }
    AnimatedVisibility(visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(600))) {
        Box(Modifier.fillMaxSize().background(Void.copy(alpha = 0.93f)), contentAlignment = Alignment.Center) {
            ParticleField(Modifier.fillMaxSize(), 70, accent)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                ShadowEyes(Modifier.size(300.dp), 1f, accent)
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.fillMaxWidth()
                        .border(androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.7f)), RoundedCornerShape(4.dp))
                        .background(PanelBg.copy(alpha = 0.7f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HudText("[ NOTIFICATION ]", color = accent.copy(alpha = 0.7f), size = 11, spacing = 3)
                        Spacer(Modifier.height(8.dp))
                        HudText(line1, color = accent, size = 22, spacing = 4)
                        Spacer(Modifier.height(6.dp))
                        HudText(line2, color = TextMain, size = 15, spacing = 3)
                    }
                }
            }
        }
    }
}

/** Small futuristic toast, shown when the service posts a notice. */
@Composable
fun SystemNotice(text: String, id: Long, accent: Color) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id == 0L || text.isBlank()) return@LaunchedEffect
        visible = true
        delay(2800)
        visible = false
    }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxWidth().padding(12.dp)
                .background(PanelBg.copy(alpha = 0.95f), RoundedCornerShape(4.dp))
                .border(androidx.compose.foundation.BorderStroke(1.dp, accent), RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Column {
                HudText("[!] SYSTEM NOTICE", color = accent, size = 11, spacing = 2)
                HudText(text, size = 13)
            }
        }
    }
}
