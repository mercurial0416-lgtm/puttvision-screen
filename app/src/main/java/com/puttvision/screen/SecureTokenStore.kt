package com.puttvision.screen

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the GitHub credential encrypted by an Android Keystore-backed AES key. */
class SecureTokenStore(private val context: Context) {
    private val alias = "puttvision.github.deploy.v1"
    private val prefs = context.getSharedPreferences("puttvision_secure", Context.MODE_PRIVATE)

    fun hasToken(): Boolean = !prefs.getString("github_token", null).isNullOrBlank()

    fun saveToken(token: String) {
        val clean = token.trim()
        require(clean.length >= 20) { "토큰 형식이 너무 짧습니다." }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))

        prefs.edit()
            .putString("github_token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("github_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadToken(): String? {
        val encoded = prefs.getString("github_token", null) ?: return null
        val ivEncoded = prefs.getString("github_iv", null) ?: return null

        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = Base64.decode(ivEncoded, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP))
            plain.toString(Charsets.UTF_8)
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove("github_token").remove("github_iv").apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }
}
