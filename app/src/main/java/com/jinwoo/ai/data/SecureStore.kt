package com.jinwoo.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API keys are encrypted with an AES-256-GCM key that lives inside the Android Keystore
 * (hardware-backed where the phone supports it). Only the ciphertext is written to disk.
 *
 * Honest limit: this protects the key at rest. It does not stop someone who has root access to a
 * running phone, or malware with accessibility control, from using it. Use keys with spending limits.
 */
object SecureStore {
    private const val ALIAS = "jinwoo_api_key_v1"
    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LEN = 12

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER)
        ks.load(null)
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? {
        return try {
            val all = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = all.copyOfRange(0, IV_LEN)
            val ct = all.copyOfRange(IV_LEN, all.size)
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(c.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}

/** Stores one encrypted API key per provider. Keys are never hard-coded and never logged. */
class KeyVault(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("jinwoo_vault", Context.MODE_PRIVATE)

    fun get(providerId: String): String =
        sp.getString("key_$providerId", null)?.let { SecureStore.decrypt(it) } ?: ""

    fun has(providerId: String): Boolean = get(providerId).isNotBlank()

    fun set(providerId: String, apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) sp.edit().remove("key_$providerId").apply()
        else sp.edit().putString("key_$providerId", SecureStore.encrypt(trimmed)).apply()
    }

    fun clearAll() = sp.edit().clear().apply()

    /** Shows only the last 4 characters, e.g. "••••a1b2". */
    fun masked(providerId: String): String {
        val k = get(providerId)
        if (k.isBlank()) return "no key saved"
        return "••••" + k.takeLast(4)
    }
}
