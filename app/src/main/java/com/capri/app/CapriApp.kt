package com.capri.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class CapriApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Brain.init(this)
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        val presence = NotificationChannel(
            Notifier.CH_PRESENCE, "Capri is awake", NotificationManager.IMPORTANCE_LOW
        )
        presence.description = "Shows that Capri is running in the background"
        nm.createNotificationChannel(presence)

        val says = NotificationChannel(
            Notifier.CH_SAYS, "Capri says", NotificationManager.IMPORTANCE_DEFAULT
        )
        says.description = "Things Capri says when she pops up"
        nm.createNotificationChannel(says)
    }
}
