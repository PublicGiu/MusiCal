package com.example.musicplayer.data.tagger

import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.LyricsRepository
import com.example.musicplayer.data.tagger.client.DeezerApiClient
import com.example.musicplayer.data.tagger.client.LrclibApiClient
import com.example.musicplayer.data.tagger.model.DeezerAlbumDetail
import com.example.musicplayer.data.tagger.model.DeezerAlbumTrackInfo
import com.example.musicplayer.data.tagger.model.DeezerTrackDetail
import com.example.musicplayer.data.tagger.repository.EnricherOptions
import com.example.musicplayer.data.tagger.repository.MetadataEnricherRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.net.URLDecoder

class DeezerAndLrclibParsingTest {

    @Test
    fun testDeezerAlbumDetailYearExtraction() {
        val albumDetail = DeezerAlbumDetail(
            id = 12345L,
            title = "Random Access Memories",
            albumArtist = "Daft Punk",
            releaseDate = "2013-05-17",
            recordType = "album",
            genre = "Electronic",
            coverXl = "https://example.com/cover_xl.jpg",
            tracks = emptyList()
        )

        assertEquals(2013, albumDetail.releaseYear)
        assertEquals("Daft Punk", albumDetail.albumArtist)
    }

    @Test
    fun testDeezerTrackDetailModel() {
        val detail = DeezerTrackDetail(
            id = 3135556L,
            title = "Harder, Better, Faster, Stronger",
            titleShort = "Harder, Better, Faster, Stronger",
            trackPosition = 4,
            diskNumber = 1,
            duration = 224L,
            releaseDate = "2001-03-12",
            albumId = 302127L,
            albumTitle = "Discovery",
            artistName = "Daft Punk",
            coverXl = "https://example.com/cover.jpg"
        )

        assertEquals(4, detail.trackPosition)
        assertEquals(1, detail.diskNumber)
        assertEquals("Discovery", detail.albumTitle)
        assertEquals("Daft Punk", detail.artistName)
    }

    @Test
    fun testAlbumTracksAlignment() {
        val repo = MetadataEnricherRepository()
        val mockUri = Mockito.mock(android.net.Uri::class.java)

        // Tracce locali con posizione non impostata o errata
        val localTracks = listOf(
            AudioTrack(id = 101L, title = "Aerodynamic", artist = "Daft Punk", duration = 210000L, album = "Discovery", trackNumber = 0, uri = mockUri, displayName = "02 - Aerodynamic.mp3"),
            AudioTrack(id = 102L, title = "Harder, Better, Faster, Stronger", artist = "Daft Punk", duration = 224000L, album = "Discovery", trackNumber = 99, uri = mockUri, displayName = "daft_punk_hbbf.mp3"),
            AudioTrack(id = 103L, title = "One More Time", artist = "Daft Punk", duration = 320000L, album = "Discovery", trackNumber = 1, uri = mockUri, displayName = "01 - One More Time.mp3")
        )

        // Tracklist ufficiale Deezer per Discovery
        val officialDeezerTracks = listOf(
            DeezerAlbumTrackInfo(id = 1L, title = "One More Time", trackPosition = 1, diskNumber = 1),
            DeezerAlbumTrackInfo(id = 2L, title = "Aerodynamic", trackPosition = 2, diskNumber = 1),
            DeezerAlbumTrackInfo(id = 3L, title = "Digital Love", trackPosition = 3, diskNumber = 1),
            DeezerAlbumTrackInfo(id = 4L, title = "Harder, Better, Faster, Stronger", trackPosition = 4, diskNumber = 1)
        )

        val aligned = repo.alignAlbumTracks(localTracks, officialDeezerTracks)

        assertEquals(3, aligned.size)
        // Aerodynamic deve essere associata alla traccia 2
        assertEquals(2, aligned[101L]?.trackPosition)
        // Harder Better deve essere associata alla traccia 4
        assertEquals(4, aligned[102L]?.trackPosition)
        // One More Time deve essere associata alla traccia 1
        assertEquals(1, aligned[103L]?.trackPosition)
    }

    @Test
    fun testLyricsParsingWithTimestamps() {
        val mockContext = Mockito.mock(android.content.Context::class.java)
        val lyricsRepo = LyricsRepository(mockContext)

        val sampleLrc = """
            [00:15.50] Like the legend of the phoenix
            [00:19.20] All ends with beginnings
            [00:23.00] What keeps the planet spinning
        """.trimIndent()

        val parsed = lyricsRepo.parseLyrics(1L, sampleLrc)
        assertTrue(parsed.isSynced)
        assertEquals(3, parsed.lines.size)
        assertEquals(15500L, parsed.lines[0].timeMs)
        assertEquals("Like the legend of the phoenix", parsed.lines[0].text)
        assertEquals(19200L, parsed.lines[1].timeMs)
        assertEquals(23000L, parsed.lines[2].timeMs)
    }

    @Test
    fun testEnrichTrackWithArtistPrefixInTitleAndSpuriousLocalArtist() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val path = chain.request().url.encodedPath
                val queryParam = URLDecoder.decode(chain.request().url.queryParameter("q").orEmpty(), "UTF-8")

