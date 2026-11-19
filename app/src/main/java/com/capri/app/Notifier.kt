package com.capri.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    const val CH_PRESENCE = "presence"
    const val CH_SAYS = "says"
    const val ID_PRESENCE = 1
    const val ID_SAYS = 2

    fun openChatIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** The always-visible notification that Android requires while Capri runs in the background. */
    fun presence(ctx: Context): Notification {
        val stop = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, CapriService::class.java).setAction(CapriService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CH_PRESENCE)
            .setSmallIcon(R.drawable.ic_stat_capri)
            .setContentTitle("Capri is awake")
            .setContentText("Tap to chat. Use Stop to switch her off.")
            .setContentIntent(openChatIntent(ctx))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop Capri", stop)
            .build()
    }

    /** A normal notification with something she said (used when the pop-up card can't be shown). */
    fun says(ctx: Context, text: String) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CH_SAYS)
            .setSmallIcon(R.drawable.ic_stat_capri)
            .setContentTitle("Capri")
            .setContentText(Fmt.stripActions(text).ifEmpty { text })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openChatIntent(ctx))
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(ID_SAYS, n)
        } catch (_: SecurityException) {
        }
    }
}
