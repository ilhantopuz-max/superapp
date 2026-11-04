package com.cardmaster

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Crypto {
    private const val ALIAS = "superapp_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return g.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray())
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    fun decrypt(s: String): String = try {
        val parts = s.split(":")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
    } catch (e: Exception) {
        ""
    }

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    fun salt(): String {
        val b = ByteArray(8)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
}

/** Tüm uygulamanın ortak ayarları. API anahtarı Android Keystore ile şifrelenerek saklanır. */
object AppSettings {
    const val DEFAULT_MODEL = "gemini-3.5-flash"
    private var prefs: SharedPreferences? = null

    var apiKey by mutableStateOf("")
    var model by mutableStateOf(DEFAULT_MODEL)
    var theme by mutableStateOf("Sistem")
    var speak by mutableStateOf(true)
    var userName by mutableStateOf("")
    var pinHash by mutableStateOf("")
    var brief by mutableStateOf("")
    var briefAt by mutableStateOf(0L)

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("cm", 0)
        prefs = p
        val enc = p.getString("key_enc", "") ?: ""
        val plain = p.getString("key", "") ?: ""
        apiKey = if (enc.isNotEmpty()) Crypto.decrypt(enc) else plain
        if (enc.isEmpty() && plain.isNotEmpty()) setAi(plain, p.getString("model_v2", DEFAULT_MODEL) ?: DEFAULT_MODEL)
        model = p.getString("model_v2", DEFAULT_MODEL) ?: DEFAULT_MODEL
        theme = p.getString("theme", "Sistem") ?: "Sistem"
        speak = p.getBoolean("speak", true)
        userName = p.getString("user_name", "") ?: ""
        pinHash = p.getString("pin", "") ?: ""
        brief = p.getString("brief", "") ?: ""
        briefAt = p.getLong("brief_at", 0L)
    }

    fun setAi(k: String, m: String) {
        apiKey = k.trim()
        model = m.trim().ifEmpty { DEFAULT_MODEL }
        val e = prefs?.edit() ?: return
        if (apiKey.isEmpty()) e.remove("key_enc") else e.putString("key_enc", Crypto.encrypt(apiKey))
        e.remove("key").putString("model_v2", model).apply()
    }

    fun setUi(theme: String, speak: Boolean, name: String) {
        this.theme = theme; this.speak = speak; this.userName = name.trim()
        prefs?.edit()?.putString("theme", theme)?.putBoolean("speak", speak)
            ?.putString("user_name", this.userName)?.apply()
    }

    fun setPin(pin: String) {
        pinHash = if (pin.isEmpty()) "" else { val s = Crypto.salt(); s + ":" + Crypto.sha256(s + pin) }
        prefs?.edit()?.putString("pin", pinHash)?.apply()
    }

    fun checkPin(pin: String): Boolean {
        val p = pinHash.split(":")
        return p.size == 2 && Crypto.sha256(p[0] + pin) == p[1]
    }

    fun saveBrief(t: String) {
        brief = t; briefAt = System.currentTimeMillis()
        prefs?.edit()?.putString("brief", t)?.putLong("brief_at", briefAt)?.apply()
    }
}
