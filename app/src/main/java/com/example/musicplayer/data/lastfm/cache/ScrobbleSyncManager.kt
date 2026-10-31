package com.example.musicplayer.data.lastfm.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.lastfm.LastFmClient
import com.example.musicplayer.data.lastfm.LastFmSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Gestore della cache offline e sincronizzazione automatica degli scrobble pendenti.
 * Monitora la connettività di rete tramite [ConnectivityManager.NetworkCallback] e
 * invia gli scrobble in ordine cronologico non appena Internet torna disponibile.
 */
class ScrobbleSyncManager(
    private val context: Context,
    private val lastFmClient: LastFmClient,
    private val sessionManager: LastFmSessionManager
) {
    private val tag = "ScrobbleSyncManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val pendingDao = AppDatabase.getInstance(context).pendingScrobbleDao()

    val pendingCountFlow: Flow<Int> = pendingDao.getPendingCountFlow()

    var onSyncCompletedListener: ((syncedCount: Int) -> Unit)? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(tag, "Rete tornata disponibile, avvio sincronizzazione scrobble offline")
            scope.launch {
                syncPendingScrobbles()
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (hasInternet && isValidated) {
                scope.launch {
                    syncPendingScrobbles()
                }
            }
        }
    }

    private var isCallbackRegistered = false

    /**
     * Registra il listener di rete per rilevare il ripristino della connessione.
     */
    fun startMonitoring() {
        if (!isCallbackRegistered) {
            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
                isCallbackRegistered = true
                Log.d(tag, "NetworkCallback registrato con successo")
            } catch (e: Exception) {
                Log.e(tag, "Errore durante la registrazione di NetworkCallback", e)
            }
        }
    }

    /**
     * Rimuove il listener di rete.
     */
    fun stopMonitoring() {
        if (isCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
                isCallbackRegistered = false
                Log.d(tag, "NetworkCallback rimosso")
            } catch (e: Exception) {
                Log.e(tag, "Errore durante unregisterNetworkCallback", e)
            }
        }
    }

    /**
     * Verifica se la rete è attualmente connessa ad Internet.
     */
    fun isNetworkAvailable(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Salva uno scrobble non riuscito o offline nella cache Room locale.
     */
    suspend fun savePendingScrobble(track: AudioTrack, timestampSeconds: Long) =
        withContext(Dispatchers.IO) {
            try {
                val entity = PendingScrobbleEntity(
                    artist = track.artist.trim(),
                    track = track.title.trim(),
                    album = if (track.album.isNotBlank() && track.album != "Sconosciuto") track.album.trim() else "",
                    timestamp = timestampSeconds
                )
                pendingDao.insert(entity)
                Log.d(tag, "Scrobble salvato nella cache offline: ${track.title} (${entity.timestamp})")
            } catch (e: Exception) {
                Log.e(tag, "Errore nel salvataggio dello scrobble offline", e)
            }
        }

    /**
     * Sincronizza tutti gli scrobble salvati in locale verso Last.fm.
     * Gli scrobble vengono inviati rigorosamente nell'ordine cronologico corretto (ORDER BY timestamp ASC)
     * e rimossi dal database locale solo dopo la conferma dell'API.
     */
    suspend fun syncPendingScrobbles(): Int = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            Log.d(tag, "Sincronizzazione già in corso, ignoro richiesta duplicata")
            return@withContext 0
        }

        try {
            val sessionKey = sessionManager.getSessionKey()
            if (sessionKey.isNullOrBlank()) {
                Log.d(tag, "Nessuna sessione Last.fm attiva, scrobble offline conservati in attesa di login")
                return@withContext 0
            }

            if (!isNetworkAvailable()) {
                Log.d(tag, "Nessuna connessione attiva, impossibile sincronizzare ora")
                return@withContext 0
            }

            val pendingList = pendingDao.getAllPendingAsc()
            if (pendingList.isEmpty()) {
                return@withContext 0
            }

            Log.d(tag, "Trovati ${pendingList.size} scrobble offline da sincronizzare")
            var successfullySynced = 0
            val idsToDelete = mutableListOf<Long>()

            try {
                for (pending in pendingList) {
                    val result = lastFmClient.scrobble(
                        artist = pending.artist,
                        track = pending.track,
                        album = pending.album.ifBlank { null },
                        timestampSeconds = pending.timestamp,
                        sessionKey = sessionKey
                    )

                    if (result.isSuccess) {
                        idsToDelete.add(pending.id)
                        successfullySynced++
                        Log.d(tag, "Scrobble confermato: ${pending.track} [id=${pending.id}]")
                    } else {
                        val error = result.exceptionOrNull()
                        Log.w(tag, "Invio scrobble fallito per ${pending.track}: ${error?.message}")
                        if (error is IllegalArgumentException) {
                            // Dati non validi (irreversibile): scarta per non bloccare la coda
                            Log.e(tag, "Scrobble non valido scartato dalla cache per evitare blocchi permanenti: [id=${pending.id}]")
                            idsToDelete.add(pending.id)
                            continue
                        }
                        // Errore transitorio o di rete: interrompi per riprovare al prossimo evento di rete
                        break
                    }
                }
            } finally {
                if (idsToDelete.isNotEmpty()) {
                    pendingDao.deleteByIds(idsToDelete)
                    Log.d(tag, "Rimossi ${idsToDelete.size} scrobble dal database in batch")
                }
            }

            if (successfullySynced > 0) {
                withContext(Dispatchers.Main) {
                    onSyncCompletedListener?.invoke(successfullySynced)
                }
            }

            successfullySynced
        } finally {
            syncMutex.unlock()
        }
    }
}
