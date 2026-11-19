package com.capri.app

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val items = mutableListOf<ChatMsg>()
    private lateinit var adapter: ChatAdapter
    private lateinit var list: RecyclerView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var thinking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        list = findViewById(R.id.list)
        input = findViewById(R.id.input)
        status = findViewById(R.id.status)

        adapter = ChatAdapter(items)
        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        list.layoutManager = lm
        list.adapter = adapter

        findViewById<TextView>(R.id.send).setOnClickListener { send() }
        findViewById<TextView>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        input.setOnEditorActionListener { _, _, _ ->
            send()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
        checkBrain()
        if (Prefs.enabled && !CapriService.running) {
            try {
                CapriService.start(this)
            } catch (_: Exception) {
            }
        }
    }

    private fun reload() {
        items.clear()
        items.addAll(Brain.snapshot())
        if (items.isEmpty()) {
            items.add(
                ChatMsg(
                    "assistant",
                    "*looks up* Oh. You found me. I'm Capri. Open Settings to wake me up properly, then say something."
                )
            )
        }
        adapter.notifyDataSetChanged()
        scrollEnd()
    }

    private fun scrollEnd() {
        if (items.isNotEmpty()) list.scrollToPosition(items.size - 1)
    }

    private fun checkBrain() {
        lifecycleScope.launch {
            val ok = Brain.engine.ping()
            if (!thinking) {
                status.text = if (ok) "● brain online" else "● brain offline (pop-ups use her saved lines)"
                status.setTextColor(if (ok) 0xFF4ADE80.toInt() else 0xFFFB7185.toInt())
            }
        }
    }

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || thinking) return
        input.setText("")
        items.add(ChatMsg("user", text))
        adapter.notifyItemInserted(items.size - 1)
        scrollEnd()

        thinking = true
        status.text = "Capri is thinking…"
        status.setTextColor(0xFF9A9AB0.toInt())

        lifecycleScope.launch {
            // NonCancellable: if you leave the screen, her answer is still saved.
            val result = withContext(NonCancellable) { Brain.reply(text) }
            thinking = false
            result.onSuccess { items.add(ChatMsg("assistant", it)) }
            result.onFailure {
                items.add(
                    ChatMsg(
                        "error",
                        "I can't reach my brain right now (${it.message}). Is the model server running? Settings has the steps."
                    )
                )
            }
            adapter.notifyItemInserted(items.size - 1)
            scrollEnd()
            checkBrain()
        }
    }
}
