package com.capri.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Chat memory + talking to the model.
 * Her memory is just the last chat lines, saved privately on this phone.
 */
object Brain {
    private const val MAX_STORED = 60   // lines kept on disk
    private const val MAX_CONTEXT = 12  // lines sent to the model each time

    private lateinit var appCtx: Context
    private val lock = Any()
    private val history = mutableListOf<ChatMsg>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val engine: LlmEngine = LlamaServerEngine { Prefs.serverUrl }

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        load()
    }

    fun snapshot(): List<ChatMsg> = synchronized(lock) { history.toList() }

    fun clear() {
        synchronized(lock) { history.clear() }
        persist()
    }

    /** A normal chat turn. Returns her reply, or a failure if the brain is unreachable. */
    suspend fun reply(userText: String): Result<String> {
        add("user", userText)
        val result = engine.chat(buildMessages(), 160, 0.7f).mapCatching { raw ->
            val t = tidy(raw)
            if (t.isBlank()) throw IOException("She sent back an empty reply")
            t
        }
        result.onSuccess { add("assistant", it) }
        result.onFailure { removeLastUser() }
        return result
    }

    /** A phone event (charger, unlock...). Never fails: uses a fallback line if the brain is offline. */
    suspend fun react(event: Event, detail: String = ""): String {
        val prompt = Persona.promptFor(event, detail)
        val result = engine.chat(listOf(ChatMsg("user", prompt)), 70, 0.8f).mapCatching { raw ->
            val t = tidy(raw)
            if (t.isBlank()) throw IOException("empty")
            t
        }
        val line = result.getOrElse { Persona.fallback(event) }
        Persona.userLine(event, detail)?.let { add("user", it) }
        add("assistant", line)
        return line
    }

    private fun buildMessages(): List<ChatMsg> {
        val recent = synchronized(lock) { history.takeLast(MAX_CONTEXT) }
        val notes = Prefs.notes.trim()
        return if (notes.isEmpty()) {
            recent
        } else {
            listOf(ChatMsg("system", "You are Capri. Things you know about the human you're talking to: $notes")) + recent
        }
    }

    /** Cleans up model output: removes stray tags and cuts a half-finished sentence. */
    fun tidy(raw: String): String {
        var t = raw.replace(Regex("<\\|[^>]*\\|>"), "").trim()
        // An odd number of asterisks means an *action* was cut off.
        if (t.count { it == '*' } % 2 == 1) {
            t = t.substring(0, t.lastIndexOf('*')).trim()
        }
        if (t.isNotEmpty() && t.last() !in ".!?*\"')…") {
            val cut = t.lastIndexOfAny(charArrayOf('.', '!', '?', '*'))
            if (cut > t.length / 3) t = t.substring(0, cut + 1)
        }
        return t.trim()
    }

    // ---- storage ----

    private fun file() = File(appCtx.filesDir, "history.json")

    private fun add(role: String, content: String) {
        synchronized(lock) {
            history.add(ChatMsg(role, content))
            while (history.size > MAX_STORED) history.removeAt(0)
        }
        persist()
    }

    private fun removeLastUser() {
        synchronized(lock) {
            if (history.isNotEmpty() && history[history.lastIndex].role == "user") {
                history.removeAt(history.lastIndex)
            }
        }
        persist()
    }

    private fun persist() {
        val copy = synchronized(lock) { history.toList() }
        ioScope.launch {
            try {
                val arr = JSONArray()
                for (m in copy) arr.put(JSONObject().put("role", m.role).put("content", m.content))
                file().writeText(arr.toString())
            } catch (_: Exception) {
            }
        }
    }

    private fun load() {
        try {
            val f = file()
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            synchronized(lock) {
                history.clear()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    history.add(ChatMsg(o.getString("role"), o.getString("content")))
                }
            }
        } catch (_: Exception) {
        }
    }
}
