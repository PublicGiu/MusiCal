package com.example.musicplayer.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class ArtistInfoRepositoryTest {

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
            Antonio Serretti, in arte Tony Boy, è un rapper e cantante italiano nato a Padova nel 1999.
            Ha pubblicato diversi album acclamati dalla critica come Going Hard e Nostalgia.
            
            <a href="https://www.last.fm/music/Tony+Boy">Read more on Last.fm</a>.
            User-contributed text is available under the Creative Commons By-SA License; additional terms may apply.
        """.trimIndent()

        val cleanBio = cleanLastFmBio(rawBioContent)

        assertTrue(cleanBio.startsWith("Antonio Serretti, in arte Tony Boy"))
        assertTrue(cleanBio.contains("Going Hard e Nostalgia"))
        assertFalse(cleanBio.contains("Last.fm"))
        assertFalse(cleanBio.contains("Creative Commons"))
        assertFalse(cleanBio.contains("<a href"))
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
        val limited = limitBiographyLength(shortBio, maxChars = 1200)
        assertEquals(shortBio, limited)
    }

    @Test
    fun testLimitBiographyLengthTrimsAtSentenceEnd() {
        val sentence1 = "Prima frase completa sull'artista. "
        val sentence2 = "Seconda frase molto lunga che prosegue la descrizione dettagliata. "
        val sentence3 = "Terza frase che descrive altri album e collaborazioni. "
        val sentence4 = "Quarta frase che supera ampiamente il limite dei caratteri consentiti per la biografia."
        val fullText = sentence1 + sentence2 + sentence3 + sentence4

        // Poniamo un limite di 100 caratteri: deve tagliare alla fine di sentence2
        val limited = limitBiographyLength(fullText, maxChars = 100)
        assertTrue(limited.endsWith("."))
        assertTrue(limited.contains("Seconda frase"))
        assertFalse(limited.contains("Terza frase"))
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
}
