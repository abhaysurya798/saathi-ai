package com.abhaysurya.saathi

import android.content.Context

object Prefs {
    private const val NAME = "saathi_prefs"
    const val KEY_BASE = "base_url"
    const val KEY_API = "api_key"
    const val KEY_MODEL = "model"
    const val KEY_TTS = "tts_enabled"

    const val DEFAULT_BASE = "http://127.0.0.1:20128/api/v1"
    const val DEFAULT_API_KEY = "sk_omniroute"
    const val DEFAULT_MODEL = "gemini/gemini-3-flash-preview"

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun baseUrl(context: Context): String =
        prefs(context).getString(KEY_BASE, DEFAULT_BASE)?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE

    fun apiKey(context: Context): String =
        prefs(context).getString(KEY_API, DEFAULT_API_KEY) ?: DEFAULT_API_KEY

    fun model(context: Context): String =
        prefs(context).getString(KEY_MODEL, DEFAULT_MODEL)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL

    fun ttsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TTS, true)

    fun save(context: Context, base: String, apiKey: String, model: String, tts: Boolean) {
        prefs(context).edit()
            .putString(KEY_BASE, base)
            .putString(KEY_API, apiKey)
            .putString(KEY_MODEL, model)
            .putBoolean(KEY_TTS, tts)
            .apply()
    }
}
