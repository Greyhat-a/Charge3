package com.charge3d.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings

/**
 * Lives in the background (as a low-priority foreground service) and listens for the
 * charger being plugged in / unplugged. These broadcasts cannot be declared in the
 * manifest on modern Android, so a running service is the reliable way to get them.
 */
class ChargeService : Service() {

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> onPlugged()
                Intent.ACTION_POWER_DISCONNECTED -> onUnplugged()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        val n = Notification.Builder(this, CH_WATCH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(getString(R.string.notif_watch_title))
            .setContentText(getString(R.string.notif_watch_text))
            .setContentIntent(openApp())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_WATCH, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_WATCH, n)
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerReceiver, f)
        // Already on the charger when the service starts? Show the screen once.
        if (BatteryReader(this).read().charging) onPlugged()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(powerReceiver) }
        super.onDestroy()
    }

    private fun onPlugged() {
        if (!Store(this).autoPopup) return
        val show = Intent(this, ChargeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        // Route 1: with "display over other apps" granted Android lets us open directly,
        // even while another app is on screen.
        if (Settings.canDrawOverlays(this)) {
            runCatching { startActivity(show) }
        }
        // Route 2: a full-screen-intent notification. This wakes a sleeping screen and
        // opens over the lock screen; if the screen is on it appears as a tap-to-open banner.
        val pi = PendingIntent.getActivity(
            this, 1, show, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CH_POPUP)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(getString(R.string.notif_popup_title))
            .setContentText("Tap to open the charging screen")
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_POPUP, n)
    }

    private fun onUnplugged() {
        getSystemService(NotificationManager::class.java).cancel(ID_POPUP)
        sendBroadcast(Intent(ACTION_CLOSE).setPackage(packageName))
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_WATCH, getString(R.string.notif_channel_watch),
                NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_POPUP, getString(R.string.notif_channel_popup),
                NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) }
        )
    }

    companion object {
        const val ACTION_CLOSE = "com.charge3d.app.CLOSE_CHARGE_SCREEN"
        private const val CH_WATCH = "watch"
        private const val CH_POPUP = "popup"
        private const val ID_WATCH = 1
        private const val ID_POPUP = 2

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, ChargeService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ChargeService::class.java))
        }
    }
}
