package com.example.musicplayer.data.tagger

import android.net.Uri
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.UNRELEASED_ALBUM_TITLE
import com.example.musicplayer.data.UnreleasedSnapshot
import com.example.musicplayer.data.findAlbumForTrack
import com.example.musicplayer.data.groupTracksIntoAlbums
import com.example.musicplayer.data.isMixedOrGeneric
import com.example.musicplayer.data.matchesArtist
import com.example.musicplayer.data.tagger.client.DeezerApiClient
import com.example.musicplayer.data.tagger.client.LrclibApiClient
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.net.URLDecoder

class UnreleasedAlbumGroupingTest {

    @Test
    fun `creates Unreleased album per artist only when artist has unreleased tracks`() {
        val mockUri = Mockito.mock(Uri::class.java)
        val cartiArtistPhoto = Mockito.mock(Uri::class.java)

        // Playboi Carti: 1 brano ufficiale ("Die Lit") + 2 brani unreleased (1 via snapshot Auto-Tag, 1 via marker [Unreleased])
        val cartiOfficial = AudioTrack(
            id = 1L,
            title = "Shoota",
            artist = "Playboi Carti feat. Lil Uzi Vert",
            album = "Die Lit",
            albumArtist = "Playboi Carti",
            duration = 153_000L,
            uri = mockUri
        )
        val cartiUnreleasedFromStore = AudioTrack(
            id = 2L,
            title = "Pissy Pamper",
            artist = "Playboi Carti",
            album = "Sconosciuto",
            duration = 180_000L,
            uri = mockUri,
            albumArtUri = null,
            displayName = "pissy_pamper.mp3"
        )
        val cartiUnreleasedFromMarker = AudioTrack(
            id = 3L,
            title = "Molly (Unreleased)",
            artist = "Playboi Carti",
            album = "<unknown>",
            duration = 140_000L,
            uri = mockUri,
            albumArtUri = null,
            displayName = "Playboi Carti - Molly (Unreleased).mp3"
        )

        // Tyler, The Creator: 1 brano con tag album "Unreleased"
        val tylerUnreleased = AudioTrack(
            id = 4L,
            title = "NMW Demo",
            artist = "Tyler, The Creator feat. Kali Uchis",
            album = "Unreleased",
            albumArtist = "Tyler, The Creator",
            duration = 160_000L,
            uri = mockUri
        )

        // Marracash: solo brani ufficiali -> NON deve essere creato alcun album "Unreleased" per Marracash
        val marracashOfficial = AudioTrack(
            id = 5L,
            title = "Brivido",
            artist = "Marracash feat. Guè",
            album = "Noi, Loro, Gli Altri",
            albumArtist = "Marracash",
            duration = 200_000L,
            uri = mockUri
        )

        val snapshot = UnreleasedSnapshot(trackIds = setOf(2L))
        val albums = groupTracksIntoAlbums(
            tracks = listOf(
                cartiOfficial,
                cartiUnreleasedFromStore,
                cartiUnreleasedFromMarker,
                tylerUnreleased,
                marracashOfficial
            ),
            unreleasedSnapshot = snapshot,
            artistPhotoUriProvider = { artist ->
                if (artist == "Playboi Carti") cartiArtistPhoto else null
            }
        )

        val unreleasedAlbums = albums.filter { it.title == UNRELEASED_ALBUM_TITLE }
        assertEquals(2, unreleasedAlbums.size)

        val cartiUnreleasedAlbum = unreleasedAlbums.find { it.artist == "Playboi Carti" }
        val tylerUnreleasedAlbum = unreleasedAlbums.find { it.artist == "Tyler, The Creator" }
        assertNotNull(cartiUnreleasedAlbum)
        assertNotNull(tylerUnreleasedAlbum)
        assertNotEquals(cartiUnreleasedAlbum!!.id, tylerUnreleasedAlbum!!.id)

        assertEquals("Unreleased", cartiUnreleasedAlbum.title)
        assertEquals("Playboi Carti", cartiUnreleasedAlbum.artist)
        assertEquals(2, cartiUnreleasedAlbum.trackCount)
        assertSame(cartiArtistPhoto, cartiUnreleasedAlbum.albumArtUri)
        assertFalse(cartiUnreleasedAlbum.isMixedOrGeneric())

        assertEquals("Unreleased", tylerUnreleasedAlbum.title)
        assertEquals("Tyler, The Creator", tylerUnreleasedAlbum.artist)
        assertEquals(1, tylerUnreleasedAlbum.trackCount)
        assertTrue(tylerUnreleasedAlbum.matchesArtist("Tyler, The Creator"))
        assertFalse(tylerUnreleasedAlbum.matchesArtist("Playboi Carti"))

        // Nessun album Unreleased per Marracash (0 tracce unreleased)
        assertTrue(unreleasedAlbums.none { it.artist.equals("Marracash", ignoreCase = true) })

        // Verifica che findAlbumForTrack distingua correttamente i due album "Unreleased" per artista
        assertEquals(cartiUnreleasedAlbum.id, albums.findAlbumForTrack(cartiUnreleasedFromStore)?.id)
        assertEquals(tylerUnreleasedAlbum.id, albums.findAlbumForTrack(tylerUnreleased)?.id)
    }

