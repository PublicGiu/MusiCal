package com.example.musicplayer.data.tagger

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import org.jaudiotagger.tag.images.Artwork
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class AudioTagWriterTest {

    @Test
    fun testJaudiotaggerClassesAvailable() {
        val artwork: Artwork = AndroidArtwork()
        assertNotNull(artwork)
        artwork.binaryData = byteArrayOf(1, 2, 3)
        artwork.mimeType = "image/jpeg"
        assertNotNull(artwork.binaryData)
        assertNotNull(FieldKey.TITLE)
        assertNotNull(FieldKey.ARTIST)
        assertNotNull(FieldKey.ALBUM)
        assertNotNull(FieldKey.ALBUM_ARTIST)
        assertNotNull(FieldKey.YEAR)
        assertNotNull(FieldKey.TRACK)
        assertNotNull(FieldKey.DISC_NO)
        assertNotNull(FieldKey.GENRE)
        assertNotNull(FieldKey.LYRICS)
    }
}
