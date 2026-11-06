package com.charge3d.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The full-screen charging experience. Opens over the lock screen / other apps when the
 * charger is plugged in and closes itself when it is unplugged.
 */
class ChargeActivity : ComponentActivity() {

    private lateinit var store: Store
    private lateinit var reader: BatteryReader
    private var glView: ChargeGLView? = null
    private var battery by mutableStateOf(Battery())
    private var config by mutableStateOf(ThemeConfig())
    private var quality by mutableStateOf(Quality.MEDIUM)
    private var presets by mutableStateOf(emptyList<ThemeConfig>())
    private var tone: ToneGenerator? = null

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { finish() }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { applyBattery(reader.read(i)) }
    }

    private fun applyBattery(b: Battery) {
        battery = b
        if (!b.charging && !intent.getBooleanExtra(EXTRA_PREVIEW, false)) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this); reader = BatteryReader(this)
        config = store.current; quality = store.quality; presets = store.presets()

        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        ContextCompat.registerReceiver(
            this, closeReceiver, IntentFilter(ChargeService.ACTION_CLOSE), ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent { Screen() }
        beep()
    }

    override fun onStart() {
        super.onStart()
        // Only listen to the battery while visible; nothing runs when the screen is off.
        val sticky = ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        applyBattery(reader.read(sticky))
    }

    override fun onResume() { super.onResume(); glView?.onResume() }
    override fun onPause() { glView?.onPause(); super.onPause() }

    override fun onStop() {
        runCatching { unregisterReceiver(batteryReceiver) }
        store.current = config
        super.onStop()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(closeReceiver) }
        tone?.release(); tone = null
        super.onDestroy()
    }

    private fun beep() {
        if (!config.sound) return
        runCatching {
            tone?.release()
            tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45).also {
                it.startTone(ToneGenerator.TONE_PROP_ACK, 180)
            }
            Handler(Looper.getMainLooper()).postDelayed({ tone?.release(); tone = null }, 400)
        }
    }

    private fun switchStyle(dir: Int) {
        val all = Style.values()
        val next = all[(config.style.ordinal + dir + all.size) % all.size]
        config = config.copy(style = next)
        beep()
    }

    @Composable
    private fun Screen() {
        // Opened from the app's "Preview" button: start with the studio showing.
        var studioOpen by remember { mutableStateOf(intent.getBooleanExtra(EXTRA_PREVIEW, false)) }
        var styleToast by remember { mutableStateOf<String?>(null) }
        var now by remember { mutableStateOf(LocalDateTime.now()) }

        LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(15_000) } }
        LaunchedEffect(styleToast) { if (styleToast != null) { delay(1600); styleToast = null } }
        // Close a little after reaching 100% so the screen is not left on all night.
        LaunchedEffect(battery.full) {
            val s = store.closeAfterFullSec
            if (battery.full && s > 0 && !intent.getBooleanExtra(EXTRA_PREVIEW, false)) { delay(s * 1000L); finish() }
        }

        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        ChargeGLView(ctx).also { v ->
                            v.onSwipe = { d -> switchStyle(d); styleToast = config.style.label }
                            v.onOpenStudio = { studioOpen = true }
                            v.onTapEffect = { beep() }
                            glView = v
                            v.onResume()
                        }
                    },
                    update = {
                        it.config = config
                        it.targetLevel = battery.percent / 100f
                        it.charging = battery.charging
                        if (it.quality != quality) it.quality = quality
                    }
                )

                // top bar: clock + date, and small controls
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        if (config.showClock) {
                            val is24 = DateFormat.is24HourFormat(this@ChargeActivity)
                            Text(now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm")),
                                color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraLight)
                            Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                                color = Color(0xFFB8C2D6), fontSize = 14.sp)
                        }
                    }
                    IconButton(onClick = { studioOpen = !studioOpen }) {
                        Icon(Icons.Default.Settings, "Customise", tint = Color(0x99FFFFFF))
                    }
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.Default.Close, "Close", tint = Color(0x99FFFFFF))
                    }
                }

                // style name toast after a swipe
                AnimatedVisibility(
                    visible = styleToast != null, enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 120.dp)
                ) {
                    Text(styleToast ?: "", color = Neon, fontSize = 16.sp, letterSpacing = 3.sp)
                }

                // percentage, time left, speed
                Column(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("${battery.percent}%", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Thin)
                    if (config.showTimeLeft) {
                        Text(formatTimeLeft(battery.minutesLeft), color = Neon, fontSize = 17.sp, letterSpacing = 1.sp)
                    }
                    val bits = listOfNotNull(
                        battery.speedLabel.takeIf { it.isNotEmpty() },
                        battery.currentMa.takeIf { it > 0 }?.let { "$it mA" },
                        battery.plug.takeIf { it.isNotEmpty() },
                    )
                    if (bits.isNotEmpty()) {
                        Text(bits.joinToString("  ·  "), color = Color(0xFF8A96AD), fontSize = 12.sp,
                            letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            AnimatedVisibility(
                visible = studioOpen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Studio(
                    config = config, quality = quality, presets = presets,
                    onConfig = { config = it },
                    onQuality = { quality = it; store.quality = it },
                    onSavePreset = { name ->
                        store.savePreset(config.copy(name = name)); presets = store.presets()
                    },
                    onDeletePreset = { store.deletePreset(it); presets = store.presets() },
                    onClose = { studioOpen = false; store.current = config },
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.55f).navigationBarsPadding(),
                )
            }
        }
    }

    companion object {
        /** Set when opened from the app (not a real plug-in) so it stays open without a charger. */
        const val EXTRA_PREVIEW = "preview"
    }
}
