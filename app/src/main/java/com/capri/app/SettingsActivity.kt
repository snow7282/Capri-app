package com.capri.app

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var permStatus: TextView
    private lateinit var fStart: EditText
    private lateinit var fEnd: EditText
    private lateinit var fGap: EditText
    private lateinit var fUrl: EditText
    private lateinit var fNotes: EditText

    private val accent = Color.parseColor("#FF2E88")

    private val setupText = """
1) Install Termux from F-Droid or its GitHub releases (not the old Play Store version).

2) In Termux, run these one at a time. The build takes a while, so keep the phone plugged in and the screen on:

termux-change-repo
pkg upgrade -y
pkg install -y clang cmake git libandroid-spawn
termux-setup-storage
cd ~ && git clone https://github.com/ggml-org/llama.cpp
cd llama.cpp
cmake -B build
cmake --build build --config Release -j 3 --target llama-server

3) Copy her model (the .gguf from Colab) and start the server:

cp ~/storage/downloads/*Q4_K_M*.gguf ~/capri.gguf
termux-wake-lock
~/llama.cpp/build/bin/llama-server -m ~/capri.gguf --host 127.0.0.1 --port 8080 -c 1024 -t 4

4) Leave Termux running in the background, come back here and tap "Test connection".
""".trim()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(40))
        scroll.addView(root)
        scroll.fitsSystemWindows = true
        setContentView(scroll)
        build()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ---- tiny UI helpers ----

    private fun header(t: String) {
        val tv = TextView(this)
        tv.text = t
        tv.setTextColor(accent)
        tv.textSize = 18f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setPadding(0, dp(24), 0, dp(6))
        root.addView(tv)
    }

    private fun body(t: String, selectable: Boolean = false): TextView {
        val tv = TextView(this)
        tv.text = t
        tv.setTextColor(0xFFB8B8CC.toInt())
        tv.textSize = 13.5f
        tv.setPadding(0, dp(2), 0, dp(6))
        if (selectable) tv.setTextIsSelectable(true)
        root.addView(tv)
        return tv
    }

    private fun button(t: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        root.addView(b)
    }

    private fun addSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val s = SwitchCompat(this)
        s.text = label
        s.isChecked = checked
        s.setTextColor(Color.WHITE)
        s.textSize = 15f
        s.setPadding(0, dp(8), 0, dp(8))
        s.setOnCheckedChangeListener { _, v -> onChange(v) }
        root.addView(s)
    }

    private fun field(label: String, value: String, type: Int, lines: Int = 1): EditText {
        val l = TextView(this)
        l.text = label
        l.setTextColor(0xFFB8B8CC.toInt())
        l.textSize = 13f
        l.setPadding(0, dp(8), 0, 0)
        root.addView(l)

        val e = EditText(this)
        e.setText(value)
        e.inputType = type
        e.setTextColor(Color.WHITE)
        if (lines > 1) {
            e.minLines = lines
            e.gravity = Gravity.TOP or Gravity.START
        }
        root.addView(e)
        return e
    }

    // ---- the screen ----

    private fun build() {
        val title = TextView(this)
        title.text = "Capri setup"
        title.setTextColor(Color.WHITE)
        title.textSize = 24f
        title.typeface = Typeface.DEFAULT_BOLD
        root.addView(title)
        body("Everything here stays on this phone. You decide what she is allowed to do.")

        // 1. Permissions
        header("1. Permissions")
        body("Capri can only do what you allow. Each button opens the normal Android permission screen.")
        permStatus = body("")
        button("Allow \"Display over other apps\" (floating bubble)") {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {
                openAppDetails()
            }
        }
        button("Allow notifications") {
            if (Build.VERSION.SDK_INT >= 33) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
                )
            } else {
                openAppDetails()
            }
        }
        button("Don't restrict battery for Capri") {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                openAppDetails()
            }
        }
        button("Xiaomi: Autostart + pop-up permissions") { openXiaomiPermissions() }
        body(
            "On Xiaomi/MIUI also turn on: Autostart, \"Display pop-up windows while running in the background\", " +
                "and lock Capri in the recent-apps screen (swipe-hold the card, tap the lock). Without these, Xiaomi will quietly kill her."
        )

        // 2. Presence
        header("2. Her presence")
        addSwitch("Capri is awake (bubble + pop-ups)", Prefs.enabled) { on ->
            if (on) {
                if (!Settings.canDrawOverlays(this)) {
                    toast("No overlay permission yet: she'll use notifications until you allow it.")
                }
                Prefs.enabled = true
                try {
                    CapriService.start(this)
                } catch (e: Exception) {
                    toast("Couldn't start: ${e.message}")
                }
            } else {
                Prefs.enabled = false
                CapriService.stop(this)
            }
        }
        addSwitch("Show the floating bubble", Prefs.showBubble) {
            Prefs.showBubble = it
            CapriService.refresh(this)
        }
        addSwitch("Speak her lines out loud", Prefs.speak) { Prefs.speak = it }
        addSwitch("Random check-ins (every 2 to 5 hours)", Prefs.randomCheckIns) {
            Prefs.randomCheckIns = it
            CapriService.refresh(this)
        }
        addSwitch("Quiet hours (no unprompted pop-ups)", Prefs.quietEnabled) { Prefs.quietEnabled = it }
        fStart = field("Quiet from hour (0 to 23)", Prefs.quietStart.toString(), InputType.TYPE_CLASS_NUMBER)
        fEnd = field("Quiet until hour (0 to 23)", Prefs.quietEnd.toString(), InputType.TYPE_CLASS_NUMBER)
        fGap = field(
            "Minimum minutes between pop-ups", Prefs.minGapMinutes.toString(), InputType.TYPE_CLASS_NUMBER
        )
        addSwitch("Start Capri after the phone reboots", Prefs.startOnBoot) { Prefs.startOnBoot = it }
        button("Make Capri pop up right now (test)") {
            if (CapriService.running) {
                CapriService.test(this)
            } else {
                toast("Switch \"Capri is awake\" on first.")
            }
        }
        body(
            "She reacts to: charger plugged in, battery low, headphones plugged in, phone unlocked (morning / late night), " +
                "and random check-ins. She does NOT read your screen, messages, apps, location, microphone or camera."
        )

        // 3. Brain
        header("3. Her brain (the model)")
        body(
            "Pop-ups work immediately using her saved lines. For real, unscripted replies, run her model on this phone. " +
                "Default address is fine if Termux runs on this same phone."
        )
        fUrl = field("Model server address", Prefs.serverUrl, InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT)
        button("Test connection") {
            saveFields()
            lifecycleScope.launch {
                val ok = Brain.engine.ping()
                toast(if (ok) "Brain online ✅" else "Can't reach the brain ❌ (is the server running?)")
            }
        }
        body("Setup steps (long-press to copy):")
        body(setupText, selectable = true)

        // 4. About you
        header("4. About you (optional)")
        body("A few short facts she can use in chat. Experimental: if she stops sounding like Capri, clear this box.")
        fNotes = field(
            "e.g. My name is Sam. I study commerce. I love cats.",
            Prefs.notes,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            3
        )

        button("Save settings") {
            saveFields()
            CapriService.refresh(this)
            toast("Saved")
        }
        button("Clear chat history") {
            Brain.clear()
            toast("Chat history cleared")
        }

        header("Privacy")
        body(
            "No accounts, no analytics, no cloud. The only network use is talking to your own model server. " +
                "Chat history is stored privately on this phone. Switch her off any time with the switch above or the Stop button in her notification."
        )
    }

    private fun saveFields() {
        Prefs.quietStart = fStart.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: Prefs.quietStart
        Prefs.quietEnd = fEnd.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: Prefs.quietEnd
        Prefs.minGapMinutes = fGap.text.toString().toIntOrNull()?.coerceIn(1, 1440) ?: Prefs.minGapMinutes
        Prefs.serverUrl = fUrl.text.toString().trim().ifEmpty { "http://127.0.0.1:8080" }
        Prefs.notes = fNotes.text.toString().trim()
    }

    private fun refreshStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val notif = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val battery = pm.isIgnoringBatteryOptimizations(packageName)
        permStatus.text =
            "Display over other apps: ${tick(overlay)}\n" +
                "Notifications: ${tick(notif)}\n" +
                "Battery unrestricted: ${tick(battery)}"
    }

    private fun tick(ok: Boolean): String = if (ok) "✅ allowed" else "❌ not yet"

    private fun openAppDetails() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
        }
    }

    private fun openXiaomiPermissions() {
        try {
            val i = Intent("miui.intent.action.APP_PERM_EDITOR")
            i.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            i.putExtra("extra_pkgname", packageName)
            startActivity(i)
        } catch (_: Exception) {
            openAppDetails()
        }
    }
}
