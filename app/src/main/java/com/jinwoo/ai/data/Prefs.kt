package com.jinwoo.ai.data

import android.content.Context
import android.content.SharedPreferences

/** Non-secret settings. API keys live in [KeyVault], never here. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("jinwoo_prefs", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun put(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    private fun put(k: String, v: Float) = sp.edit().putFloat(k, v).apply()
    private fun put(k: String, v: Int) = sp.edit().putInt(k, v).apply()

    // ---- AI provider ----
    var providerId: String
        get() = str("provider", "gemini")
        set(v) = put("provider", v)

    fun baseUrl(providerId: String, default: String): String =
        str("base_$providerId", "").ifBlank { default }

    fun setBaseUrl(providerId: String, v: String) = put("base_$providerId", v.trim())

    fun model(providerId: String, default: String): String =
        str("model_$providerId", "").ifBlank { default }

    fun setModel(providerId: String, v: String) = put("model_$providerId", v.trim())

    // ---- wake word ----
    var requireHey: Boolean
        get() = sp.getBoolean("require_hey", false)
        set(v) = put("require_hey", v)

    var preferOffline: Boolean
        get() = sp.getBoolean("prefer_offline", true)
        set(v) = put("prefer_offline", v)

    var followUp: Boolean
        get() = sp.getBoolean("follow_up", true)
        set(v) = put("follow_up", v)

    var language: String
        get() = str("language", "en-IN")
        set(v) = put("language", v)

    var extraWakePhrases: String
        get() = str("extra_wake", "")
        set(v) = put("extra_wake", v)

    // ---- voice ----
    var speakReplies: Boolean
        get() = sp.getBoolean("speak_replies", true)
        set(v) = put("speak_replies", v)

    var pitch: Float
        get() = sp.getFloat("pitch", 0.7f)
        set(v) = put("pitch", v)

    var rate: Float
        get() = sp.getFloat("rate", 0.92f)
        set(v) = put("rate", v)

    var voiceName: String
        get() = str("voice_name", "")
        set(v) = put("voice_name", v)

    // ---- theme ----
    var accent: Int
        get() = sp.getInt("accent", 0)
        set(v) = put("accent", v)

    var particles: Boolean
        get() = sp.getBoolean("particles", true)
        set(v) = put("particles", v)

    var particleCount: Int
        get() = sp.getInt("particle_count", 55)
        set(v) = put("particle_count", v)

    var cinematic: Boolean
        get() = sp.getBoolean("cinematic", true)
        set(v) = put("cinematic", v)

    // ---- activation sound ----
    /** 0 = built-in, 1 = my own audio file, 2 = silent */
    var soundMode: Int
        get() = sp.getInt("sound_mode", 0)
        set(v) = put("sound_mode", v)

    var customSoundUri: String
        get() = str("custom_sound", "")
        set(v) = put("custom_sound", v)

    var soundVolume: Float
        get() = sp.getFloat("sound_volume", 0.8f)
        set(v) = put("sound_volume", v)

    // ---- privacy ----
    var historyEnabled: Boolean
        get() = sp.getBoolean("history_enabled", true)
        set(v) = put("history_enabled", v)

    var sendContext: Boolean
        get() = sp.getBoolean("send_context", true)
        set(v) = put("send_context", v)

    var confirmSensitive: Boolean
        get() = sp.getBoolean("confirm_sensitive", true)
        set(v) = put("confirm_sensitive", v)

    var keyWarningAccepted: Boolean
        get() = sp.getBoolean("key_warning_accepted", false)
        set(v) = put("key_warning_accepted", v)

    var gmailConnected: Boolean
        get() = sp.getBoolean("gmail_connected", false)
        set(v) = put("gmail_connected", v)

    fun clearAll() = sp.edit().clear().apply()
}
