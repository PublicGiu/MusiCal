package com.example.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
        mediaSession = MediaSession.Builder(this, autoPlayForwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setBitmapLoader(bitmapLoader)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
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
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
