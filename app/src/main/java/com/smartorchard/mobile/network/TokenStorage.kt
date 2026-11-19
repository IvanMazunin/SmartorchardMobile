package com.smartorchard.mobile.network

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

interface TokenStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun clear()
}

/**
 * Аппаратно-защищённое хранилище токенов на базе Android KeyStore (AES-256-GCM).
 * Шифротекст вместе с 12-байтным IV кодируется в Base64 и хранится в приватных SharedPreferences.
 */
@Singleton
class EncryptedTokenStorage @Inject constructor(
    @ApplicationContext context: Context
) : TokenStorage {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun getString(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            require(combined.size > 2)
            val ivLength = combined[0].toInt() and 0xFF
            require(ivLength in 12..16 && combined.size > 1 + ivLength)
            val iv = combined.copyOfRange(1, 1 + ivLength)
            val ciphertext = combined.copyOfRange(1 + ivLength, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrElse {
            prefs.edit { remove(key) }
            null
        }
    }

    @Synchronized
    override fun putString(key: String, value: String?) {
        if (value == null) {
            prefs.edit { remove(key) }
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(1 + iv.size + ciphertext.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(ciphertext, 0, combined, 1 + iv.size, ciphertext.size)

        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
        prefs.edit { putString(key, encoded) }
    }

    @Synchronized
    override fun clear() {
        prefs.edit { clear() }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "smartorchard_auth_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREFS_NAME = "smart_orchard_secure_auth"
    }
}
