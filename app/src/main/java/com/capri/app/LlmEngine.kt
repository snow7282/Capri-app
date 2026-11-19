package com.capri.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ChatMsg(val role: String, val content: String)

/**
 * Capri's "brain" interface. Right now there is one implementation (a local
 * llama.cpp server). Later you can add another one that runs the model inside
 * the app itself, without touching anything else.
 */
interface LlmEngine {
    suspend fun ping(): Boolean
    suspend fun chat(messages: List<ChatMsg>, maxTokens: Int, temperature: Float): Result<String>
}

/**
 * Talks to llama.cpp's built-in server (OpenAI-compatible API), usually running
 * in Termux on this same phone at http://127.0.0.1:8080
 */
class LlamaServerEngine(private val baseUrl: () -> String) : LlmEngine {

    private fun base(): String = baseUrl().trim().trimEnd('/')

    override suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = URL(base() + "/health").openConnection() as HttpURLConnection
            c.connectTimeout = 1500
            c.readTimeout = 1500
            val ok = c.responseCode == 200
            c.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun chat(
        messages: List<ChatMsg>,
        maxTokens: Int,
        temperature: Float
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray()
            for (m in messages) {
                arr.put(JSONObject().put("role", m.role).put("content", m.content))
            }
            val body = JSONObject()
                .put("messages", arr)
                .put("max_tokens", maxTokens)
                .put("temperature", temperature.toDouble())
                .put("top_p", 0.9)
                .put("repeat_penalty", 1.1)
                .put("stream", false)
                .toString()

            val conn = URL(base() + "/v1/chat/completions").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 4000
                conn.readTimeout = 180_000 // a phone can be slow, give her time
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) throw IOException("Server answered $code: ${text.take(200)}")

                JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } finally {
                conn.disconnect()
            }
        }
    }
}
