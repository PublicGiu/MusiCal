package com.example.musicplayer.data.lastfm

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Gestore della sessione e credenziali locali per Last.fm.
 * Le credenziali sensibili (session key, API key e shared secret personalizzati)
 * vengono cifrate a riposo tramite chiave AES-256-GCM vincolata ad AndroidKeyStore
 * ed escluse dai backup tramite data_extraction_rules.xml / backup_rules.xml.
 */
class LastFmSessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("lastfm_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SESSION_KEY = "session_key"
        private const val KEY_USERNAME = "username"
        private const val KEY_AVATAR_PATH = "avatar_path"
        private const val KEY_AVATAR_HASH = "avatar_hash"
        private const val KEY_CUSTOM_API_KEY = "custom_api_key"
        private const val KEY_CUSTOM_SHARED_SECRET = "custom_shared_secret"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "musical_lastfm_credentials_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val ENCRYPTED_PREFIX = "enc:v1:"
    }

    init {
        LastFmConfig.setCustomCredentials(
            apiKey = getCustomApiKey(),
            sharedSecret = getCustomSharedSecret()
        )
    }

    fun saveCustomApiCredentials(apiKey: String, sharedSecret: String) {
        val cleanKey = apiKey.trim()
        val cleanSecret = sharedSecret.trim()
        val encryptedKey = encryptValue(cleanKey) ?: return
        val encryptedSecret = encryptValue(cleanSecret) ?: return
        prefs.edit()
            .putString(KEY_CUSTOM_API_KEY, encryptedKey)
            .putString(KEY_CUSTOM_SHARED_SECRET, encryptedSecret)
            .apply()
        LastFmConfig.setCustomCredentials(cleanKey, cleanSecret)
    }

    fun clearCustomApiCredentials() {
        prefs.edit()
            .remove(KEY_CUSTOM_API_KEY)
            .remove(KEY_CUSTOM_SHARED_SECRET)
            .apply()
        LastFmConfig.setCustomCredentials(null, null)
    }

    fun getCustomApiKey(): String? = readProtectedString(KEY_CUSTOM_API_KEY)

    fun getCustomSharedSecret(): String? = readProtectedString(KEY_CUSTOM_SHARED_SECRET)

    fun saveSession(username: String, sessionKey: String) {
        val encryptedSessionKey = encryptValue(sessionKey) ?: return
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_SESSION_KEY, encryptedSessionKey)
            .apply()
    }

    fun saveAvatarInfo(path: String?, hash: String?) {
        val editor = prefs.edit()
        if (path != null) editor.putString(KEY_AVATAR_PATH, path) else editor.remove(KEY_AVATAR_PATH)
        if (hash != null) editor.putString(KEY_AVATAR_HASH, hash) else editor.remove(KEY_AVATAR_HASH)
        editor.apply()
    }

    fun getAvatarPath(): String? = prefs.getString(KEY_AVATAR_PATH, null)

    fun getAvatarHash(): String? = prefs.getString(KEY_AVATAR_HASH, null)

    fun clearSession() {
        getAvatarPath()?.let { path ->
            try {
                File(path).delete()
            } catch (_: Exception) {}
        }
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_SESSION_KEY)
            .remove(KEY_AVATAR_PATH)
            .remove(KEY_AVATAR_HASH)
            .apply()
    }

    fun getSessionKey(): String? = readProtectedString(KEY_SESSION_KEY)

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun isLoggedIn(): Boolean {
        val sk = getSessionKey()
        return !sk.isNullOrBlank()
    }

    private fun readProtectedString(prefKey: String): String? {
        val rawValue = prefs.getString(prefKey, null) ?: return null
        if (rawValue.startsWith(ENCRYPTED_PREFIX)) {
            return decryptValue(rawValue.removePrefix(ENCRYPTED_PREFIX))
        }
        // Migrazione automatica in-place per eventuali valori legacy non cifrati
        val migrated = encryptValue(rawValue)
        if (migrated != null) {
            prefs.edit().putString(prefKey, migrated).apply()
            return rawValue
        }
        return null
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existingKey != null) {
            return existingKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptValue(plainText: String): String? {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
            ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun decryptValue(encodedPayload: String): String? {
        if (encodedPayload.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encodedPayload, Base64.NO_WRAP)
            if (combined.size < 2) return null
            val ivLength = combined[0].toInt() and 0xFF
            if (ivLength <= 0 || combined.size <= 1 + ivLength) return null
            val iv = combined.copyOfRange(1, 1 + ivLength)
            val cipherBytes = combined.copyOfRange(1 + ivLength, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