    @Test
    fun `enrichTrack marks track as Unreleased with album Unreleased when Deezer returns empty results`() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val jsonBody = if (url.contains("lrclib.net")) "{}" else """{"data": []}"""
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
        val mockUri = Mockito.mock(Uri::class.java)
        val track = AudioTrack(
            id = 77L,
            title = "Playboi Carti - Cancun (Unreleased)",
            artist = "Artista sconosciuto",
            album = "Download",
            duration = 148_000L,
            uri = mockUri,
            displayName = "Playboi Carti - Cancun (Unreleased).mp3"
        )

        val enriched = repo.enrichTrack(track, EnricherOptions(embedCover = false, includeLyrics = false)).getOrThrow()
        assertTrue(enriched.isUnreleased)
        assertEquals("Cancun", enriched.title)
        assertEquals("Playboi Carti", enriched.artist)
        assertEquals("Playboi Carti", enriched.albumArtist)
        assertEquals(UNRELEASED_ALBUM_TITLE, enriched.album)
    }

    @Test
    fun `enrichTrack does NOT falsely mark track as Unreleased when Deezer fails with HTTP error`() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val repo = MetadataEnricherRepository(
            deezerClient = DeezerApiClient(mockClient),
            lrclibClient = LrclibApiClient(mockClient)
        )
        val mockUri = Mockito.mock(Uri::class.java)
        val track = AudioTrack(
            id = 88L,
            title = "Magnolia",
            artist = "Playboi Carti",
            album = "Download",
            duration = 181_000L,
            uri = mockUri
        )

        val enriched = repo.enrichTrack(track, EnricherOptions(embedCover = false, includeLyrics = false)).getOrThrow()
        assertFalse(enriched.isUnreleased)
        assertEquals("Download", enriched.album)
    }

    @Test
    fun `DeezerApiClient comma-free fallback finds Tyler The Creator when quoted comma query fails`() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val path = chain.request().url.encodedPath
                val q = URLDecoder.decode(chain.request().url.queryParameter("q").orEmpty(), "UTF-8")
                val jsonBody = when {
                    // Simula il caso reale in cui la query avanzata con virgola restituisce vuoto, ma senza virgola trova il brano
                    path.startsWith("/search") && q.contains("artist:\"Tyler, The Creator\"", ignoreCase = true) ->
                        """{"data": []}"""
                    path.startsWith("/search") && q.contains("Tyler The Creator", ignoreCase = true) ->
                        """
                        {
                          "data": [
                            {
                              "id": 674321,
                              "title": "EARFQUAKE",
                              "title_short": "EARFQUAKE",
                              "duration": 190,
                              "artist": { "name": "Tyler, The Creator" },
                              "album": {
                                "id": 96123,
                                "title": "IGOR",
                                "cover_xl": "https://example.com/igor.jpg"
                              }
                            }
                          ]
                        }
                        """.trimIndent()
                    path == "/album/96123" ->
                        """
                        {
                          "id": 96123,
                          "title": "IGOR",
                          "release_date": "2019-05-17",
                          "record_type": "album",
                          "artist": { "name": "Tyler, The Creator" },
                          "cover_xl": "https://example.com/igor.jpg",
                          "genres": { "data": [ { "name": "Rap/Hip Hop" } ] },
                          "tracks": {
                            "data": [
                              { "id": 674321, "title": "EARFQUAKE", "track_position": 2, "disk_number": 1 }
                            ]
                          },
                          "nb_tracks": 12
                        }
                        """.trimIndent()
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
        val mockUri = Mockito.mock(Uri::class.java)
        val track = AudioTrack(
            id = 90L,
            title = "EARFQUAKE",
            artist = "Tyler, The Creator feat. Playboi Carti",
            album = "Download",
            duration = 190_000L,
            uri = mockUri
        )

        val enriched = repo.enrichTrack(track, EnricherOptions(embedCover = false, includeLyrics = false)).getOrThrow()
        assertFalse(enriched.isUnreleased)
        assertEquals("EARFQUAKE", enriched.title)
        assertEquals("Tyler, The Creator feat. Playboi Carti", enriched.artist)
        assertEquals("IGOR", enriched.album)
        assertEquals("Tyler, The Creator", enriched.albumArtist)
        assertEquals(2, enriched.trackNumber)
    }
}
