package com.charge3d.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/** Home screen: master switch, permission set-up with explanations, and entry to the studio. */
class MainActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)   // bumped on resume to re-check permissions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Store(this).autoPopup) ChargeService.start(this)
        setContent { Home() }
    }

    override fun onResume() { super.onResume(); refresh++ }

    // ---- permission state ----
    private fun notifOk() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun overlayOk() = Settings.canDrawOverlays(this)
    private fun fullScreenOk() = Build.VERSION.SDK_INT < 34 ||
        getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    private fun batteryOk() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun open(i: Intent) = runCatching { startActivity(i) }.onFailure {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    @Composable
    private fun Home() {
        val store = remember { Store(this) }
        var auto by remember { mutableStateOf(store.autoPopup) }
        var closeSec by remember { mutableIntStateOf(store.closeAfterFullSec) }
        @Suppress("UNUSED_VARIABLE") val tick = refresh   // re-read permission state after returning from Settings
        val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF05070D), Color(0xFF0B1226), Color(0xFF05070D))))
        ) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("CHARGE 3D", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Thin, letterSpacing = 6.sp)
                Text("Turn charging into a show.", color = Color(0xFF8A96AD), fontSize = 14.sp)

                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pop up when plugged in", color = Color.White, fontSize = 16.sp)
                            Text(if (auto) "Active — waiting for a charger" else "Off", color = if (auto) Neon else Color(0xFF8A96AD), fontSize = 12.sp)
                        }
                        Switch(auto, {
                            auto = it; store.autoPopup = it
                            if (it) ChargeService.start(this@MainActivity) else ChargeService.stop(this@MainActivity)
                        }, colors = SwitchDefaults.colors(checkedTrackColor = Neon.copy(alpha = 0.6f)))
                    }
                }

                Button(
                    onClick = { startActivity(Intent(this@MainActivity, ChargeActivity::class.java).putExtra(ChargeActivity.EXTRA_PREVIEW, true)) },
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Color.Black)
                ) { Text("Preview & customise", fontSize = 16.sp, fontWeight = FontWeight.Medium) }

                Text("SET UP", color = Neon, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 10.dp))
                Text("Each permission is optional, but the pop-up works best with all of them on.",
                    color = Color(0xFF8A96AD), fontSize = 12.sp)

                PermCard("Notifications", "Lets the charger watcher run and shows the charging screen over your lock screen.",
                    notifOk(), "Allow") { if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                PermCard("Display over other apps", "Lets the animation open instantly on top of whatever you are doing when you plug in.",
                    overlayOk(), "Open settings") { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
                if (Build.VERSION.SDK_INT >= 34) PermCard("Full-screen notifications",
                    "Wakes the screen and shows the animation on the lock screen.",
                    fullScreenOk(), "Open settings") {
                    open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
                }
                PermCard("Run in background", "Stops Android from putting the charger watcher to sleep. Choose “Don’t optimise” for CHARGE 3D.",
                    batteryOk(), "Open settings") { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                Text("Tecno, Infinix, Xiaomi, Oppo and Samsung phones may also have an “Auto-start” or “Unrestricted battery” option in the app's settings.",
                    color = Color(0xFF6F7B93), fontSize = 11.sp)

                Text("BEHAVIOUR", color = Neon, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 10.dp))
                GlassCard {
                    Text("Close the screen after 100%", color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Never", 30 to "30 sec", 120 to "2 min").forEach { (s, label) ->
                            FilterChip(selected = closeSec == s, onClick = { closeSec = s; store.closeAfterFullSec = s },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Neon.copy(alpha = 0.25f), selectedLabelColor = Color.White, labelColor = Color(0xFFB8C2D6)))
                        }
                    }
                }
                Text("On the charging screen: swipe fast to change animation · tap for a pulse · double-tap to change effect · drag to rotate · long-press or ⚙ to customise.",
                    color = Color(0xFF6F7B93), fontSize = 11.sp)
            }
        }
    }

    @Composable
    private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0x14FFFFFF), border = BorderStroke(1.dp, GlassBorder)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
        }
    }

    @Composable
    private fun PermCard(title: String, why: String, granted: Boolean, action: String, onClick: () -> Unit) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 15.sp)
                    Text(why, color = Color(0xFF8A96AD), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.width(10.dp))
                if (granted) Text("ON ✓", color = Color(0xFF00E676), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                else OutlinedButton(onClick = onClick, border = BorderStroke(1.dp, Neon)) { Text(action, color = Neon, fontSize = 12.sp) }
            }
        }
    }
}
