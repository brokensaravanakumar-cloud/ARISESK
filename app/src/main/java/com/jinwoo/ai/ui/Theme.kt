package com.jinwoo.ai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Void = Color(0xFF000000)
val PanelBg = Color(0xFF060A14)
val Violet = Color(0xFF7B2CFF)
val DeepPurple = Color(0xFF2A0B5E)
val TextMain = Color(0xFFD6F3FF)
val TextDim = Color(0xFF7FA6C2)
val Danger = Color(0xFFFF4D6D)

val AccentNames = listOf("Monarch Blue", "Shadow Violet", "Arise Indigo")
val AccentColors = listOf(Color(0xFF19D3FF), Color(0xFF9B5CFF), Color(0xFF4D7CFF))

val LocalAccent = compositionLocalOf { Color(0xFF19D3FF) }

@Composable
fun AriseTheme(accent: Color, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent, onPrimary = Void, secondary = Violet,
            background = Void, surface = PanelBg, onSurface = TextMain, onBackground = TextMain
        )
    ) {
        CompositionLocalProvider(LocalAccent provides accent) { content() }
    }
}

private val Mono = FontFamily.Monospace

@Composable
fun HudText(text: String, color: Color = TextMain, size: Int = 14, spacing: Int = 0, modifier: Modifier = Modifier) {
    Text(text, color = color, fontFamily = Mono, fontSize = size.sp, letterSpacing = spacing.sp, modifier = modifier)
}

@Composable
fun HudPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val a = LocalAccent.current
    Column(
        modifier
            .fillMaxWidth()
            .background(PanelBg.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, a.copy(alpha = 0.55f)), RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        HudText("[ $title ]", color = a, size = 12, spacing = 2)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
fun HudButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    val c = if (danger) Danger else LocalAccent.current
    val col = if (enabled) c else c.copy(alpha = 0.3f)
    Box(
        modifier
            .background(col.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, col), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        HudText(text, color = col, size = 13, spacing = 1)
    }
}

@Composable
fun HudSwitch(label: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val a = LocalAccent.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            HudText(label, size = 14)
            if (desc != null) HudText(desc, color = TextDim, size = 11)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Void, checkedTrackColor = a, uncheckedTrackColor = PanelBg)
        )
    }
}

@Composable
fun HudSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, fmt: (Float) -> String) {
    val a = LocalAccent.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        HudText("$label: ${fmt(value)}", size = 13)
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = a, activeTrackColor = a, inactiveTrackColor = a.copy(alpha = 0.25f))
        )
    }
}

@Composable
fun HudField(label: String, value: String, onChange: (String) -> Unit, secret: Boolean = false, singleLine: Boolean = true) {
    val a = LocalAccent.current
    var show by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = value, onValueChange = onChange,
            label = { Text(label, fontFamily = Mono, fontSize = 12.sp) },
            singleLine = singleLine,
            visualTransformation = if (secret && !show) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = a, unfocusedBorderColor = a.copy(alpha = 0.4f),
                focusedTextColor = TextMain, unfocusedTextColor = TextMain, cursorColor = a,
                focusedLabelColor = a, unfocusedLabelColor = TextDim
            )
        )
        if (secret) {
            Spacer(Modifier.height(4.dp))
            HudText(if (show) "[ HIDE ]" else "[ SHOW ]", color = a, size = 11, modifier = Modifier.clickable { show = !show })
        }
    }
}

@Composable
fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val a = LocalAccent.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, name ->
            val on = i == selected
            Box(
                Modifier
                    .background(if (on) a.copy(alpha = 0.25f) else Void, RoundedCornerShape(4.dp))
                    .border(BorderStroke(1.dp, if (on) a else a.copy(alpha = 0.35f)), RoundedCornerShape(4.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) { HudText(name, color = if (on) a else TextDim, size = 12) }
        }
    }
}
