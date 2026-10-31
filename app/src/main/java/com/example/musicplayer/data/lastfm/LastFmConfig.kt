package com.example.musicplayer.data.lastfm

import com.example.musicplayer.BuildConfig
import java.security.MessageDigest

/**
 * Configurazione e utility di firma per le API Last.fm.
 */
object LastFmConfig {
    @Volatile
    private var customApiKey: String? = null

    @Volatile
    private var customSharedSecret: String? = null

    val API_KEY: String
        get() = customApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.LASTFM_API_KEY

    val SHARED_SECRET: String
        get() = customSharedSecret?.takeIf { it.isNotBlank() } ?: BuildConfig.LASTFM_SHARED_SECRET

    const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"

    /**
     * Indica se l'APK contiene chiavi preconfigurate in fase di build (da local.properties).
     */
    val hasBuildConfigKeys: Boolean
        get() = BuildConfig.LASTFM_API_KEY.isNotBlank() && BuildConfig.LASTFM_SHARED_SECRET.isNotBlank()

    /**
     * Indica se l'utente ha configurato chiavi API personalizzate dall'interfaccia dell'app.
     */
    val hasCustomKeys: Boolean
        get() = !customApiKey.isNullOrBlank() && !customSharedSecret.isNullOrBlank()

    /**
     * Verifica se le credenziali API sono disponibili (da configurazione utente o da BuildConfig).
     */
    val isConfigured: Boolean
        get() = API_KEY.isNotBlank() && SHARED_SECRET.isNotBlank()

    /**
     * Imposta o rimuove le credenziali API personalizzate a runtime.
     */
    fun setCustomCredentials(apiKey: String?, sharedSecret: String?) {
        customApiKey = apiKey?.trim()?.takeIf { it.isNotBlank() }
        customSharedSecret = sharedSecret?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Calcola la firma api_sig richiesta da Last.fm:
     * 1. Esclude 'format' e 'callback'.
     * 2. Ordina alfabeticamente i parametri per chiave.
     * 3. Concatena nome e valore: k1v1k2v2... + SHARED_SECRET.
     * 4. Calcola l'hash MD5 esadecimale in minuscolo a 32 caratteri.
     */
    fun generateApiSig(params: Map<String, String>): String {
        val filteredAndSorted = params
            .filter { (key, _) -> key != "format" && key != "callback" && key != "api_sig" }
            .toSortedMap()

        val signatureBase = StringBuilder()
        for ((key, value) in filteredAndSorted) {
            signatureBase.append(key).append(value)
        }
        signatureBase.append(SHARED_SECRET)

        return md5(signatureBase.toString())
    }

    /**
     * Calcola l'hash MD5 di una stringa UTF-8 e lo restituisce in formato esadecimale minuscolo.
     */
    fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
