package com.capri.app

import android.content.Context
import android.content.SharedPreferences

/** All user settings live here. Everything is stored privately on this phone only. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("capri_prefs", Context.MODE_PRIVATE)
    }

    /** Is Capri's background presence switched on? */
    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    var showBubble: Boolean
        get() = sp.getBoolean("showBubble", true)
        set(v) { sp.edit().putBoolean("showBubble", v).apply() }

    var speak: Boolean
        get() = sp.getBoolean("speak", false)
        set(v) { sp.edit().putBoolean("speak", v).apply() }

    var randomCheckIns: Boolean
        get() = sp.getBoolean("randomCheckIns", true)
        set(v) { sp.edit().putBoolean("randomCheckIns", v).apply() }

    var quietEnabled: Boolean
        get() = sp.getBoolean("quietEnabled", false)
        set(v) { sp.edit().putBoolean("quietEnabled", v).apply() }

    var quietStart: Int
        get() = sp.getInt("quietStart", 23)
        set(v) { sp.edit().putInt("quietStart", v).apply() }

    var quietEnd: Int
        get() = sp.getInt("quietEnd", 7)
        set(v) { sp.edit().putInt("quietEnd", v).apply() }

    /** Minimum minutes between unprompted pop-ups, so she never spams you. */
    var minGapMinutes: Int
        get() = sp.getInt("minGapMinutes", 10)
        set(v) { sp.edit().putInt("minGapMinutes", v).apply() }

    var startOnBoot: Boolean
        get() = sp.getBoolean("startOnBoot", false)
        set(v) { sp.edit().putBoolean("startOnBoot", v).apply() }

    var serverUrl: String
        get() = sp.getString("serverUrl", "http://127.0.0.1:8080") ?: "http://127.0.0.1:8080"
        set(v) { sp.edit().putString("serverUrl", v).apply() }

    /** Short facts you choose to tell her about yourself (optional). */
    var notes: String
        get() = sp.getString("notes", "") ?: ""
        set(v) { sp.edit().putString("notes", v).apply() }

    var lastMorningDay: String
        get() = sp.getString("lastMorningDay", "") ?: ""
        set(v) { sp.edit().putString("lastMorningDay", v).apply() }

    var lastPopupAt: Long
        get() = sp.getLong("lastPopupAt", 0L)
        set(v) { sp.edit().putLong("lastPopupAt", v).apply() }

    var bubbleX: Int
        get() = sp.getInt("bubbleX", 0)
        set(v) { sp.edit().putInt("bubbleX", v).apply() }

    var bubbleY: Int
        get() = sp.getInt("bubbleY", 400)
        set(v) { sp.edit().putInt("bubbleY", v).apply() }
}
