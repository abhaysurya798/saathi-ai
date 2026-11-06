# ⚡ Saathi AI

Ek personal Android AI assistant — **voice, chat, image input, TTS**, aur basic phone control.

Phase 1 MVP. APK GitHub Actions se build hota hai.

## Features (Phase 1)

- 💬 Chat UI (Hinglish/Hindi/English)
- 🎤 Voice input (Android SpeechRecognizer)
- 🔊 Voice reply (Android TTS)
- 🖼 Image input (gallery) — vision-capable model ke saath
- 📱 Phone actions:
  - App kholna: "YouTube kholo"
  - Dialer: "Call 98765..."
  - SMS composer
  - Alarm set karna
  - Torch on/off
  - WiFi/Bluetooth/Settings kholna
  - Volume, battery, time
  - URL browser mein kholna
- ⚙️ Settings: base URL, API key, model, TTS on/off
- 🔒 API key sirf phone ke SharedPreferences mein rehti hai

## Default AI backend

App ka default endpoint aapke phone ke andar chal raha **OmniRoute** hai:

| Setting | Default |
|---------|---------|
| Base URL | `http://127.0.0.1:20128/api/v1` |
| API Key | `sk_omniroute` |
| Model | `gemini/gemini-3-flash-preview` |

Agar OmniRoute band ho, Settings mein koi bhi OpenAI-compatible URL/key/model daal sakte ho
(OpenAI, OpenRouter, Gemini, Groq, local Ollama etc.).

## Build

Push to `main` par GitHub Actions APK build karta hai:

```text
Actions → Build Saathi AI APK → Artifacts → saathi-ai-debug
```

Local build (agar Android SDK + JDK 17 ho):

```bash
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Install

1. APK download karo.
2. "Install unknown apps" allow karo.
3. Install karke kholo.
4. Mic permission do.
5. Settings mein endpoint check karo.
6. "YouTube kholo" ya "Torch on" try karo.

## Permissions

| Permission | Kyun |
|---|---|
| INTERNET | AI API calls |
| RECORD_AUDIO | Voice input |
| CAMERA | Torch only (runtime par maangi jaati hai) |
| QUERY_ALL_PACKAGES | Installed apps ko naam se kholna |
| VIBRATE | Feedback (future) |

## Phase roadmap

- **Phase 2:** Accessibility Service — apps ke andar tap, type, scroll, screen read
- **Phase 3:** Video input, autonomous multi-step tasks, daily routines
- **Phase 4:** Floating bubble, wake-word, notification listening, app shortcuts

## Known limits

- Phase 1 mein app ke andar automate karna nahi hai (sirf app kholna).
- SMS send karne ke liye compose screen kholti hai; automatic send nahi karti.
- Voice recognition phone ke Google Speech service par depend karta hai.
- `QUERY_ALL_PACKAGES` sideload ke liye fine hai; Play Store policy ke liye alag approach chahiye.

## Tech

Kotlin · Android SDK 34 · minSdk 26 · OkHttp · RecyclerView · SQLite-free (SharedPreferences)
