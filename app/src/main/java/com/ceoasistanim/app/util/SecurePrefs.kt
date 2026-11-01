package com.ceoasistanim.app.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/** Hassas ayarlar (API anahtarı, PIN) Android Keystore ile şifreli saklanır. */
class SecurePrefs(ctx: Context) {
    private val sp = EncryptedSharedPreferences.create(
        ctx,
        "secure_prefs",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    var provider: String
        get() = str("provider", "gemini")
        set(v) = put("provider", v)
    var baseUrl: String
        get() = str("baseUrl", "https://generativelanguage.googleapis.com/v1beta")
        set(v) = put("baseUrl", v)
    var model: String
        get() = str("model", "gemini-3.8-flash")
        set(v) = put("model", v)
    var apiKey: String
        get() = str("apiKey", "")
        set(v) = put("apiKey", v)
    var speak: Boolean
        get() = sp.getBoolean("speak", true)
        set(v) = sp.edit().putBoolean("speak", v).apply()
    var biometric: Boolean
        get() = sp.getBoolean("biometric", false)
        set(v) = sp.edit().putBoolean("biometric", v).apply()
    var theme: Int // 0 sistem, 1 açık, 2 koyu
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()

    fun hasAi() = apiKey.isNotBlank()
    fun hasPin() = str("pinHash", "").isNotEmpty()
    fun setPin(pin: String) = put("pinHash", hash(pin))
    fun clearPin() { sp.edit().remove("pinHash").putBoolean("biometric", false).apply() }
    fun checkPin(pin: String) = hash(pin) == str("pinHash", "")

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(("ceo-salt-v1:$pin").toByteArray())
            .joinToString("") { "%02x".format(it) }
}
