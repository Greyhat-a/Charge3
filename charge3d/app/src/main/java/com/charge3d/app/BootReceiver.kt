package com.charge3d.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the charger watcher after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Store(context).autoPopup) {
            runCatching { ChargeService.start(context) }
        }
    }
}
