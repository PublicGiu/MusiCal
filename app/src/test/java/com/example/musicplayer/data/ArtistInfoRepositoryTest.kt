package com.example.musicplayer.data

import android.content.Context
import android.net.Uri
import com.example.musicplayer.data.lastfm.LastFmConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import java.net.URLDecoder

class ArtistInfoRepositoryTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val dummyUri: Uri = Mockito.mock(Uri::class.java)

    @Test
    fun testArtistInfoModelDefaults() {
        val info = ArtistInfo(
            name = "Tony Boy",
            photoUrl = "https://example.com/photo.jpg",
            biography = "Rapper italiano",
            source = "Deezer / Wikipedia"
        )

        assertEquals("Tony Boy", info.name)
        assertEquals("https://example.com/photo.jpg", info.photoUrl)
        assertEquals("Rapper italiano", info.biography)
        assertEquals("Deezer / Wikipedia", info.source)
        assertTrue(info.lastUpdated > 0L)
    }

    @Test
    fun testArtistSearchResult() {
        val result = ArtistSearchResult(
            name = "Tony Boy",
            trackCount = 64,
            albumCount = 5,
            photoUrl = null,
            fallbackCoverUri = null
        )

        assertEquals("Tony Boy", result.name)
        assertEquals(64, result.trackCount)
        assertEquals(5, result.albumCount)
        assertNull(result.photoUrl)
        assertNull(result.fallbackCoverUri)
    }

    @Test
    fun testDeezerJsonParsingExactMatch() {
        val rawJson = """
            {
                "data": [
                    {
                        "id": 1234,
                        "name": "Tony Boy",
                        "picture_medium": "https://api.deezer.com/artist/1234/medium.jpg",
                        "picture_big": "https://api.deezer.com/artist/1234/big.jpg",
                        "picture_xl": "https://api.deezer.com/artist/1234/xl.jpg"
                    },
                    {
                        "id": 5678,
                        "name": "Tony Boy Tribute",
                        "picture_xl": "https://api.deezer.com/artist/5678/xl.jpg"
                    }
                ]
            }
        """.trimIndent()

        val json = JSONObject(rawJson)
        val dataArray = json.optJSONArray("data")
        assertNotNull(dataArray)

        var photoUrl: String? = null
        val cleanName = "Tony Boy"

        var bestObj: JSONObject? = null
        for (i in 0 until dataArray!!.length()) {
            val obj = dataArray.optJSONObject(i)
            if (obj != null && obj.optString("name").equals(cleanName, ignoreCase = true)) {
                bestObj = obj
                break
            }
        }
        val target = bestObj ?: dataArray.optJSONObject(0)
        if (target != null) {
            photoUrl = target.optString("picture_xl").takeIf { it.isNotBlank() }
                ?: target.optString("picture_big").takeIf { it.isNotBlank() }
                ?: target.optString("picture_medium").takeIf { it.isNotBlank() }
        }

        assertEquals("https://api.deezer.com/artist/1234/xl.jpg", photoUrl)
    }

    @Test
    fun testWikipediaSummaryParsing() {
        val rawWiki = """
            {
                "title": "Tony Boy",
                "extract": "Antonio Serretti, noto come Tony Boy (Padova, 1999), è un rapper e cantante italiano.",
                "originalimage": {
                    "source": "https://upload.wikimedia.org/wikipedia/commons/tony_boy.jpg"
                }
            }
        """.trimIndent()

        val json = JSONObject(rawWiki)
        val extract = json.optString("extract").trim()
        val originalImg = json.optJSONObject("originalimage")?.optString("source")

        assertTrue(extract.startsWith("Antonio Serretti"))
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/tony_boy.jpg", originalImg)
    }

    @Test
    fun testLastFmBioCleanup() {
        val rawBioContent = """
            Antonio Serretti, in arte &quot;Tony Boy&quot;, è un rapper &amp; cantante italiano nato a Padova nel 1999.
            Ha pubblicato l&#39;album &apos;Going Hard&apos; e Nostalgia...
            
            <a href="https://www.last.fm/music/Tony+Boy">Read more on Last.fm</a>.
            User-contributed text is available under the Creative Commons By-SA License; additional terms may apply.
        """.trimIndent()

        val cleanBio = cleanLastFmBio(rawBioContent)

        assertTrue(cleanBio.startsWith("Antonio Serretti, in arte \"Tony Boy\", è un rapper & cantante italiano"))
        assertTrue(cleanBio.contains("l'album 'Going Hard' e Nostalgia"))
        assertTrue(cleanBio.endsWith("."))
        assertFalse(cleanBio.endsWith("..."))
        assertFalse(cleanBio.contains("Last.fm"))
        assertFalse(cleanBio.contains("Creative Commons"))
        assertFalse(cleanBio.contains("<a href"))
        assertFalse(cleanBio.contains("&quot;"))
        assertFalse(cleanBio.contains("&amp;"))
        assertFalse(cleanBio.contains("&#39;"))
    }

    @Test
    fun testExtractMainArtistNormalization() {
        assertEquals("Tony Boy", extractMainArtist("Tony Boy feat. Shiva"))
        assertEquals("Tony Boy", extractMainArtist("Tony Boy & Shiva"))
        assertEquals("Tony Boy", extractMainArtist("Tony Boy with Shiva"))
        assertEquals("Drake", extractMainArtist("Drake ft. 21 Savage"))
        assertEquals("SingleArtist", extractMainArtist("SingleArtist"))
    }

    @Test
    fun testMatchesArtist() {
        val track = AudioTrack(
            id = 1L,
            title = "Hot",
            artist = "Tony Boy feat. Artie 5ive",
            album = "Going Hard 3",
            duration = 180000L,
            uri = dummyUri
        )

        // L'artista principale "Tony Boy" viene correttamente riconosciuto
        assertTrue(track.matchesArtist("Tony Boy"))
        assertTrue(track.matchesArtist("Tony Boy feat. Artie 5ive"))
        // Un artista diverso non deve corrispondere
        assertFalse(track.matchesArtist("Sfera Ebbasta"))
    }

    @Test
    fun testArtistInfoCustomFlagsSerialization() {
        val original = ArtistInfo(
            name = "Pitta",
            photoUrl = "file:///storage/emulated/0/custom.jpg",
            biography = "Rapper italiano",
            source = "Personalizzata",
            isCustomPhoto = true,
            isCustomBio = true
        )

        // Simula serializzazione JSON
        val obj = JSONObject()
        obj.put("name", original.name)
        original.photoUrl?.let { obj.put("photoUrl", it) }
        original.biography?.let { obj.put("biography", it) }
        original.source?.let { obj.put("source", it) }
        if (original.isCustomPhoto) obj.put("isCustomPhoto", true)
        if (original.isCustomBio) obj.put("isCustomBio", true)
        obj.put("lastUpdated", original.lastUpdated)

        // Simula deserializzazione JSON
        val deserialized = ArtistInfo(
            name = obj.optString("name"),
            photoUrl = obj.optString("photoUrl").takeIf { it.isNotBlank() },
            biography = obj.optString("biography").takeIf { it.isNotBlank() },
            source = obj.optString("source").takeIf { it.isNotBlank() },
            isCustomPhoto = obj.optBoolean("isCustomPhoto", false),
            isCustomBio = obj.optBoolean("isCustomBio", false),
            lastUpdated = obj.optLong("lastUpdated", 0L)
        )

        assertEquals("Pitta", deserialized.name)
        assertEquals("file:///storage/emulated/0/custom.jpg", deserialized.photoUrl)
        assertEquals("Rapper italiano", deserialized.biography)
        assertEquals("Personalizzata", deserialized.source)
        assertTrue(deserialized.isCustomPhoto)
        assertTrue(deserialized.isCustomBio)
    }

    @Test
    fun testWikipediaOpenSearchParsing() {
        val rawOpenSearch = """
            [
                "Pitta",
                ["Pitta", "Pitta (rapper)", "Nuova Scena"],
                ["", "", ""],
                ["https://it.wikipedia.org/wiki/Pitta", "https://it.wikipedia.org/wiki/Pitta_(rapper)", "https://it.wikipedia.org/wiki/Nuova_Scena"]
            ]
        """.trimIndent()

        val jsonArray = JSONArray(rawOpenSearch)
        assertEquals(4, jsonArray.length())
        val titlesArray = jsonArray.optJSONArray(1)
        assertNotNull(titlesArray)
        assertEquals("Pitta", titlesArray!!.optString(0))
        assertEquals("Pitta (rapper)", titlesArray.optString(1))
    }

    @Test
    fun testLimitBiographyLengthShortText() {
        val shortBio = "Aubrey Drake Graham è un rapper e cantante canadese."
        val limited = limitBiographyLength(shortBio, maxChars = 2800)
        assertEquals(shortBio, limited)
    }

    @Test
    fun testLimitBiographyLengthPreservesCompleteParagraphs() {
        val p1 = "Primo paragrafo che introduce l'artista e le sue origini musicali nei minimi dettagli."
        val p2 = "Secondo paragrafo che racconta l'uscita del primo album in studio e il successo ottenuto nelle classifiche nazionali."
        val p3 = "Terzo paragrafo che descrive la tournée mondiale e le successive collaborazioni con grandi artisti."
        val fullBio = "$p1\n\n$p2\n\n$p3"

        // Con limite a 220 caratteri: p1 (~87) + p2 (~117) = ~206 caratteri -> entrano entrambi completi.
        // p3 farebbe superare la soglia, quindi NON deve essere iniziato a metà lasciando il concetto campato in aria.
        val limited = limitBiographyLength(fullBio, maxChars = 220)
        assertTrue(limited.contains(p1))
        assertTrue(limited.contains(p2))
        assertFalse(limited.contains(p3))
        assertTrue(limited.endsWith("."))
        assertFalse(limited.endsWith("..."))
    }

    @Test
    fun testLimitBiographyLengthTrimsAtSentenceEnd() {
        val sentence1 = "Prima frase completa sull'artista. "
        val sentence2 = "Seconda frase molto lunga che prosegue la descrizione dettagliata. "
        val sentence3 = "Terza frase che descrive altri album e collaborazioni. "
        val sentence4 = "Quarta frase che supera ampiamente il limite dei caratteri consentiti per la biografia."
        val fullText = sentence1 + sentence2 + sentence3 + sentence4

        // Poniamo un limite di 100 caratteri in un testo privo di interruzioni di paragrafo:
        // deve tagliare alla fine di sentence2
        val limited = limitBiographyLength(fullText, maxChars = 100)
        assertTrue(limited.endsWith("."))
        assertTrue(limited.contains("Seconda frase"))
        assertFalse(limited.contains("Terza frase"))
    }

    @Test
    fun testIsReliableMusicWikipediaPageValidArtists() {
        val tonyBoyJson = JSONObject("""
            {
                "title": "Tony Boy",
                "type": "standard",
                "description": "rapper italiano",
                "extract": "Antonio Serretti, noto come Tony Boy (Padova, 1999), è un rapper e cantante italiano."
            }
        """.trimIndent())
        assertTrue(isReliableMusicWikipediaPage(tonyBoyJson, "Tony Boy"))

        val coronaCantanteJson = JSONObject("""
            {
                "title": "Corona (cantante)",
                "type": "standard",
                "description": "cantante brasiliana naturalizzata italiana",
                "extract": "Corona, pseudonimo di Olga Maria de Souza, è una cantante brasiliana naturalizzata italiana nota per The Rhythm of the Night."
            }
        """.trimIndent())
        assertTrue(isReliableMusicWikipediaPage(coronaCantanteJson, "Corona"))

        val genesisBandJson = JSONObject("""
            {
                "title": "Genesis (gruppo musicale)",
                "type": "standard",
                "description": "gruppo musicale britannico",
                "extract": "I Genesis sono stati un celebre gruppo musicale rock britannico formatosi nel 1967."
            }
        """.trimIndent())
        assertTrue(isReliableMusicWikipediaPage(genesisBandJson, "Genesis"))
    }

    @Test
    fun testIsReliableMusicWikipediaPageRejectsDisambiguation() {
        val disambiguationTypeJson = JSONObject("""
            {
                "title": "Corona",
                "type": "disambiguation",
                "description": "pagina di disambiguazione di un progetto Wikimedia",
                "extract": "Corona può riferirsi a diverse voci."
            }
        """.trimIndent())
        assertFalse(isReliableMusicWikipediaPage(disambiguationTypeJson, "Corona"))

        val disambiguationExtractJson = JSONObject("""
            {
                "title": "Genesis",
                "type": "standard",
                "extract": "Genesis può indicare: Genesis (gruppo musicale), Genesi (Bibbia)..."
            }
        """.trimIndent())
        assertFalse(isReliableMusicWikipediaPage(disambiguationExtractJson, "Genesis"))
    }

    @Test
    fun testIsReliableMusicWikipediaPageRejectsNonMusicDomains() {
        val astronomyJson = JSONObject("""
            {
                "title": "Corona",
                "type": "standard",
                "description": "fenomeno astronomico del Sole",
                "extract": "La corona solare è la parte più esterna dell'atmosfera del Sole, formata da gas ionizzato ad altissime temperature."
            }
        """.trimIndent())
        assertFalse(isReliableMusicWikipediaPage(astronomyJson, "Corona"))

        val riverJson = JSONObject("""
            {
                "title": "Jordan",
                "type": "standard",
                "description": "fiume del Medio Oriente",
                "extract": "Il Giordano è un fiume storico del Medio Oriente che sfocia nel Mar Morto."
            }
        """.trimIndent())
        assertFalse(isReliableMusicWikipediaPage(riverJson, "Jordan"))

        val birdJson = JSONObject("""
            {
                "title": "Pitta",
                "type": "standard",
                "description": "famiglia di uccelli passeriformi",
                "extract": "I Pittidi sono una famiglia di uccelli passeriformi diffusi nelle foreste tropicali."
            }
        """.trimIndent())
        assertFalse(isReliableMusicWikipediaPage(birdJson, "Pitta"))

        val squareJson = JSONObject("""
            {
                "title": "Piazza Pitti",
                "type": "standard",
                "description": "piazza di Firenze",
                "extract": "Piazza Pitti è una piazza di Firenze dominata dalla mole di Palazzo Pitti."
            }
        """.trimIndent())
        assertFalse(isReliableMusicWikipediaPage(squareJson, "Pitta"))
    }

    @Test
    fun testIsReliableMusicText() {
        val nonMusicText = "La corona solare è la parte più esterna dell'atmosfera del Sole, visibile durante un'eclissi totale."
        assertFalse(isReliableMusicText(nonMusicText))

        val musicText = "Antonio Serretti, in arte Tony Boy, è un rapper e cantante italiano che ha pubblicato vari album e singoli."
        assertTrue(isReliableMusicText(musicText))
    }

    @Test
    fun testIsCollabArtist() {
        assertTrue(isCollabArtist("Drake & 21 Savage"))
        assertTrue(isCollabArtist("Salmo & Noyz Narcos"))
        assertTrue(isCollabArtist("Marracash x Guè"))
        assertTrue(isCollabArtist("Baby Gang + Simba La Rue"))
        assertTrue(isCollabArtist("Simon and Garfunkel"))
        assertTrue(isCollabArtist("Drake, 21 Savage"))

        assertFalse(isCollabArtist("Drake"))
        assertFalse(isCollabArtist("Drake feat. 21 Savage"))
        assertFalse(isCollabArtist("Tyler, The Creator"))
        assertFalse(isCollabArtist("Tyler, The Creator feat. Kali Uchis"))
        assertTrue(isCollabArtist("Tyler, The Creator & A\$AP Rocky"))
        assertEquals("Tyler, The Creator", extractMainArtist("Tyler, The Creator"))
        assertEquals("Tyler, The Creator", extractMainArtist("Tyler, The Creator feat. Kali Uchis"))
        assertEquals(
            listOf("Tyler, The Creator", "A\$AP Rocky"),
            extractCollaborators("Tyler, The Creator & A\$AP Rocky")
        )
        assertFalse(isCollabArtist("Various Artists"))
        assertFalse(isCollabArtist(null))
        assertFalse(isCollabArtist(""))
    }

    @Test
    fun testExtractCollaborators() {
        val duo = extractCollaborators("Drake & 21 Savage")
        assertEquals(listOf("Drake", "21 Savage"), duo)

        val trio = extractCollaborators("Artista 1 x Artista 2 + Artista 3")
        assertEquals(listOf("Artista 1", "Artista 2", "Artista 3"), trio)
    }

    @Test
    fun testIsPartnerInCollab() {
        assertTrue(isPartnerInCollab("Drake", "Drake & 21 Savage"))
        assertTrue(isPartnerInCollab("21 Savage", "Drake & 21 Savage"))
        assertFalse(isPartnerInCollab("Travis Scott", "Drake & 21 Savage"))
        assertFalse(isPartnerInCollab(null, "Drake & 21 Savage"))
        assertFalse(isPartnerInCollab("Drake", null))
    }

    @Test
    fun testCollabAndSoloTrackMatching() {
        val soloTrack = AudioTrack(
            id = 1L,
            title = "God's Plan",
            artist = "Drake",
            album = "Scorpion",
            duration = 180000L,
            uri = dummyUri
        )
        val featTrack = AudioTrack(
            id = 2L,
            title = "Jimmy Cooks",
            artist = "Drake feat. 21 Savage",
            album = "Honestly, Nevermind",
            duration = 200000L,
            uri = dummyUri
        )
        val collabTrack = AudioTrack(
            id = 3L,
            title = "Rich Flex",
            artist = "Drake & 21 Savage",
            album = "Her Loss",
            duration = 220000L,
            uri = dummyUri
        )

        // Profilo solista Drake:
        // - Deve contenere il brano solista
        // - Deve contenere il brano con feat
        // - NON deve contenere il brano del collab album per evitare duplicazioni
        assertTrue(soloTrack.matchesArtist("Drake"))
        assertTrue(featTrack.matchesArtist("Drake"))
        assertFalse(collabTrack.matchesArtist("Drake"))

        // Profilo di coppia "Drake & 21 Savage":
        // - Deve contenere SOLO il brano di coppia
        // - NON deve contenere brani solisti
        assertTrue(collabTrack.matchesArtist("Drake & 21 Savage"))
        assertFalse(soloTrack.matchesArtist("Drake & 21 Savage"))
        assertFalse(featTrack.matchesArtist("Drake & 21 Savage"))
    }

    @Test
    fun testMemoizedArtistMatchingConsistencyAcrossLargeCatalog() {
        val catalog = (1..2000).map { idx ->
            val artist = when (idx % 5) {
                0 -> "Marracash feat. Guè"
                1 -> "Tyler, The Creator feat. Kali Uchis"
                2 -> "Drake & 21 Savage"
                3 -> "Lazza"
                else -> "Playboi Carti"
            }
            AudioTrack(
                id = idx.toLong(),
                title = "Track $idx",
                artist = artist,
                album = "Album ${idx % 20}",
                duration = 180_000L,
                uri = dummyUri
            )
        }

        val marraTracks = catalog.filter { it.matchesArtist("Marracash") }
        val tylerTracks = catalog.filter { it.matchesArtist("Tyler, The Creator") }
        val duoTracks = catalog.filter { it.matchesArtist("Drake & 21 Savage") }

        assertEquals(400, marraTracks.size)
        assertEquals(400, tylerTracks.size)
        assertEquals(400, duoTracks.size)
    }

    @Test
    fun testCountCatalogMentionsInTextAndBoundaryProtection() {
        val text = "Nicola Pittalis, in arte Pitta, è un rapper italiano noto per l'album Sardegna e il singolo Fino all'alba."
        val stats = countCatalogMentionsInText(
            text = text,
            trackRefs = listOf("Fino all'alba", "Io", "In"),
            albumRefs = listOf("Sardegna")
        )
        assertEquals(1, stats.trackMatches)
        assertEquals(1, stats.albumMatches)
        assertEquals(2, stats.total)

        // Parole corte (< 3 char), termini generici musicali o sottostringhe dentro altre parole non devono produrre falsi positivi
        val falseStats = countCatalogMentionsInText(
            text = "Artista famoso per la musica elettronica e sognando nuovi traguardi.",
            trackRefs = listOf("Musica", "Arte", "Io", "Sogna"),
            albumRefs = emptyList()
        )
        assertEquals(0, falseStats.trackMatches)
    }

    @Test
    fun testDisambiguateMultiArtistBioSelectsCorrectSection() {
        val multiBio = """
            There are multiple artists with this name:

            1) Pitta is an indie rock band formed in Tokyo in 2004, known for the album Neon Streets.

            2) Pitta (Nicola Pittalis) is an Italian rapper known for tracks like Fino all'alba and Sardegna.
        """.trimIndent()

        val result = disambiguateMultiArtistBio(
            bio = multiBio,
            trackRefs = listOf("Fino all'alba"),
            albumRefs = listOf("Sardegna")
        )
        assertEquals(MultiArtistBioStatus.SELECTED_SECTION, result.status)
        assertNotNull(result.selectedBio)
        assertTrue(result.selectedBio!!.contains("Italian rapper"))
        assertFalse(result.selectedBio!!.contains("Tokyo"))

        val ambiguousResult = disambiguateMultiArtistBio(
            bio = multiBio,
            trackRefs = listOf("Canzone Inesistente"),
            albumRefs = emptyList()
        )
        assertEquals(MultiArtistBioStatus.AMBIGUOUS_NO_MATCH, ambiguousResult.status)
    }

    @Test
    fun testEndToEndHomonymyResolutionUsesTrackReferencesAcrossDeezerAndWikipedia() = runBlocking {
        val mockContext = Mockito.mock(Context::class.java)
        Mockito.`when`(mockContext.filesDir).thenReturn(tempFolder.root)

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url
                val host = url.host
                val path = url.encodedPath
                val query = URLDecoder.decode(url.queryParameter("q").orEmpty(), "UTF-8")
                val srsearch = URLDecoder.decode(url.queryParameter("srsearch").orEmpty(), "UTF-8")
                val titles = URLDecoder.decode(url.queryParameter("titles").orEmpty(), "UTF-8")

                val bodyJson = when {
                    // Deezer: ricerca per brano identifica l'ID esatto 2002 (non l'omonimo 1001)
                    host == "api.deezer.com" && path == "/search" && query.contains("Sognando", ignoreCase = true) -> {
                        """
                        {
                          "data": [
                            {
                              "id": 9001,
                              "title": "Sognando",
                              "title_short": "Sognando",
                              "artist": {
                                "id": 2002,
                                "name": "Pitta",
                                "picture_xl": "https://cdn.deezer.com/artist/2002/real_pitta_xl.jpg"
                              },
                              "album": { "id": 500, "title": "Sardegna" }
                            }
                          ]
                        }
                        """.trimIndent()
                    }
                    host == "api.deezer.com" && path == "/artist/2002/top" -> {
                        """{"data": [{"title": "Sognando", "title_short": "Sognando"}]}"""
                    }
                    host == "api.deezer.com" && path == "/artist/2002/albums" -> {
                        """{"data": [{"title": "Sardegna"}]}"""
                    }
                    // Last.fm: restituisce una bio di un omonimo sbagliato e top tracks completamente diverse
                    host.contains("last.fm") && url.queryParameter("method") == "artist.getinfo" -> {
                        """
                        {
                          "artist": {
                            "name": "Pitta",
                            "bio": {
                              "content": "Pitta is an American jazz quartet formed in 1982 in Chicago, famous for the album Blue Brass."
                            }
                          }
                        }
                        """.trimIndent()
                    }
                    host.contains("last.fm") && url.queryParameter("method") == "artist.gettoptracks" -> {
                        """
                        {
                          "toptracks": {
                            "track": [
                              {"name": "Chicago Midnight"},
                              {"name": "Blue Brass"}
                            ]
                          }
                        }
                        """.trimIndent()
                    }
                    // Wikipedia IT: MediaWiki search con artista + brano trova la pagina reale (es. sotto nome anagrafico)
                    host == "it.wikipedia.org" && path == "/w/api.php" && url.queryParameter("list") == "search" -> {
                        if (srsearch.contains("Sognando")) {
                            """{"query": {"search": [{"title": "Nicola Pittalis"}]}}"""
                        } else {
                            """{"query": {"search": [{"title": "Pitta (cantante americano)"}, {"title": "Nicola Pittalis"}]}}"""
                        }
                    }
                    // Wikipedia IT batch query: confronta l'omonimo americano vs il profilo reale che cita il brano Sognando
                    host == "it.wikipedia.org" && path == "/w/api.php" && url.queryParameter("prop")?.contains("extracts") == true -> {
                        """
                        {
                          "query": {
                            "pages": {
                              "101": {
                                "pageid": 101,
                                "title": "Pitta (cantante americano)",
                                "description": "cantante statunitense",
                                "extract": "Pitta è un cantante statunitense di musica jazz attivo negli anni ottanta. Ha inciso diversi dischi di successo internazionale tra cui Blue Brass e Chicago Nights presso la storica etichetta americana. La sua carriera si è svolta interamente negli Stati Uniti con numerosi concerti dal vivo e tournée jazz internazionali, collaborando con importanti strumentisti della scena di Chicago e pubblicando oltre dieci album in studio."
                              },
                              "102": {
                                "pageid": 102,
                                "title": "Nicola Pittalis",
                                "description": "rapper italiano",
                                "extract": "Nicola Pittalis, noto con lo pseudonimo Pitta, è un rapper italiano.\n\n== Carriera ==\nNel corso della sua carriera ha pubblicato l'album Sardegna contenente il celebre brano Sognando."
                              }
                            }
                          }
                        }
                        """.trimIndent()
                    }
                    else -> """{}"""
                }

                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bodyJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val repo = ArtistInfoRepository(context = mockContext, client = mockClient)
        LastFmConfig.setCustomCredentials("test_api_key", "test_secret")
        try {
            val info = repo.getArtistInfo(
                artistName = "Pitta",
                forceRefresh = true,
                referenceTrackTitles = listOf("Sognando"),
                referenceAlbumTitles = listOf("Sardegna")
            )

            assertEquals("https://cdn.deezer.com/artist/2002/real_pitta_xl.jpg", info.photoUrl)
            assertEquals("Wikipedia", info.source)
            assertNotNull(info.biography)
            assertTrue(info.biography!!.contains("Nicola Pittalis"))
            assertFalse(info.biography!!.contains("Chicago"))
            assertEquals(ArtistInfoRepository.CURRENT_DISAMBIGUATION_VERSION, info.disambiguationVersion)
        } finally {
            LastFmConfig.setCustomCredentials(null, null)
        }
    }

    @Test
    fun testIsReliableMusicWikipediaPageRejectsSongsAndAlbums() {
        val singleWithSuffix = JSONObject("""
            {
                "title": "Brividi (singolo)",
                "type": "standard",
                "description": "singolo di Mahmood e Blanco del 2022",
                "extract": "Brividi è un singolo dei cantanti italiani Mahmood e Blanco, pubblicato il 2 febbraio 2022."
            }
        """.trimIndent())
        assertFalse(isReliableMusicWikipediaPage(singleWithSuffix, "Mahmood"))

        val singleWithoutSuffix = JSONObject("""
            {
                "title": "Cenere",
                "type": "standard",
                "description": "",
                "extract": "Cenere è un brano musicale del rapper italiano Lazza, pubblicato come secondo singolo dall'album Sirio."
            }
        """.trimIndent())
        assertFalse(isReliableMusicWikipediaPage(singleWithoutSuffix, "Lazza"))

        val albumJson = JSONObject("""
            {
                "title": "Sirio",
                "type": "standard",
                "description": "album in studio di Lazza",
                "extract": "Sirio è il terzo album in studio del rapper italiano Lazza, pubblicato nel 2022."
            }
        """.trimIndent())
        assertFalse(isReliableMusicWikipediaPage(albumJson, "Lazza"))

        val englishSongJson = JSONObject("""
            {
                "title": "Blinding Lights",
                "type": "standard",
                "description": "2019 single by the Weeknd",
                "extract": "Blinding Lights is a song by Canadian singer-songwriter the Weeknd, recorded for his fourth studio album After Hours."
            }
        """.trimIndent())
        assertFalse(isReliableMusicWikipediaPage(englishSongJson, "The Weeknd", lang = "en"))

        val artistMentioningDebutAlbum = JSONObject("""
            {
                "title": "Mario Rossi",
                "type": "standard",
                "description": "cantante italiano",
                "extract": "Mario Rossi è un cantante italiano; il suo primo lavoro è un album uscito nel 2020."
            }
        """.trimIndent())
        assertTrue(isReliableMusicWikipediaPage(artistMentioningDebutAlbum, "Mario Rossi"))
    }

    @Test
    fun testEndToEndRejectsWikipediaSongPagesAndNeverShowsSongDescription() = runBlocking {
        val mockContext = Mockito.mock(Context::class.java)
        Mockito.`when`(mockContext.filesDir).thenReturn(tempFolder.root)

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url
                val host = url.host
                val path = url.encodedPath
                val bodyJson = when {
                    host == "api.deezer.com" && path == "/search" -> {
                        """
                        {
                          "data": [
                            {
                              "id": 777,
                              "title": "Stella Cadente",
                              "title_short": "Stella Cadente",
                              "artist": {
                                "id": 888,
                                "name": "Artista Test",
                                "picture_xl": "https://cdn.deezer.com/artist/888/pic.jpg"
                              },
                              "album": { "id": 999, "title": "Notte" }
                            }
                          ]
                        }
                        """.trimIndent()
                    }
                    host == "api.deezer.com" && path == "/artist/888/top" -> {
                        """{"data": [{"title": "Stella Cadente", "title_short": "Stella Cadente"}]}"""
                    }
                    host == "api.deezer.com" && path == "/artist/888/albums" -> {
                        """{"data": [{"title": "Notte"}]}"""
                    }
                    (host == "it.wikipedia.org" || host == "en.wikipedia.org") && path == "/w/api.php" && url.queryParameter("list") == "search" -> {
                        """{"query": {"search": [{"title": "Stella Cadente"}, {"title": "Notte (album)"}]}}"""
                    }
                    (host == "it.wikipedia.org" || host == "en.wikipedia.org") && path == "/w/api.php" && url.queryParameter("prop")?.contains("extracts") == true -> {
                        """
                        {
                          "query": {
                            "pages": {
                              "301": {
                                "pageid": 301,
                                "title": "Stella Cadente",
                                "description": "brano musicale",
                                "extract": "Stella Cadente è una canzone scritta ed interpretata dal cantante Artista Test, estratta dall'album Notte."
                              },
                              "302": {
                                "pageid": 302,
                                "title": "Notte (album)",
                                "description": "album in studio di Artista Test",
                                "extract": "Notte è il primo album in studio di Artista Test."
                              }
                            }
                          }
                        }
                        """.trimIndent()
                    }
                    else -> """{}"""
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bodyJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val repo = ArtistInfoRepository(context = mockContext, client = mockClient)
        LastFmConfig.setCustomCredentials(null, null)
        val info = repo.getArtistInfo(
            artistName = "Artista Test",
            forceRefresh = true,
            referenceTrackTitles = listOf("Stella Cadente"),
            referenceAlbumTitles = listOf("Notte")
        )

        assertEquals("https://cdn.deezer.com/artist/888/pic.jpg", info.photoUrl)
        assertNull(info.biography)
    }
}
