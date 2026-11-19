package com.capri.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Capri's "presence". A foreground service (with a visible notification you can
 * stop any time) that listens for a few phone events and makes her pop up.
 *
 * It only listens for: charger plugged in, battery low, headphones plugged in,
 * and phone unlocked. It does NOT read your screen, messages, apps, location,
 * microphone or camera.
 */
class CapriService : Service() {

    companion object {
        const val ACTION_STOP = "com.capri.app.STOP"
        const val ACTION_REFRESH = "com.capri.app.REFRESH"
        const val ACTION_TEST = "com.capri.app.TEST"

        @Volatile
        var running = false

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, CapriService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, CapriService::class.java))
        }

        /** Re-read settings (bubble on/off, check-in schedule) without restarting. */
        fun refresh(ctx: Context) {
            if (!running) return
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, CapriService::class.java).setAction(ACTION_REFRESH)
            )
        }

        /** Makes her pop up right now (for testing). */
        fun test(ctx: Context) {
            if (!running) return
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, CapriService::class.java).setAction(ACTION_TEST)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var overlay: OverlayController
    private var speaker: Speaker? = null
    private var checkInJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        overlay = OverlayController(this)
        speaker = Speaker(this)
        startAsForeground()
        if (Prefs.showBubble) overlay.showBubble()
        registerEventReceiver()
        scheduleCheckIns()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Prefs.enabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                startAsForeground()
                overlay.hideAll()
                if (Prefs.showBubble) overlay.showBubble()
                scheduleCheckIns()
            }
            ACTION_TEST -> {
                startAsForeground()
                fire(Event.RANDOM, force = true)
            }
            else -> startAsForeground()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        overlay.hideAll()
        speaker?.shutdown()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val n = Notifier.presence(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                Notifier.ID_PRESENCE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Notifier.ID_PRESENCE, n)
        }
    }

    // ---- listening for phone events ----

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_POWER_CONNECTED -> fire(Event.CHARGER)
                Intent.ACTION_BATTERY_LOW -> fire(Event.BATTERY_LOW, batteryPercent())
                Intent.ACTION_USER_PRESENT -> onUnlock()
                Intent.ACTION_HEADSET_PLUG ->
                    if (i.getIntExtra("state", 0) == 1) fire(Event.HEADPHONES)
            }
        }
    }

    private fun registerEventReceiver() {
        val f = IntentFilter()
        f.addAction(Intent.ACTION_POWER_CONNECTED)
        f.addAction(Intent.ACTION_BATTERY_LOW)
        f.addAction(Intent.ACTION_USER_PRESENT)
        f.addAction(Intent.ACTION_HEADSET_PLUG)
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun batteryPercent(): String {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return "${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
    }

    private fun onUnlock() {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour in 0..4) {
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
            fire(Event.LATE_NIGHT, time)
        } else if (hour in 5..11) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
            if (Prefs.lastMorningDay != today && fire(Event.MORNING)) {
                Prefs.lastMorningDay = today
            }
        }
    }

    // ---- random check-ins ----

    private fun scheduleCheckIns() {
        checkInJob?.cancel()
        checkInJob = scope.launch {
            while (isActive) {
                delay((120..300).random() * 60_000L) // every 2 to 5 hours
                if (Prefs.randomCheckIns) fire(Event.RANDOM)
            }
        }
    }

    // ---- making her pop up (with limits so she never spams) ----

    private fun inQuietHours(): Boolean {
        if (!Prefs.quietEnabled) return false
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val s = Prefs.quietStart
        val e = Prefs.quietEnd
        return when {
            s == e -> false
            s < e -> h in s until e
            else -> h >= s || h < e
        }
    }

    /** Returns true if she actually went off to say something. */
    private fun fire(event: Event, detail: String = "", force: Boolean = false): Boolean {
        if (!Prefs.enabled) return false
        val now = System.currentTimeMillis()
        if (!force) {
            if (inQuietHours()) return false
            if (now - Prefs.lastPopupAt < Prefs.minGapMinutes * 60_000L) return false
        }
        Prefs.lastPopupAt = now
        scope.launch {
            val line = Brain.react(event, detail)
            present(line)
        }
        return true
    }

    private fun present(line: String) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            // Screen is off: leave a notification instead of drawing over a dark screen.
            Notifier.says(this, line)
            return
        }
        if (overlay.canDraw()) {
            overlay.showCard(line)
        } else {
            Notifier.says(this, line)
        }
        if (Prefs.speak) speaker?.speak(line)
    }
}