                val jsonBody = when {
                    path.startsWith("/search") &&
                        queryParam.contains("Tango", ignoreCase = true) &&
                        !queryParam.contains("Tananai - Tango", ignoreCase = true) &&
                        queryParam.contains("Tananai", ignoreCase = true) -> {
                        """
                        {
                          "data": [
                            {
                              "id": 9991,
                              "title": "Tango",
                              "title_short": "Tango",
                              "duration": 212,
                              "artist": { "name": "Tananai" },
                              "album": {
                                "id": 5551,
                                "title": "RAVE, ECLISSI",
                                "cover_xl": "https://example.com/tango.jpg"
                              }
                            }
                          ]
                        }
                        """.trimIndent()
                    }
                    path.startsWith("/search") -> """{"data": []}"""
                    path == "/album/5551" -> {
                        """
                        {
                          "id": 5551,
                          "title": "RAVE, ECLISSI",
                          "release_date": "2022-11-25",
                          "record_type": "album",
                          "artist": { "name": "Tananai" },
                          "cover_xl": "https://example.com/tango.jpg",
                          "genres": { "data": [ { "name": "Pop" } ] },
                          "tracks": {
                            "data": [
                              { "id": 9991, "title": "Tango", "track_position": 4, "disk_number": 1 }
                            ]
                          },
                          "nb_tracks": 1
                        }
                        """.trimIndent()
                    }
                    url.contains("lrclib.net") -> """{"id": 1, "trackName": "Tango", "artistName": "Tananai", "syncedLyrics": "[00:10.00] Linea"}"""
                    else -> """{}"""
                }

                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonBody.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val repo = MetadataEnricherRepository(
            deezerClient = DeezerApiClient(mockClient),
            lrclibClient = LrclibApiClient(mockClient)
        )
        val mockUri = Mockito.mock(android.net.Uri::class.java)
        val track = AudioTrack(
            id = 50L,
            title = "Tananai - Tango",
            artist = "YouTubeDownloader",
            duration = 212_000L,
            uri = mockUri,
            displayName = "download_audio_50.mp3"
        )

        val result = repo.enrichTrackWithCandidates(
            track = track,
            options = EnricherOptions(embedCover = false, includeLyrics = true)
        ).getOrThrow()

        assertEquals("Tango", result.first.title)
        assertEquals("Tananai", result.first.artist)
        assertEquals("RAVE, ECLISSI", result.first.album)
        assertEquals(4, result.first.trackNumber)
        assertTrue(result.first.hasSyncedLyrics)
        assertEquals(1, result.second.size)

        // Verifica anche che searchCustom faccia fallback automatico se il titolo inserito contiene "Artista - Canzone"
        val customResults = repo.searchCustom("Tananai - Tango", "").getOrThrow()
        assertEquals(1, customResults.size)
        assertEquals("Tango", customResults.first().title)
    }

    @Test
    fun testAlignAlbumTracksWithArtistPrefixInTitle() {
        val repo = MetadataEnricherRepository()
        val mockUri = Mockito.mock(android.net.Uri::class.java)
        val localTracks = listOf(
            AudioTrack(
                id = 201L,
                title = "Daft Punk - Digital Love",
                artist = "CanaleSbagliato",
                duration = 298000L,
                album = "Discovery",
                uri = mockUri,
                displayName = "track_unknown.mp3"
            )
        )
        val official = listOf(
            DeezerAlbumTrackInfo(id = 3L, title = "Digital Love", trackPosition = 3, diskNumber = 1)
        )

        val aligned = repo.alignAlbumTracks(localTracks, official)
        assertEquals(3, aligned[201L]?.trackPosition)
    }

    @Test
    fun testFetchLyricsOnlyRetrievesSyncedLyricsWithoutAlteringTrackMetadata() = runBlocking {
        var deezerAlbumCalled = false
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val path = chain.request().url.encodedPath
                if (path.startsWith("/album/")) {
                    deezerAlbumCalled = true
                }
                val jsonBody = when {
                    url.contains("lrclib.net/api/get") && url.contains("Brivido") -> {
                        """
                        {
                          "id": 777,
                          "trackName": "Brivido",
                          "artistName": "Marracash",
                          "instrumental": false,
                          "plainLyrics": "Testo normale",
                          "syncedLyrics": "[00:12.00] Verso sincronizzato"
                        }
                        """.trimIndent()
                    }
                    else -> """{"data": []}"""
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonBody.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val repo = MetadataEnricherRepository(
            deezerClient = DeezerApiClient(mockClient),
            lrclibClient = LrclibApiClient(mockClient)
        )
        val mockUri = Mockito.mock(android.net.Uri::class.java)
        val originalTrack = AudioTrack(
            id = 500L,
            title = "Marracash - Brivido",
            artist = "Marracash Custom Tag",
            album = "Il Mio Album Custom",
            duration = 200_000L,
            uri = mockUri,
            displayName = "marracash_brivido.mp3"
        )

        val lrclib = repo.fetchLyricsOnly(originalTrack).getOrThrow()
        assertNotNull(lrclib)
        assertTrue(lrclib!!.hasSyncedLyrics)
        assertEquals("[00:12.00] Verso sincronizzato", lrclib.syncedLyrics)
        // Nessun recupero di dettagli album Deezer e nessuna modifica ai metadati di AudioTrack
        org.junit.Assert.assertFalse(deezerAlbumCalled)
        assertEquals("Marracash - Brivido", originalTrack.title)
        assertEquals("Marracash Custom Tag", originalTrack.artist)
        assertEquals("Il Mio Album Custom", originalTrack.album)
    }
}
