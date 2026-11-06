package com.abhaysurya.saathi

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiClient(context: Context) {

    interface Listener {
        fun onSuccess(reply: String)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun chat(
        history: List<ChatMessage>,
        userText: String,
        imageBase64: String?,
        listener: Listener,
    ) {
        val base = Prefs.baseUrl(appContext).trim().trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        history.takeLast(12).forEach { message ->
            if (message.role == "user" || message.role == "assistant") {
                messages.put(JSONObject().put("role", message.role).put("content", message.text))
            }
        }

        val userMessage = JSONObject().put("role", "user")
        if (imageBase64 != null) {
            val content = JSONArray()
            content.put(
                JSONObject()
                    .put("type", "text")
                    .put("text", userText.ifBlank { "Is image ko describe karo." }),
            )
            content.put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageBase64")),
            )
            userMessage.put("content", content)
        } else {
            userMessage.put("content", userText)
        }
        messages.put(userMessage)

        val body = JSONObject()
            .put("model", Prefs.model(appContext))
            .put("messages", messages)
            .put("temperature", 0.6)
            .put("max_tokens", 768)

        val request = try {
            val builder = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
            val apiKey = Prefs.apiKey(appContext)
            if (apiKey.isNotBlank()) {
                builder.addHeader("Authorization", "Bearer $apiKey")
            }
            builder.build()
        } catch (e: Exception) {
            listener.onError("Request error: ${e.message}")
            return
        }

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                listener.onError("Network error: ${e.message ?: "connection failed"}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        val message = try {
                            JSONObject(raw).optJSONObject("error")?.optString("message") ?: raw
                        } catch (e: Exception) {
                            raw
                        }
                        listener.onError("HTTP ${it.code}: ${message.take(400)}")
                        return
                    }

                    try {
                        val json = JSONObject(raw)
                        val choices = json.optJSONArray("choices")
                        if (choices == null || choices.length() == 0) {
                            listener.onError("AI response khaali hai")
                            return
                        }

                        val messageObj = choices.getJSONObject(0).optJSONObject("message")
                        val contentValue = messageObj?.opt("content")
                        val content = when (contentValue) {
                            is String -> contentValue
                            is JSONArray -> (0 until contentValue.length()).joinToString("") { index ->
                                contentValue.optJSONObject(index)?.optString("text").orEmpty()
                            }
                            else -> ""
                        }

                        if (content.isBlank()) {
                            listener.onError("AI ne koi text nahi bheja")
                        } else {
                            listener.onSuccess(content.trim())
                        }
                    } catch (e: Exception) {
                        listener.onError("Response parse error: ${e.message}")
                    }
                }
            }
        })
    }

    companion object {
        private val SYSTEM_PROMPT = """
            You are Saathi, a friendly AI assistant living on the user's Android phone.
            Always reply in the user's language (Hindi, Hinglish or English).
            Keep answers short and useful.

            You can control the phone by returning ONLY one JSON object,
            with no markdown and no extra text:
            {"action":"ACTION","args":{...}}

            Available actions:
            - open_app: {"name":"YouTube"}
            - call: {"number":"9876543210"}
            - sms: {"number":"9876543210","message":"Hello"}
            - set_alarm: {"time":"07:30"}
            - torch_on: {}
            - torch_off: {}
            - open_settings: {"page":"wifi"} (page: wifi, bluetooth, settings)
            - set_volume: {"level":50}
            - battery: {}
            - time: {}
            - open_url: {"url":"https://example.com"}

            If the user asks for a phone action, return ONLY the JSON object.
            For normal questions, answer directly in plain text.
            Never invent action results.
            When the user sends an image, describe or answer based on the image.
        """.trimIndent()
    }
}
