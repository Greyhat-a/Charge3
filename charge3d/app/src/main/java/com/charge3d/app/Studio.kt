package com.charge3d.app

import android.graphics.Color as AColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Neon = Color(0xFF00E5FF)
val Glass = Color(0xE60B0F1A)
val GlassBorder = Color(0x2EFFFFFF)

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(), color = Neon, fontSize = 11.sp, letterSpacing = 2.sp,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp)
    )
}

@Composable
private fun <T> Chips(items: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach {
            FilterChip(
                selected = it == selected, onClick = { onPick(it) },
                label = { Text(label(it), fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Neon.copy(alpha = 0.25f),
                    selectedLabelColor = Color.White, labelColor = Color(0xFFB8C2D6)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true, selected = it == selected,
                    borderColor = GlassBorder, selectedBorderColor = Neon
                )
            )
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFFD6DEEE), fontSize = 13.sp)
            Text("%.1f".format(value), color = Color(0xFF8A96AD), fontSize = 12.sp)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Neon, activeTrackColor = Neon,
                inactiveTrackColor = Color(0x33FFFFFF))
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFD6DEEE), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Neon.copy(alpha = 0.6f)))
    }
}

/** HSV colour picker with three gradient sliders for one colour. */
@Composable
private fun ColorPicker(color: Int, onColor: (Int) -> Unit) {
    val hsv = remember(color) { FloatArray(3).also { AColor.colorToHSV(color, it) } }
    fun emit(h: Float, s: Float, v: Float) = onColor(AColor.HSVToColor(floatArrayOf(h, s, v)))
    val hueBrush = Brush.horizontalGradient(List(7) { Color(AColor.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) })
    val satBrush = Brush.horizontalGradient(
        listOf(Color(AColor.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2]))),
               Color(AColor.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2])))))
    val valBrush = Brush.horizontalGradient(
        listOf(Color.Black, Color(AColor.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f)))))
    GradSlider("Hue", hsv[0], 0f..360f, hueBrush) { emit(it, hsv[1], hsv[2]) }
    GradSlider("Saturation", hsv[1], 0f..1f, satBrush) { emit(hsv[0], it, hsv[2]) }
    GradSlider("Brightness", hsv[2], 0f..1f, valBrush) { emit(hsv[0], hsv[1], it) }
    val swatches = listOf(0xFF00E5FF, 0xFF7C4DFF, 0xFFFF1744, 0xFFFFC400, 0xFF00E676, 0xFFFF4081, 0xFF2979FF, 0xFFFFFFFF)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        swatches.forEach {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(it))
                .border(1.dp, GlassBorder, CircleShape).clickable { onColor(it.toInt()) })
        }
    }
}

@Composable
private fun GradSlider(label: String, v: Float, range: ClosedFloatingPointRange<Float>, brush: Brush, onChange: (Float) -> Unit) {
    Text(label, color = Color(0xFF8A96AD), fontSize = 11.sp)
    Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        Slider(
            value = v, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White,
                activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent)
        )
    }
}

/** The whole customisation studio. Every change is applied live via [onConfig]. */
@Composable
fun Studio(
    config: ThemeConfig,
    quality: Quality,
    presets: List<ThemeConfig>,
    onConfig: (ThemeConfig) -> Unit,
    onQuality: (Quality) -> Unit,
    onSavePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var slot by remember { mutableIntStateOf(0) }
    if (slot >= config.colors.size) slot = config.colors.size - 1
    var presetName by remember { mutableStateOf("") }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = Glass, border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Studio", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light,
                    letterSpacing = 3.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Section("Animation")
                Chips(Style.values().toList(), config.style, { it.label }) { onConfig(config.copy(style = it)) }
                Section("Fills as it charges")
                Chips(FillShape.values().toList(), config.shape, { it.label }) { onConfig(config.copy(shape = it)) }

                Section("Colours")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    config.colors.forEachIndexed { i, c ->
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(c))
                            .border(if (i == slot) 3.dp else 1.dp, if (i == slot) Color.White else GlassBorder, CircleShape)
                            .clickable { slot = i })
                    }
                    if (config.colors.size < 4) IconButton(onClick = {
                        onConfig(config.copy(colors = config.colors + config.colors.last())); slot = config.colors.size
                    }) { Icon(Icons.Default.Add, "Add colour", tint = Neon) }
                    if (config.colors.size > 1) IconButton(onClick = {
                        onConfig(config.copy(colors = config.colors.filterIndexed { i, _ -> i != slot }))
                    }) { Icon(Icons.Default.Delete, "Remove colour", tint = Color(0xFFFF5370)) }
                }
                Text(
                    when (config.colors.size) { 1 -> "Single colour"; 2 -> "Two-colour gradient"; else -> "Multi-colour gradient" },
                    color = Color(0xFF8A96AD), fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)
                )
                ColorPicker(config.colors[slot]) { c ->
                    onConfig(config.copy(colors = config.colors.mapIndexed { i, old -> if (i == slot) c else old }))
                }

                Section("Motion & light")
                LabeledSlider("Animation speed", config.speed, 0.2f..3f) { onConfig(config.copy(speed = it)) }
                LabeledSlider("Brightness", config.brightness, 0.4f..1.6f) { onConfig(config.copy(brightness = it)) }
                LabeledSlider("Glow intensity", config.glow, 0f..2f) { onConfig(config.copy(glow = it)) }
                LabeledSlider("Particle density", config.particles, 0f..1f) { onConfig(config.copy(particles = it)) }
                LabeledSlider("Size", config.size, 0.6f..1.4f) { onConfig(config.copy(size = it)) }

                Section("Background")
                Chips(Background.values().toList(), config.background, { it.label }) { onConfig(config.copy(background = it)) }

                Section("Display")
                SwitchRow("Show clock & date", config.showClock) { onConfig(config.copy(showClock = it)) }
                SwitchRow("Show time left", config.showTimeLeft) { onConfig(config.copy(showTimeLeft = it)) }
                SwitchRow("Sound effects", config.sound) { onConfig(config.copy(sound = it)) }

                Section("Performance")
                Chips(Quality.values().toList(), quality, { it.label }) { onQuality(it) }

                Section("My themes")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = presetName, onValueChange = { presetName = it.take(24) },
                        placeholder = { Text("Theme name") }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Neon, unfocusedBorderColor = GlassBorder)
                    )
                    Button(
                        enabled = presetName.isNotBlank(),
                        onClick = { onSavePreset(presetName.trim()); presetName = "" },
                        colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Color.Black)
                    ) { Text("Save") }
                }
                presets.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color(0x14FFFFFF)).clickable { onConfig(p) }.padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(
                                Brush.horizontalGradient(p.colors.map { Color(it) }.let { if (it.size == 1) it + it else it })))
                            Column {
                                Text(p.name, color = Color.White, fontSize = 14.sp)
                                Text("${p.style.label} · ${p.shape.label}", color = Color(0xFF8A96AD), fontSize = 11.sp)
                            }
                        }
                        IconButton(onClick = { onDeletePreset(p.name) }) {
                            Icon(Icons.Default.Delete, "Delete theme", tint = Color(0xFF8A96AD))
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
