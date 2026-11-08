package com.example.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import com.example.musicplayer.MainActivity

/**
 * Servizio di riproduzione in background basato su AndroidX Media3 MediaSessionService
 * e gestito da un'istanza di ExoPlayer.
 *
 * Configurato per gestire sessioni audio, controlli da notifica di sistema e lock screen.
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Configurazione degli AudioAttributes per la gestione automatica dell'Audio Focus di sistema
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()

        // Creazione dell'istanza di ExoPlayer con gestione Audio Focus e disconnessione cuffie
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
            .build()
        player = exoPlayer

        // Inizializzazione equalizzatore ed effetti hardware collegati alla sessione audio
        EqualizerManager.init(this, exoPlayer.audioSessionId)
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                    EqualizerManager.init(this@PlaybackService, audioSessionId)
                }
            }
        })

        // PendingIntent per consentire all'utente di toccare la notifica e riaprire l'app
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ForwardingPlayer per garantire l'avvio immediato della riproduzione
        // al passaggio traccia successivo/precedente anche se il lettore era in pausa
        val autoPlayForwardingPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun seekToNextMediaItem() {
                super.seekToNextMediaItem()
                play()
            }

            override fun seekToPreviousMediaItem() {
                super.seekToPreviousMediaItem()
                play()
            }

            override fun seekToNext() {
                super.seekToNext()
                play()
            }

            override fun seekToPrevious() {
                super.seekToPrevious()
                play()
            }
        }

        // Configurazione del BitmapLoader per copertine su notifiche e lock screen
        val bitmapLoader = Media3BitmapLoader(this)

        // Configurazione della MediaSession associata al ForwardingPlayer
        // con verifica del chiamante e restrizione dei comandi concessi (CWE-284)
        mediaSession = MediaSession.Builder(this, autoPlayForwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setBitmapLoader(bitmapLoader)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    if (isSelfOrNotificationController(session, controller)) {
                        return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
                    }
                    if (isTrustedSystemController(session, controller)) {
                        val safePlayerCommands = Player.Commands.Builder()
                            .addAll(
                                Player.COMMAND_PLAY_PAUSE,
                                Player.COMMAND_SEEK_TO_NEXT,
                                Player.COMMAND_SEEK_TO_PREVIOUS,
                                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                                Player.COMMAND_STOP,
                                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                                Player.COMMAND_GET_METADATA,
                                Player.COMMAND_GET_TIMELINE,
                                Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                                Player.COMMAND_GET_VOLUME,
                                Player.COMMAND_GET_DEVICE_VOLUME
                            )
                            .build()
                        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailableSessionCommands(SessionCommands.EMPTY)
                            .setAvailablePlayerCommands(safePlayerCommands)
                            .build()
                    }
                    return MediaSession.ConnectionResult.reject()
                }
            })
            .build()
    }

    @OptIn(UnstableApi::class)
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val session = mediaSession ?: return null
        return if (
            isSelfOrNotificationController(session, controllerInfo) ||
            isTrustedSystemController(session, controllerInfo)
        ) {
            session
        } else {
            null
        }
    }

    @OptIn(UnstableApi::class)
    private fun isSelfOrNotificationController(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo
    ): Boolean {
        val isOwnApp = controllerInfo.uid == Process.myUid() &&
            controllerInfo.packageName == packageName
        return isOwnApp || session.isMediaNotificationController(controllerInfo)
    }

    @OptIn(UnstableApi::class)
    private fun isTrustedSystemController(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo
    ): Boolean {
        return controllerInfo.uid == Process.SYSTEM_UID ||
            controllerInfo.isTrusted ||
            session.isAutomotiveController(controllerInfo) ||
            session.isAutoCompanionController(controllerInfo)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Se la riproduzione è ferma quando l'app viene chiusa dai recenti, arrestiamo il servizio
        val activePlayer = player
        if (activePlayer == null || (!activePlayer.isPlaying && !activePlayer.playWhenReady) || activePlayer.playbackState == androidx.media3.common.Player.STATE_ENDED || activePlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        EqualizerManager.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
