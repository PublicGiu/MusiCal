package com.example.musicplayer.data.lastfm

import com.example.musicplayer.BuildConfig
import java.security.MessageDigest

/**
 * Configurazione e utility di firma per le API Last.fm.
 */
object LastFmConfig {
    val API_KEY: String = BuildConfig.LASTFM_API_KEY
    val SHARED_SECRET: String = BuildConfig.LASTFM_SHARED_SECRET
    const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"

    /**
     * Verifica se le credenziali API sono state configurate.
     */
    val isConfigured: Boolean
        get() = API_KEY.isNotBlank() && SHARED_SECRET.isNotBlank()

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
