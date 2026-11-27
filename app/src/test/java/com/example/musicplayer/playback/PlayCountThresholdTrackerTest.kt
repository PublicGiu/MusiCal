package com.example.musicplayer.playback

import android.net.Uri
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.SortOption
import com.example.musicplayer.data.TrackStats
import com.example.musicplayer.data.sortAlbums
import com.example.musicplayer.data.sortPlaylists
import com.example.musicplayer.data.sortTracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class PlayCountThresholdTrackerTest {

    @Test
    fun `triggers valid play only after 50 percent of actual listened time`() {
        val tracker = PlayCountThresholdTracker()
        val durationMs = 100_000L // 100s -> soglia 50_000ms (50s)

        tracker.onTrackSessionStarted(trackId = 10L, initialPositionMs = 0L)

        // Ascolto fino a 49 secondi (in step da 1000ms) -> NON ancora valido
        for (sec in 1..49) {
            val triggered = tracker.onPlaybackTick(
                trackId = 10L,
                currentPositionMs = sec * 1_000L,
                durationMs = durationMs,
                isPlaying = true
            )
            assertFalse("Non deve scattare al secondo $sec", triggered)
        }
        assertFalse(tracker.hasRecordedValidPlay)

        // Raggiungimento del 50° secondo -> SCATTA esattamente una volta
        val triggeredAt50 = tracker.onPlaybackTick(
            trackId = 10L,
            currentPositionMs = 50_000L,
            durationMs = durationMs,
            isPlaying = true
        )
        assertTrue(triggeredAt50)
        assertTrue(tracker.hasRecordedValidPlay)

        // Ulteriori tick nella stessa riproduzione non devono duplicare il conteggio
        val triggeredAt51 = tracker.onPlaybackTick(
            trackId = 10L,
            currentPositionMs = 51_000L,
            durationMs = durationMs,
            isPlaying = true
        )
        assertFalse(triggeredAt51)
    }

    @Test
    fun `manual seek past 50 percent does not count until 50 percent time is actually listened`() {
        val tracker = PlayCountThresholdTracker()
        val durationMs = 200_000L // 200s -> soglia 100_000ms (100s)

        tracker.onTrackSessionStarted(trackId = 42L, initialPositionMs = 0L)

        // L'utente ascolta 5s e poi salta con la seekbar a 180s (90%)
        for (sec in 1..5) {
            assertFalse(tracker.onPlaybackTick(42L, sec * 1_000L, durationMs, isPlaying = true))
        }
        tracker.onManualSeek(180_000L)

        // Ascolta altri 10s fino a 190s (totale ascoltato = 15s < 100s) -> NON valido
        for (sec in 181..190) {
            assertFalse(tracker.onPlaybackTick(42L, sec * 1_000L, durationMs, isPlaying = true))
        }
        assertEquals(15_000L, tracker.accumulatedListenedMs)
        assertFalse(tracker.hasRecordedValidPlay)
    }

    @Test
    fun `repeat mode reset allows counting a second full listen of the same track`() {
        val tracker = PlayCountThresholdTracker()
        val durationMs = 20_000L // 20s -> soglia 10s

        tracker.onTrackSessionStarted(trackId = 7L, initialPositionMs = 0L)
        for (sec in 1..9) {
            assertFalse(tracker.onPlaybackTick(7L, sec * 1_000L, durationMs, isPlaying = true))
        }
        assertTrue(tracker.onPlaybackTick(7L, 10_000L, durationMs, isPlaying = true))

        // Riavvio traccia per Repeat One
        tracker.onTrackSessionStarted(trackId = 7L, initialPositionMs = 0L)
        assertFalse(tracker.hasRecordedValidPlay)
        for (sec in 1..9) {
            assertFalse(tracker.onPlaybackTick(7L, sec * 1_000L, durationMs, isPlaying = true))
        }
        assertTrue(tracker.onPlaybackTick(7L, 10_000L, durationMs, isPlaying = true))
    }

    @Test
    fun `recently played order for tracks albums and playlists updates only after valid play`() {
        val mockUri = Mockito.mock(Uri::class.java)
        val trackOld = AudioTrack(1L, "Old Top", "Artist A", 100_000L, mockUri, albumId = 10L, album = "Album A")
        val trackNow = AudioTrack(2L, "Now Playing", "Artist B", 100_000L, mockUri, albumId = 20L, album = "Album B")

        val albumA = Album(10L, "Album A", "Artist A", null, listOf(trackOld))
        val albumB = Album(20L, "Album B", "Artist B", null, listOf(trackNow))

        val playlistA = Playlist(id = "pl_a", name = "Playlist A", trackIds = listOf(1L))
        val playlistB = Playlist(id = "pl_b", name = "Playlist B", trackIds = listOf(2L))

        val initialStats = mapOf(
            1L to TrackStats(audioId = 1L, playCount = 3, lastPlayedTimestamp = 5_000L),
            2L to TrackStats(audioId = 2L, playCount = 1, lastPlayedTimestamp = 1_000L)
        )

        // Prima del 50%: le statistiche non vengono aggiornate, l'ordine resta invariato
        assertEquals(listOf(1L, 2L), listOf(trackOld, trackNow).sortTracks(SortOption.RECENTLY_PLAYED, initialStats).map { it.id })
        assertEquals(listOf(10L, 20L), listOf(albumA, albumB).sortAlbums(SortOption.RECENTLY_PLAYED, initialStats).map { it.id })
        assertEquals(listOf("pl_a", "pl_b"), listOf(playlistA, playlistB).sortPlaylists(SortOption.RECENTLY_PLAYED, initialStats).map { it.id })

        // Al raggiungimento del 50%: aggiornamento stats -> brano, album e playlist salgono in cima
        val updatedStats = initialStats + (2L to TrackStats(audioId = 2L, playCount = 2, lastPlayedTimestamp = 9_000L))
        assertEquals(listOf(2L, 1L), listOf(trackOld, trackNow).sortTracks(SortOption.RECENTLY_PLAYED, updatedStats).map { it.id })
        assertEquals(listOf(20L, 10L), listOf(albumA, albumB).sortAlbums(SortOption.RECENTLY_PLAYED, updatedStats).map { it.id })
        assertEquals(listOf("pl_b", "pl_a"), listOf(playlistA, playlistB).sortPlaylists(SortOption.RECENTLY_PLAYED, updatedStats).map { it.id })
    }
}
