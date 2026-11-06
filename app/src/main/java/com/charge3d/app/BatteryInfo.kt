package com.charge3d.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import kotlin.math.abs

/** One snapshot of the battery. */
data class Battery(
    val percent: Int = 0,
    val charging: Boolean = false,
    val full: Boolean = false,
    val plug: String = "",          // "AC", "USB", "Wireless" or ""
    val currentMa: Int = 0,         // charging current in mA, 0 if unknown
    val tempC: Float = 0f,
    val voltageV: Float = 0f,
    val minutesLeft: Int = -1,      // minutes until 100%, -1 if unknown
) {
    val speedLabel: String
        get() = when {
            !charging -> ""
            currentMa >= 3000 -> "Super fast"
            currentMa >= 1500 -> "Fast"
            currentMa > 0 -> "Normal"
            plug == "Wireless" -> "Wireless"
            else -> "Charging"
        }
}

/** Reads the battery and estimates time-to-full. Create one per screen/service. */
class BatteryReader(private val context: Context) {
    private val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    // Fallback estimate: remember when the percentage last ticked up.
    private var lastPct = -1
    private var lastChangeAt = 0L
    private var minutesPerPct = -1f

    fun read(intent: Intent? = null): Battery {
        val i = intent ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return Battery()
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) (level * 100f / scale).toInt().coerceIn(0, 100) else 0
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val full = status == BatteryManager.BATTERY_STATUS_FULL || (plugged != 0 && pct >= 100)
        val charging = plugged != 0 &&
            (status == BatteryManager.BATTERY_STATUS_CHARGING || full)
        val plug = when {
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless"
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            else -> ""
        }
        // CURRENT_NOW units differ by phone (µA on most, mA on some); normalise to mA.
        val raw = abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
        val ma = when {
            raw == 0 || raw == Int.MIN_VALUE -> 0
            raw > 20000 -> raw / 1000
            else -> raw
        }
        return Battery(
            percent = pct, charging = charging, full = full, plug = plug,
            currentMa = if (charging) ma else 0,
            tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            voltageV = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f,
            minutesLeft = if (charging && !full) estimate(pct) else if (full) 0 else -1,
        )
    }

    private fun estimate(pct: Int): Int {
        // 1) Android's own estimate (API 28+, supported by many phones).
        if (Build.VERSION.SDK_INT >= 28) {
            val ms = bm.computeChargeTimeRemaining()
            if (ms > 0) return ((ms + 59_999) / 60_000).toInt()
        }
        // 2) Measure how fast the percentage is rising, then extrapolate.
        val now = SystemClock.elapsedRealtime()
        if (lastPct < 0 || pct < lastPct) { lastPct = pct; lastChangeAt = now }
        else if (pct > lastPct) {
            val mins = (now - lastChangeAt) / 60_000f / (pct - lastPct)
            if (mins > 0.1f) minutesPerPct = if (minutesPerPct < 0) mins else minutesPerPct * 0.5f + mins * 0.5f
            lastPct = pct; lastChangeAt = now
        }
        return if (minutesPerPct > 0) (minutesPerPct * (100 - pct)).toInt().coerceAtLeast(1) else -1
    }
}

fun formatTimeLeft(minutes: Int): String = when {
    minutes < 0 -> "Calculating…"
    minutes == 0 -> "Fully charged"
    minutes < 60 -> "$minutes min left"
    else -> "${minutes / 60} h ${minutes % 60} min left"
}
