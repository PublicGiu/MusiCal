package com.example.musicplayer.data.lastfm

import android.content.Context
import android.util.Log
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.lastfm.cache.ScrobbleSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * Coordinatore dell'integrazione con Last.fm per MusiCal.
 * Incapsula sessione, autenticazione, invio Now Playing, tracking scrobble
 * (metà durata o 4 minuti) e sincronizzazione della cache offline Room.
 */
class LastFmCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val getCurrentTrack: () -> AudioTrack?,
    private val isPlayingProvider: () -> Boolean
) {
    private val appContext = context.applicationContext
    private val lastFmSessionManager = LastFmSessionManager(appContext)
    private val lastFmClient = LastFmClient()
    private val syncManager = ScrobbleSyncManager(appContext, lastFmClient, lastFmSessionManager)

    private val _isLastFmLoggedIn = MutableStateFlow(lastFmSessionManager.isLoggedIn())
    val isLastFmLoggedIn: StateFlow<Boolean> = _isLastFmLoggedIn.asStateFlow()

    private val _lastFmUsername = MutableStateFlow(lastFmSessionManager.getUsername())
    val lastFmUsername: StateFlow<String?> = _lastFmUsername.asStateFlow()

    private val _lastFmAvatarPath = MutableStateFlow(
        lastFmSessionManager.getAvatarPath()?.takeIf { File(it).exists() }
    )
    val lastFmAvatarPath: StateFlow<String?> = _lastFmAvatarPath.asStateFlow()

    private val _lastFmAuthLoading = MutableStateFlow(false)
    val lastFmAuthLoading: StateFlow<Boolean> = _lastFmAuthLoading.asStateFlow()

    private val _lastFmAuthError = MutableStateFlow<String?>(null)
    val lastFmAuthError: StateFlow<String?> = _lastFmAuthError.asStateFlow()

    private val _lastFmStatusMessage = MutableStateFlow<String?>(null)
    val lastFmStatusMessage: StateFlow<String?> = _lastFmStatusMessage.asStateFlow()

    private val _lastFmCustomApiKey = MutableStateFlow(lastFmSessionManager.getCustomApiKey() ?: "")
    val lastFmCustomApiKey: StateFlow<String> = _lastFmCustomApiKey.asStateFlow()

    private val _lastFmCustomSharedSecret = MutableStateFlow(lastFmSessionManager.getCustomSharedSecret() ?: "")
    val lastFmCustomSharedSecret: StateFlow<String> = _lastFmCustomSharedSecret.asStateFlow()

    val pendingScrobblesCount: StateFlow<Int> = syncManager.pendingCountFlow
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    // Tracciamento stato scrobble per il brano corrente
    private var currentTrackStartTimeSeconds: Long = 0L
    private var scrobbleSentForCurrentTrack: Boolean = false
    private var currentScrobbleTrackId: Long? = null

    init {
        syncManager.startMonitoring()
        syncManager.onSyncCompletedListener = { syncedCount ->
            Log.d("LastFm", "Sincronizzati $syncedCount scrobble dalla cache offline")
        }
        scope.launch(Dispatchers.IO) {
            delay(3000L) // Differito di 3 secondi dall'avvio per non intasare il cold start
            if (_isLastFmLoggedIn.value) {
                refreshUserAvatar()
            }
            syncManager.syncPendingScrobbles()
        }
    }

    fun refreshUserAvatar(usernameOverride: String? = null) {
        val username = (usernameOverride ?: _lastFmUsername.value ?: lastFmSessionManager.getUsername())?.trim()
        if (username.isNullOrBlank() || !LastFmConfig.isConfigured) return
        scope.launch(Dispatchers.IO) {
            val existingPath = lastFmSessionManager.getAvatarPath()
            val existingFileValid = existingPath != null && File(existingPath).exists()
            val knownHash = if (existingFileValid) lastFmSessionManager.getAvatarHash() else null

            val result = lastFmClient.fetchUserAvatar(username, knownHash)
            result.onSuccess { avatarResult ->
                when (avatarResult) {
                    is UserAvatarResult.NoAvatar -> {
                        existingPath?.let { runCatching { File(it).delete() } }
                        lastFmSessionManager.saveAvatarInfo(null, null)
                        _lastFmAvatarPath.value = null
                    }
                    is UserAvatarResult.Unchanged -> {
                        if (existingFileValid) {
                            _lastFmAvatarPath.value = existingPath
                        }
                    }
                    is UserAvatarResult.Downloaded -> {
                        val ext = if (avatarResult.isGif) "gif" else "png"
                        val avatarFile = File(appContext.filesDir, "lastfm_avatar_${avatarResult.hash}.$ext")
                        avatarFile.writeBytes(avatarResult.bytes)
                        appContext.filesDir.listFiles { file ->
                            file.name.startsWith("lastfm_avatar_") && file.absolutePath != avatarFile.absolutePath
                        }?.forEach { oldFile ->
                            runCatching { oldFile.delete() }
                        }
                        lastFmSessionManager.saveAvatarInfo(avatarFile.absolutePath, avatarResult.hash)
                        _lastFmAvatarPath.value = avatarFile.absolutePath
                    }
                }
            }.onFailure { e ->
                Log.d("LastFm", "Impossibile aggiornare avatar Last.fm: ${e.message}")
            }
        }
    }

    fun handleTrackStarted(track: AudioTrack) {
        currentScrobbleTrackId = track.id
        currentTrackStartTimeSeconds = System.currentTimeMillis() / 1000
        scrobbleSentForCurrentTrack = false

        val sessionKey = lastFmSessionManager.getSessionKey()
        if (!sessionKey.isNullOrBlank()) {
            scope.launch(Dispatchers.IO) {
                val result = lastFmClient.updateNowPlaying(track, sessionKey)
                result.onSuccess {
                    Log.d("LastFm", "Now Playing inviato con successo: ${track.title} - ${track.artist}")
                }.onFailure { e ->
                    Log.d("LastFm", "Errore invio Now Playing (${track.title}): ${e.message}")
                }
            }
        }
    }

    fun checkAndTriggerScrobble(positionMs: Long, durationMs: Long) {
        if (scrobbleSentForCurrentTrack) return
        val track = getCurrentTrack() ?: return
        val sessionKey = lastFmSessionManager.getSessionKey() ?: return

        val trackDurMs = if (durationMs > 0L) durationMs else track.duration
        if (trackDurMs < 30_000L) return

        val thresholdMs = minOf(trackDurMs / 2, 240_000L)
        if (positionMs >= thresholdMs) {
            scrobbleSentForCurrentTrack = true
            val startTime = currentTrackStartTimeSeconds
            scope.launch(Dispatchers.IO) {
                if (!syncManager.isNetworkAvailable()) {
                    syncManager.savePendingScrobble(track, startTime)
                    Log.d("LastFm", "Dispositivo offline: scrobble salvato in cache Room per ${track.title} - ${track.artist}")
                } else {
                    val result = lastFmClient.scrobble(track, startTime, sessionKey)
                    result.onSuccess {
                        Log.d("LastFm", "Ascolto registrato su Last.fm con successo: ${track.title} - ${track.artist}")
                    }.onFailure { e ->
                        syncManager.savePendingScrobble(track, startTime)
                        Log.d("LastFm", "Errore invio scrobble (${e.message}): salvato in cache offline Room per ${track.title}")
                    }
                }
            }
        }
    }

    fun resetCurrentTrackScrobble() {
        currentScrobbleTrackId = null
        scrobbleSentForCurrentTrack = false
    }

    fun isCurrentScrobbleTrack(trackId: Long): Boolean = currentScrobbleTrackId == trackId

    fun saveApiCredentials(apiKey: String, sharedSecret: String) {
        val cleanKey = apiKey.trim()
        val cleanSecret = sharedSecret.trim()
        if (cleanKey.isBlank() && cleanSecret.isBlank()) {
            lastFmSessionManager.clearCustomApiCredentials()
            _lastFmCustomApiKey.value = ""
            _lastFmCustomSharedSecret.value = ""
        } else {
            lastFmSessionManager.saveCustomApiCredentials(cleanKey, cleanSecret)
            _lastFmCustomApiKey.value = cleanKey
            _lastFmCustomSharedSecret.value = cleanSecret
            if (_isLastFmLoggedIn.value && LastFmConfig.isConfigured) {
                _lastFmStatusMessage.value = "Chiavi API salvate con successo"
                refreshUserAvatar()
                getCurrentTrack()?.let { current ->
                    if (isPlayingProvider()) {
                        handleTrackStarted(current)
                    }
                }
                syncOfflineScrobbles()
            }
        }
    }

    fun login(
        username: String,
        password: String,
        customApiKey: String? = null,
        customSharedSecret: String? = null
    ) {
        if (!customApiKey.isNullOrBlank() && !customSharedSecret.isNullOrBlank()) {
            saveApiCredentials(customApiKey, customSharedSecret)
        }
        if (!LastFmConfig.isConfigured) {
            _lastFmAuthError.value = "Inserisci API Key e Shared Secret di Last.fm per continuare."
            return
        }
        if (username.isBlank() || password.isBlank()) {
            _lastFmAuthError.value = "Inserisci username e password"
            return
        }
        scope.launch(Dispatchers.Main) {
            _lastFmAuthLoading.value = true
            _lastFmAuthError.value = null
            val result = lastFmClient.getMobileSession(username, password)
            result.onSuccess { (user, sessionKey) ->
                lastFmSessionManager.saveSession(user, sessionKey)
                _isLastFmLoggedIn.value = true
                _lastFmUsername.value = user
                _lastFmAuthLoading.value = false
                _lastFmStatusMessage.value = "Connesso a Last.fm come $user"
                refreshUserAvatar(user)

                getCurrentTrack()?.let { current ->
                    if (isPlayingProvider()) {
                        handleTrackStarted(current)
                    }
                }

                // Sincronizza eventuali scrobble offline accumulati in precedenza
                scope.launch(Dispatchers.IO) {
                    syncManager.syncPendingScrobbles()
                }
            }.onFailure { error ->
                _lastFmAuthLoading.value = false
                _lastFmAuthError.value = error.message ?: "Errore durante l'accesso a Last.fm"
            }
        }
    }

    fun logout() {
        lastFmSessionManager.clearSession()
        _isLastFmLoggedIn.value = false
        _lastFmUsername.value = null
        _lastFmAvatarPath.value = null
        _lastFmAuthError.value = null
        _lastFmStatusMessage.value = "Disconnesso da Last.fm"
    }

    fun syncOfflineScrobbles() {
        scope.launch(Dispatchers.IO) {
            if (!LastFmConfig.isConfigured) {
                _lastFmStatusMessage.value = "Chiavi API Last.fm mancanti: inseriscile qui sotto per sincronizzare"
                return@launch
            }
            val count = syncManager.syncPendingScrobbles()
            if (count > 0) {
                _lastFmStatusMessage.value = "Sincronizzati $count scrobble offline"
            } else if (!syncManager.isNetworkAvailable()) {
                _lastFmStatusMessage.value = "Connessione assente: scrobble mantenuti in coda"
            } else if (pendingScrobblesCount.value > 0) {
                _lastFmStatusMessage.value = "Sincronizzazione non riuscita per ${pendingScrobblesCount.value} scrobble in attesa"
            } else {
                _lastFmStatusMessage.value = "Nessuno scrobble in attesa"
            }
        }
    }

    fun clearStatusMessage() {
        _lastFmStatusMessage.value = null
    }

    fun stop() {
        syncManager.stopMonitoring()
    }
}
