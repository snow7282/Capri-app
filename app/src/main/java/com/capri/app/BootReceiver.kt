package com.capri.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Optional: brings Capri back after a reboot, only if you switched that on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs.enabled && Prefs.startOnBoot) {
            CapriService.start(context)
        }
    }
}
