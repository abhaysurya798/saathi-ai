package com.abhaysurya.saathi

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val base = findViewById<EditText>(R.id.setBase)
        val apiKey = findViewById<EditText>(R.id.setKey)
        val model = findViewById<EditText>(R.id.setModel)
        val tts = findViewById<CheckBox>(R.id.setTts)
        val testResult = findViewById<TextView>(R.id.testResult)

        base.setText(Prefs.baseUrl(this))
        apiKey.setText(Prefs.apiKey(this))
        model.setText(Prefs.model(this))
        tts.isChecked = Prefs.ttsEnabled(this)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            save(base, apiKey, model, tts)
            Toast.makeText(this, "Settings saved ✓", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            save(base, apiKey, model, tts)
            testResult.text = "Testing…"
            AiClient(this).chat(
                emptyList(),
                "Say OK in one word",
                null,
                object : AiClient.Listener {
                    override fun onSuccess(reply: String) {
                        runOnUiThread {
                            testResult.text = "✅ Connected: ${reply.take(120)}"
                        }
                    }

                    override fun onError(message: String) {
                        runOnUiThread {
                            testResult.text = "❌ $message"
                        }
                    }
                },
            )
        }
    }

    private fun save(
        base: EditText,
        apiKey: EditText,
        model: EditText,
        tts: CheckBox,
    ) {
        Prefs.save(
            this,
            base.text.toString().trim(),
            apiKey.text.toString().trim(),
            model.text.toString().trim(),
            tts.isChecked,
        )
    }
}
