package com.abhaysurya.saathi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var input: EditText
    private lateinit var statusRow: View
    private lateinit var statusText: TextView

    private var pendingImageBase64: String? = null
    private var pendingTorch: Pair<String, JSONObject>? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private lateinit var audioManager: AudioManager
    private var mediaVolBefore = -1
    private var mutedViaVolume = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startVoiceInput() else toast("Mic permission chahiye")
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingTorch
        pendingTorch = null
        if (granted && pending != null) {
            val result = PhoneActions.execute(this, pending.first, pending.second)
            addMessage(ChatMessage("action", result))
            speak(result)
        } else if (!granted) {
            toast("Torch ke liye camera permission chahiye")
        }
    }

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            showStatus("Image process ho rahi hai…")
            lifecycleScope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    ImageUtils.uriToBase64(this@MainActivity, uri)
                }
                hideStatus()
                if (encoded == null) {
                    toast("Image padh nahi paaya")
                } else {
                    pendingImageBase64 = encoded
                    input.hint = "Image attached ✓ — sawaal likho ya bhejo"
                    toast("Image attached ✓")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        recycler = findViewById(R.id.recyclerMessages)
        input = findViewById(R.id.inputText)
        statusRow = findViewById(R.id.statusRow)
        statusText = findViewById(R.id.statusText)

        adapter = ChatAdapter(messages)
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter

        findViewById<TextView>(R.id.btnSend).setOnClickListener { sendCurrentInput() }
        findViewById<TextView>(R.id.btnMic).setOnClickListener { toggleVoice() }
        findViewById<TextView>(R.id.btnImage).setOnClickListener {
            imagePicker.launch("image/*")
        }
        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.btnNewChat).setOnClickListener { newChat() }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentInput()
                true
            } else {
                false
            }
        }

        initTts()
        addMessage(ChatMessage("assistant", WELCOME))
    }

    private fun sendCurrentInput() {
        val text = input.text.toString().trim()
        val image = pendingImageBase64
        if (text.isEmpty() && image == null) return

        val prompt = if (text.isEmpty() && image != null) {
            "Is image ko describe karo."
        } else {
            text
        }

        input.setText("")
        input.hint = getString(R.string.hint_input)
        pendingImageBase64 = null
        addMessage(ChatMessage("user", if (image != null) "📷 $prompt" else prompt))

        if (image == null) {
            if (requestCameraIfTorch(prompt)) return

            val local = PhoneActions.localCommand(this, prompt)
            if (local != null) {
                addMessage(ChatMessage("action", local))
                speak(local)
                return
            }
        }

        showStatus("Saathi soch raha hai…")
        val history = messages.dropLast(1)
        AiClient(this).chat(history, prompt, image, object : AiClient.Listener {
            override fun onSuccess(reply: String) {
                runOnUiThread {
                    hideStatus()
                    handleAiReply(reply)
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    hideStatus()
                    val errorText = "⚠️ $message"
                    addMessage(ChatMessage("assistant", errorText))
                    speak(errorText)
                }
            }
        })
    }

    private fun handleAiReply(reply: String) {
        val action = parseAction(reply)
        if (action != null) {
            if (action.first == "torch_on" || action.first == "torch_off") {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingTorch = action
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    return
                }
            }

            val result = PhoneActions.execute(this, action.first, action.second)
            addMessage(ChatMessage("action", result))
            speak(result)
            return
        }

        addMessage(ChatMessage("assistant", reply))
        speak(reply)
    }

    private fun parseAction(text: String): Pair<String, JSONObject>? {
        var candidate = text.trim()
        if (candidate.contains("```")) {
            candidate = candidate.replace("```json", "").replace("```", "").trim()
        }

        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        return try {
            val json = JSONObject(candidate.substring(start, end + 1))
            val action = json.optString("action", "")
            if (action.isBlank()) {
                null
            } else {
                action to (json.optJSONObject("args") ?: JSONObject())
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun requestCameraIfTorch(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        val wantsTorch = lower.contains("torch") || lower.contains("flashlight")
        if (!wantsTorch) return false

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val action = if (lower.contains("off") || lower.contains("band")) {
            "torch_off"
        } else {
            "torch_on"
        }
        pendingTorch = action to JSONObject()
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        return true
    }

    private fun toggleVoice() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            hideStatus()
            // Google ka stop-ding thodi der baad bajta hai — uske baad volume wapas karo
            mainHandler.postDelayed({ restoreRecognitionSounds() }, 500)
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startVoiceInput()
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Speech recognition available nahi hai")
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        showStatus("Sun raha hoon…")
                    }

                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        isListening = false
                        hideStatus()
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        hideStatus()
                        restoreRecognitionSounds()
                        toast("Awaaz samajh nahi aayi (code $error)")
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        hideStatus()
                        restoreRecognitionSounds()
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            input.setText(text)
                            sendCurrentInput()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (!text.isNullOrBlank()) input.setText(text)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(intent)
        isListening = true
        showStatus("Sun raha hoon…")
        // Google recognizer ka start-ding dabao: media volume 0 (original yaad rakho)
        muteRecognitionSounds()
    }

    private fun muteRecognitionSounds() {
        if (mediaVolBefore != -1) return
        try {
            mediaVolBefore = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            mutedViaVolume = true
        } catch (e: Exception) {
            mediaVolBefore = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            try {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE,
                    0,
                )
                mutedViaVolume = false
            } catch (e2: Exception) {
                mediaVolBefore = -1
            }
        }
    }

    private fun restoreRecognitionSounds() {
        if (mediaVolBefore == -1) return
        val saved = mediaVolBefore
        val viaVolume = mutedViaVolume
        mediaVolBefore = -1
        try {
            if (viaVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
            } else {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_UNMUTE,
                    0,
                )
            }
        } catch (e: Exception) {
            // volume restore fail ho to bhi chup rehna theek hai
        }
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val hindi = Locale("hi", "IN")
                val availability = tts?.isLanguageAvailable(hindi)
                    ?: TextToSpeech.LANG_NOT_SUPPORTED
                tts?.language = if (availability >= TextToSpeech.LANG_AVAILABLE) {
                    hindi
                } else {
                    Locale.US
                }
                ttsReady = true
            }
        }
    }

    private fun speak(text: String) {
        if (!Prefs.ttsEnabled(this) || !ttsReady) return
        val clean = text
            .replace(Regex("[*_#`>]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isBlank()) return

        val hasHindi = clean.any { it.code in 0x0900..0x097F }
        tts?.language = if (hasHindi) Locale("hi", "IN") else Locale.US
        tts?.speak(clean.take(1200), TextToSpeech.QUEUE_FLUSH, null, "saathi")
    }

    private fun addMessage(message: ChatMessage) {
        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        recycler.scrollToPosition(messages.size - 1)
    }

    private fun newChat() {
        tts?.stop()
        messages.clear()
        adapter.notifyDataSetChanged()
        pendingImageBase64 = null
        input.hint = getString(R.string.hint_input)
        addMessage(ChatMessage("assistant", WELCOME))
    }

    private fun showStatus(text: String) {
        statusText.text = text
        statusRow.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        statusRow.visibility = View.GONE
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        restoreRecognitionSounds()
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    companion object {
        private const val WELCOME =
            "Namaste! Main Saathi hoon. Bol kar ya likh kar batao — main aapki madad karunga.\n\n" +
                "Try: \"YouTube kholo\", \"Torch on\", \"Battery status\", ya koi sawaal."
    }
}
