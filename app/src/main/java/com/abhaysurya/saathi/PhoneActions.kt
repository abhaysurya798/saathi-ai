package com.abhaysurya.saathi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhoneActions {

    fun localCommand(context: Context, raw: String): String? {
        val text = raw.trim()
        val lower = text.lowercase(Locale.getDefault())
        return try {
            when {
                lower.contains("torch on") ||
                    lower.contains("flashlight on") ||
                    lower.contains("flash chalu") -> setTorch(context, true)

                lower.contains("torch off") ||
                    lower.contains("flashlight off") ||
                    lower.contains("flash band") -> setTorch(context, false)

                lower.contains("battery") || lower.contains("batteri") -> battery(context)
                lower == "time" || lower.contains("kitne baje") ||
                    lower.contains("samay kya") || lower.contains("time kya") -> time()

                lower.startsWith("open ") -> openApp(context, text.substring(5).trim())
                lower.startsWith("kholo ") -> openApp(context, text.substring(6).trim())
                lower.endsWith(" kholo") -> openApp(context, text.dropLast(6).trim())
                lower.endsWith(" khol do") -> openApp(context, text.dropLast(7).trim())
                lower.startsWith("call ") -> dial(context, text.substring(5).trim())
                lower.startsWith("alarm ") -> setAlarm(context, text.substring(6).trim())
                else -> null
            }
        } catch (e: Exception) {
            "❌ ${e.message}"
        }
    }

    fun execute(context: Context, action: String, args: JSONObject): String {
        return try {
            when (action) {
                "open_app" -> openApp(context, args.optString("name"))
                "call" -> dial(context, args.optString("number"))
                "sms" -> sms(context, args.optString("number"), args.optString("message"))
                "set_alarm" -> setAlarm(context, args.optString("time"))
                "torch_on" -> setTorch(context, true)
                "torch_off" -> setTorch(context, false)
                "open_settings" -> openSettings(context, args.optString("page"))
                "set_volume" -> setVolume(context, args.optInt("level", 50))
                "battery" -> battery(context)
                "time" -> time()
                "open_url" -> openUrl(context, args.optString("url"))
                else -> "❌ Action nahi pata: $action"
            }
        } catch (e: Exception) {
            "❌ ${e.message}"
        }
    }

    private fun openApp(context: Context, name: String): String {
        if (name.isBlank()) return "❌ App ka naam nahi mila"
        val query = name.trim()
        val lower = query.lowercase(Locale.getDefault())

        if (lower == "settings" || lower == "setting") return openSettings(context, "settings")
        if (lower == "phone" || lower == "dialer") return dial(context, "")

        val known = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "telegram" to "org.telegram.messenger",
            "spotify" to "com.spotify.music",
            "facebook" to "com.facebook.katana",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "camera" to "com.android.camera",
            "play store" to "com.android.vending",
            "netflix" to "com.netflix.mediaclient",
            "snapchat" to "com.snapchat.android",
            "twitter" to "com.twitter.android",
        )

        val knownPackage = known.entries.firstOrNull { lower.contains(it.key) }?.value
        if (knownPackage != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(knownPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return "✅ $query khol raha hoon"
            }
        }

        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val exact = apps.firstOrNull { app ->
            pm.getApplicationLabel(app).toString().equals(query, ignoreCase = true)
        }
        val match = exact ?: apps.firstOrNull { app ->
            pm.getApplicationLabel(app).toString().contains(query, ignoreCase = true)
        }

        if (match != null) {
            val intent = pm.getLaunchIntentForPackage(match.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return "✅ ${pm.getApplicationLabel(match)} khol raha hoon"
            }
        }

        return "❌ \"$query\" naam ka app nahi mila"
    }

    private fun dial(context: Context, number: String): String {
        val clean = number.replace(Regex("[^0-9+]"), "")
        val intent = if (clean.isBlank()) {
            Intent(Intent.ACTION_DIAL)
        } else {
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return if (clean.isBlank()) {
            "📞 Dialer khol raha hoon"
        } else {
            "📞 Dialer mein $clean daal raha hoon"
        }
    }

    private fun sms(context: Context, number: String, message: String): String {
        val clean = number.replace(Regex("[^0-9+]"), "")
        if (clean.isBlank()) return "❌ SMS ke liye number chahiye"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$clean")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "✉️ SMS app khol raha hoon — message bhejne ke liye Send dabao"
    }

    private fun setAlarm(context: Context, time: String): String {
        val match = Regex("(\\d{1,2})[:. ](\\d{2})").find(time)
            ?: return "❌ Time samajh nahi aaya. Example: 7:30"
        val hour = match.groupValues[1].toIntOrNull() ?: 7
        val minute = match.groupValues[2].toIntOrNull() ?: 0

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "⏰ Alarm set kar raha hoon: ${String.format(Locale.US, "%02d:%02d", hour, minute)}"
    }

    private fun setTorch(context: Context, on: Boolean): String {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "❌ Is phone mein flash nahi mila"

        manager.setTorchMode(cameraId, on)
        return if (on) "🔦 Torch on" else "🔦 Torch off"
    }

    private fun openSettings(context: Context, page: String): String {
        val intent = when (page.lowercase(Locale.getDefault())) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "⚙️ Settings khol raha hoon"
    }

    private fun setVolume(context: Context, level: Int): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percent = level.coerceIn(0, 100)
        val target = (max * percent / 100.0).toInt().coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return "🔊 Volume $percent%"
    }

    private fun battery(context: Context): String {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level in 0..100) "🔋 Battery: $level%" else "🔋 Battery status available nahi"
    }

    private fun time(): String {
        val formatter = SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", Locale.getDefault())
        return "🕒 ${formatter.format(Date())}"
    }

    private fun openUrl(context: Context, url: String): String {
        if (url.isBlank()) return "❌ URL khaali hai"
        val full = if (url.startsWith("http")) url else "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(full))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "🌐 Browser mein khol raha hoon"
    }
}
