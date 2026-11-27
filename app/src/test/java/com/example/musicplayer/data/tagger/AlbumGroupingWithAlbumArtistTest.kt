package com.example.musicplayer.data.tagger

import android.net.Uri
import com.example.musicplayer.data.Album
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.groupTracksIntoAlbums
import com.example.musicplayer.data.tagger.model.EnrichedMetadata
import com.example.musicplayer.data.tagger.repository.EnricherOptions
import com.example.musicplayer.ui.tagger.EnricherUiState
import com.example.musicplayer.ui.tagger.MetadataEnricherCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class AlbumGroupingWithAlbumArtistTest {

    @Test
    fun testAlbumTracksWithDifferentTrackArtistsUseAlbumArtist() {
        val mockUri = Mockito.mock(Uri::class.java)

        val track1 = AudioTrack(
            id = 1L,
            title = "Get Lucky",
            artist = "Daft Punk feat. Pharrell Williams",
            duration = 248000L,
            uri = mockUri,
            albumId = 100L,
            album = "Random Access Memories",
            albumArtist = "Daft Punk",
            trackNumber = 8,
            discNumber = 1
        )

        val track2 = AudioTrack(
            id = 2L,
            title = "Instant Crush",
            artist = "Daft Punk feat. Julian Casablancas",
            duration = 337000L,
            uri = mockUri,
            albumId = 100L,
            album = "Random Access Memories",
            albumArtist = "Daft Punk",
            trackNumber = 5,
            discNumber = 1
        )

        val albums = groupTracksIntoAlbums(listOf(track1, track2))
        assertEquals(1, albums.size)
        val album = albums.first()
        assertEquals("Random Access Memories", album.title)
        assertEquals("Daft Punk", album.artist)
        assertEquals(2, album.trackCount)
        // Verifichiamo che siano ordinati per numero di traccia
        assertEquals("Instant Crush", album.tracks[0].title)
        assertEquals("Get Lucky", album.tracks[1].title)
    }

    @Test
    fun testSingleTrackEditFromReviewAlbumPreservesParentAndReorders() {
        val mockUri = Mockito.mock(Uri::class.java)
        val track1 = AudioTrack(
            id = 10L,
            title = "Song A",
            artist = "Artist",
            duration = 180000L,
            uri = mockUri,
            albumId = 1L,
            album = "My Album",
            albumArtist = "Artist",
            trackNumber = 1,
            discNumber = 1
        )
        val track2 = AudioTrack(
            id = 20L,
            title = "Song B",
            artist = "Artist",
            duration = 200000L,
            uri = mockUri,
            albumId = 1L,
            album = "My Album",
            albumArtist = "Artist",
            trackNumber = 5,
            discNumber = 1
        )
        val album = Album(
            id = 1L,
            title = "My Album",
            artist = "Artist",
            albumArtUri = null,
            tracks = listOf(track1, track2)
        )
        val initialMap = mapOf(
            10L to EnrichedMetadata(
                title = "Song A",
                artist = "Artist",
                album = "My Album",
                albumArtist = "Artist",
                trackNumber = 2,
                discNumber = 1
            ),
            20L to EnrichedMetadata(
                title = "Song B",
                artist = "Artist",
                album = "My Album",
                albumArtist = "Artist",
                trackNumber = 5,
                discNumber = 1
            )
        )

        val reviewAlbumState = EnricherUiState.ReviewAlbum(
            album = album,
            tracksEnriched = initialMap,
            individualTracksEnriched = initialMap,
            unifiedTracksEnriched = initialMap,
            options = EnricherOptions(normalizeAlbum = true),
            isSeparationMode = false,
            editedAlbumTitle = "My Album"
        )

        // Simuliamo l'apertura della sottofinestra per track2 e la modifica del numero traccia a 1 e del titolo
        val reviewTrackState = EnricherUiState.ReviewTrack(
            track = track2,
            enriched = initialMap.getValue(20L),
            parentAlbumState = reviewAlbumState
        )
        assertNotNull(reviewTrackState.parentAlbumState)

        val editedMetaForTrack2 = initialMap.getValue(20L).copy(
            title = "Song B (Intro)",
            trackNumber = 1
        )
        val updatedTracksEnriched = reviewAlbumState.tracksEnriched + (20L to editedMetaForTrack2)

        val sortedDisplayTracks = album.tracks.sortedWith(
            compareBy<AudioTrack> { updatedTracksEnriched[it.id]?.discNumber ?: it.discNumber }
                .thenBy { updatedTracksEnriched[it.id]?.trackNumber ?: it.trackNumber }
                .thenBy { updatedTracksEnriched[it.id]?.title ?: it.title }
        )

        assertEquals(20L, sortedDisplayTracks[0].id)
        assertEquals("Song B (Intro)", updatedTracksEnriched[sortedDisplayTracks[0].id]?.title)
        assertEquals(10L, sortedDisplayTracks[1].id)
    }

    @Test
    fun testSingleTrackAlbumChangeMovesTrackOutInUnifyMode() {
        val mockUri = Mockito.mock(Uri::class.java)
        val track1 = AudioTrack(
            id = 10L,
            title = "Track 1",
            artist = "Artist",
            duration = 180000L,
            uri = mockUri,
            albumId = 1L,
            album = "Unified Album",
            albumArtist = "Artist",
            trackNumber = 1,
            discNumber = 1
        )
        val track2 = AudioTrack(
            id = 20L,
            title = "Intruso",
            artist = "Other Artist",
            duration = 210000L,
            uri = mockUri,
            albumId = 1L,
            album = "Unified Album",
            albumArtist = "Artist",
            trackNumber = 2,
            discNumber = 1
        )
        val album = Album(
            id = 1L,
            title = "Unified Album",
            artist = "Artist",
            albumArtUri = null,
            tracks = listOf(track1, track2)
        )
        val initialMap = mapOf(
            10L to EnrichedMetadata("Track 1", "Artist", "Unified Album", "Artist", trackNumber = 1, discNumber = 1),
            20L to EnrichedMetadata("Intruso", "Other Artist", "Unified Album", "Artist", trackNumber = 2, discNumber = 1)
        )

        val parent = EnricherUiState.ReviewAlbum(
            album = album,
            tracksEnriched = initialMap,
            unifiedTracksEnriched = initialMap,
            individualTracksEnriched = initialMap,
            isSeparationMode = false,
            editedAlbumTitle = "Unified Album"
        )

        // L'utente cambia l'album di track2 in "Another Album"
        val editedTrack2Meta = initialMap.getValue(20L).copy(
            album = "Another Album",
            albumArtist = "Other Artist"
        )

        val targetUnifiedAlbum = (parent.editedAlbumTitle ?: parent.album.title).trim()
        val isMoved = !parent.isSeparationMode &&
            !editedTrack2Meta.album.trim().equals(targetUnifiedAlbum, ignoreCase = true)

        assertTrue(isMoved)

        val remainingTracks = parent.album.tracks.filter { it.id != track2.id }
        val updatedState = parent.copy(
            album = parent.album.copy(tracks = remainingTracks),
            tracksEnriched = parent.tracksEnriched - track2.id,
            movedOutTracks = parent.movedOutTracks + (track2.id to (track2 to editedTrack2Meta)),
            manuallyEditedTrackIds = parent.manuallyEditedTrackIds + track2.id
        )

        assertEquals(1, updatedState.album.tracks.size)
        assertEquals(10L, updatedState.album.tracks.first().id)
        assertFalse(updatedState.tracksEnriched.containsKey(20L))
        assertEquals(1, updatedState.movedOutTracks.size)
        assertEquals("Another Album", updatedState.movedOutTracks[20L]?.second?.album)
    }

    @Test
    fun testResolveUnifiedAlbumArtistKeepsOnlyFirstArtist() {
        assertEquals(
            "Marracash",
            MetadataEnricherCoordinator.resolveUnifiedAlbumArtist(
                officialAlbumArtist = "Marracash",
                unifiedCandidateAlbumArtist = "Marracash feat. Guè",
                localAlbumArtist = "Marracash feat. Guè",
                firstTrackArtist = "Marracash feat. Guè",
                hasMajorityConsensus = true
            )
        )

        assertEquals(
            "Lazza",
            MetadataEnricherCoordinator.resolveUnifiedAlbumArtist(
                officialAlbumArtist = "Lazza, Sfera Ebbasta",
                unifiedCandidateAlbumArtist = "Lazza",
                localAlbumArtist = "Artista sconosciuto",
                firstTrackArtist = "Lazza feat. Sfera Ebbasta",
                hasMajorityConsensus = true
            )
        )

        assertEquals(
            "Tedua",
            MetadataEnricherCoordinator.resolveUnifiedAlbumArtist(
                officialAlbumArtist = null,
                unifiedCandidateAlbumArtist = "",
                localAlbumArtist = "Artista sconosciuto",
                firstTrackArtist = "Tedua & Rkomi",
                hasMajorityConsensus = false
            )
        )

        assertEquals(
            "Tyler, The Creator",
            MetadataEnricherCoordinator.resolveUnifiedAlbumArtist(
                officialAlbumArtist = "Tyler, The Creator",
                unifiedCandidateAlbumArtist = "Tyler, The Creator",
                localAlbumArtist = "Tyler, The Creator",
                firstTrackArtist = "Tyler, The Creator feat. Playboi Carti",
                hasMajorityConsensus = true
            )
        )
    }

    @Test
    fun testTylerTheCreatorAlbumTracksUnifyIntoSingleAlbumEvenWithoutExplicitAlbumArtist() {
        val mockUri = Mockito.mock(Uri::class.java)
        val track1 = AudioTrack(
            id = 301L,
            title = "EARFQUAKE",
            artist = "Tyler, The Creator, Playboi Carti",
            duration = 190000L,
            uri = mockUri,
            albumId = 900L,
            album = "IGOR",
            albumArtist = "",
            trackNumber = 2,
            discNumber = 1
        )
        val track2 = AudioTrack(
            id = 302L,
            title = "NEW MAGIC WAND",
            artist = "Tyler, The Creator",
            duration = 195000L,
            uri = mockUri,
            albumId = 900L,
            album = "IGOR",
            albumArtist = "Tyler, The Creator",
            trackNumber = 6,
            discNumber = 1
        )

        val albums = groupTracksIntoAlbums(listOf(track1, track2))
        assertEquals(1, albums.size)
        assertEquals("IGOR", albums.first().title)
        assertEquals("Tyler, The Creator", albums.first().artist)
        assertEquals(2, albums.first().trackCount)
    }
}
