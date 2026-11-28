package com.example.musicplayer.data.tagger.writer

import com.example.musicplayer.data.tagger.model.EnrichedMetadata
import com.example.musicplayer.data.tagger.repository.EnricherOptions
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.id3.ID3v11Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.valuepair.TextEncoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudioTagWriterEncodingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * Costruisce un file MP3 sintetico valido composto da più frame MPEG-1 Layer 3 (128 kbps, 44.1 kHz, 417 byte/frame)
     * leggibile e scrivibile da jaudiotagger su JVM.
     */
    private fun createMinimalValidMp3(name: String): File {
        val mp3File = tempFolder.newFile(name)
        val frameSize = 417
        val singleFrame = ByteArray(frameSize)
        // Sync word + MPEG1 Layer3 128kbps 44100Hz Stereo: FF FB 90 00
        singleFrame[0] = 0xFF.toByte()
        singleFrame[1] = 0xFB.toByte()
        singleFrame[2] = 0x90.toByte()
        singleFrame[3] = 0x00.toByte()

        mp3File.outputStream().use { out ->
            repeat(12) {
                out.write(singleFrame)
            }
        }
        return mp3File
    }

    @Test
    fun `writeTags upgrades ISO-8859-1 ID3v23 frames to UTF-16 removes ID3v1 and preserves special Unicode characters`() {
        val file = createMinimalValidMp3("legacy_iso88591.mp3")

        // Simula un file MP3 legacy con ID3v2.3 forzato a ISO-8859-1 + ID3v1 corrotto
        TagOptionSingleton.getInstance().id3v23DefaultTextEncoding = TextEncoding.ISO_8859_1
        TagOptionSingleton.getInstance().isResetTextEncodingForExistingFrames = false

        val initialMp3 = AudioFileIO.read(file) as MP3File
        val legacyV23 = ID3v23Tag().apply {
            setField(FieldKey.TITLE, "Broken ? Title")
            setField(FieldKey.ARTIST, "Old Artist")
            setField(FieldKey.ALBUM, "Old Album")
        }
        val legacyV1 = ID3v11Tag().apply {
            setTitle("Corrupted ID3v1")
            setArtist("Bad V1")
        }
        initialMp3.iD3v2Tag = legacyV23
        initialMp3.iD3v1Tag = legacyV1
        initialMp3.commit()

        val unicodeTitle = "Can’t Stop — Perché l’estate è qui… (feat. Żółć & 東京)"
        val unicodeArtist = "Tyler, The Creator feat. Guè & Кириллица"
        val unicodeAlbum = "L’Alba — Edizione Speciale €"
        val unicodeLyrics = "[00:05.00] Non c’è più tempo — perché…\n[00:10.00] 音楽 e parole"

        val metadata = EnrichedMetadata(
            title = unicodeTitle,
            artist = unicodeArtist,
            album = unicodeAlbum,
            albumArtist = "Tyler, The Creator",
            releaseYear = 2026,
            trackNumber = 3,
            discNumber = 1,
            genre = "Hip-Hop / R&B",
            syncedLyrics = unicodeLyrics
        )

        val ok = AudioTagWriter.writeTags(
            file = file,
            metadata = metadata,
            options = EnricherOptions(embedCover = false, includeLyrics = true)
        )
        assertTrue(ok)

        val reloaded = AudioFileIO.read(file) as MP3File
        assertFalse("Il tag legacy ID3v1 deve essere rimosso", reloaded.hasID3v1Tag())

        val reloadedTag = reloaded.iD3v2Tag
        assertEquals(unicodeTitle, reloadedTag.getFirst(FieldKey.TITLE))
        assertEquals(unicodeArtist, reloadedTag.getFirst(FieldKey.ARTIST))
        assertEquals(unicodeAlbum, reloadedTag.getFirst(FieldKey.ALBUM))
        assertEquals("Tyler, The Creator", reloadedTag.getFirst(FieldKey.ALBUM_ARTIST))
        assertEquals(unicodeLyrics, reloadedTag.getFirst(FieldKey.LYRICS))
    }

    @Test
    fun `writeTags preserves full UTF-8 Unicode on ID3v24 tags`() {
        val file = createMinimalValidMp3("modern_id3v24.mp3")
        val initialMp3 = AudioFileIO.read(file) as MP3File
        initialMp3.iD3v2Tag = ID3v24Tag().apply {
            setField(FieldKey.TITLE, "Initial")
        }
        initialMp3.commit()

        val unicodeTitle = "Don’t Worry – È già domani ★"
        val metadata = EnrichedMetadata(
            title = unicodeTitle,
            artist = "Artista Èlite",
            album = "Città d’Oro",
            albumArtist = "Artista Èlite"
        )

        val ok = AudioTagWriter.writeTags(
            file = file,
            metadata = metadata,
            options = EnricherOptions(embedCover = false, includeLyrics = false)
        )
        assertTrue(ok)

        val reloaded = AudioFileIO.read(file) as MP3File
        assertEquals(unicodeTitle, reloaded.iD3v2Tag.getFirst(FieldKey.TITLE))
        assertEquals("Artista Èlite", reloaded.iD3v2Tag.getFirst(FieldKey.ARTIST))
        assertEquals("Città d’Oro", reloaded.iD3v2Tag.getFirst(FieldKey.ALBUM))
    }
}
