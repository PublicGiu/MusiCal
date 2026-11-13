package com.example.musicplayer.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Provider singleton di [OkHttpClient] condiviso tra tutti i client HTTP dell'app
 * per riutilizzare ConnectionPool, Dispatcher e connessioni TLS/HTTP2.
 */
object SharedHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Crea una variante che condivide Dispatcher e ConnectionPool di [instance]
     * con timeout personalizzati.
     */
    fun withTimeouts(
        connectSeconds: Long = 15,
        readSeconds: Long = 15,
        writeSeconds: Long = 15
    ): OkHttpClient {
        return instance.newBuilder()
            .connectTimeout(connectSeconds, TimeUnit.SECONDS)
            .readTimeout(readSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeSeconds, TimeUnit.SECONDS)
            .build()
    }
}
