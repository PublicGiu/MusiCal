package com.example.musicplayer.data.tagger.writer

import android.graphics.BitmapFactory
import com.example.musicplayer.data.tagger.model.EnrichedMetadata
import com.example.musicplayer.data.tagger.repository.EnricherOptions
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v22Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.AbstractFrameBodyTextInfo
import org.jaudiotagger.tag.id3.framebody.FrameBodyCOMM
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT
import org.jaudiotagger.tag.id3.valuepair.TextEncoding
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.File

/**
 * Gestisce la scrittura fisica dei metadati e dell'artwork all'interno dei file audio.
 * Supporta MP3 (ID3v2 Unicode UTF-16/UTF-8), FLAC (Vorbis Comments + Picture Block), M4A/MP4 (atomi iTunes) e OGG.
 */
object AudioTagWriter {

    init {
        configureUnicodeTagOptions()
    }

    /**
     * Configura jaudiotagger per usare sempre codifiche Unicode reali (UTF-16 per ID3v2.3, UTF-8 per ID3v2.4)
     * anziché ISO-8859-1 di default, forzando la ricodifica anche sui frame preesistenti.
     */
    internal fun configureUnicodeTagOptions() {
        TagOptionSingleton.getInstance().apply {
            id3v23DefaultTextEncoding = TextEncoding.UTF_16
            id3v24DefaultTextEncoding = TextEncoding.UTF_8
            isResetTextEncodingForExistingFrames = true
        }
    }

    private fun replaceFieldCleanly(tag: Tag, key: FieldKey, value: String) {
        try {
            tag.deleteField(key)
        } catch (_: Exception) {}
        tag.setField(key, value)
    }

    /**
     * Forza tutti i frame testuali di un tag ID3v2 alla codifica Unicode appropriata
     * (UTF-16 per ID3v2.3, UTF-8 per ID3v2.4), prevenendo caratteri sostituiti ('?' o simboli corrotti).
     */
    private fun enforceUnicodeOnId3v2Frames(tag: AbstractID3v2Tag) {
        val targetEncoding: Byte = if (tag is ID3v24Tag) TextEncoding.UTF_8 else TextEncoding.UTF_16
        val fieldsIterator = tag.fields
        while (fieldsIterator.hasNext()) {
            val field = fieldsIterator.next()
            if (field is AbstractID3v2Frame) {
                when (val body = field.body) {
                    is AbstractFrameBodyTextInfo -> body.textEncoding = targetEncoding
                    is FrameBodyUSLT -> body.textEncoding = targetEncoding
                    is FrameBodyCOMM -> body.textEncoding = targetEncoding
                }
            }
        }
    }

    /**
     * Scrive i campi di metadati e incorpora l'eventuale copertina nel file audio specificato.
     */
    fun writeTags(
        file: File,
        metadata: EnrichedMetadata,
        options: EnricherOptions = EnricherOptions()
    ): Boolean {
        return try {
            configureUnicodeTagOptions()
            val audioFile = AudioFileIO.read(file)
            val tag = if (audioFile is MP3File) {
                val existingV2 = audioFile.iD3v2Tag
                val activeV2: AbstractID3v2Tag = when {
                    existingV2 is ID3v22Tag -> ID3v23Tag(existingV2).also { audioFile.iD3v2Tag = it }
                    existingV2 != null -> existingV2
                    else -> ID3v23Tag().also { audioFile.iD3v2Tag = it }
                }
                // Rimuove eventuali tag legacy ID3v1 (limitati a 30 byte ISO-8859-1) che causano simboli corrotti o troncamenti
                if (audioFile.hasID3v1Tag()) {
                    audioFile.iD3v1Tag = null
                }
                activeV2
            } else {
                audioFile.tagOrCreateAndSetDefault ?: audioFile.createDefaultTag().also { audioFile.tag = it }
            }

            // 1. Titolo traccia (TIT2 / Vorbis TITLE / MP4 ©nam)
            if (metadata.title.isNotBlank()) {
                replaceFieldCleanly(tag, FieldKey.TITLE, metadata.title)
            }

            // 2. Artista specifico della traccia (TPE1 / Vorbis ARTIST / MP4 ©ART)
            if (metadata.artist.isNotBlank()) {
                replaceFieldCleanly(tag, FieldKey.ARTIST, metadata.artist)
            }

            // 3. Titolo dell'album (TALB / Vorbis ALBUM / MP4 ©alb)
            if (metadata.album.isNotBlank()) {
                replaceFieldCleanly(tag, FieldKey.ALBUM, metadata.album)
            }

            // 4. Artista principale dell'album (TPE2 / Vorbis ALBUMARTIST / MP4 aART)
            // Risolve la frammentazione degli album in Android MediaStore!
            if (metadata.albumArtist.isNotBlank()) {
                try {
                    replaceFieldCleanly(tag, FieldKey.ALBUM_ARTIST, metadata.albumArtist)
                } catch (_: Exception) {}
            }

            // 5. Anno di uscita (TYER / TDRC / Vorbis DATE / MP4 ©day)
            metadata.releaseYear?.let { year ->
                if (year > 0) {
                    try {
                        replaceFieldCleanly(tag, FieldKey.YEAR, year.toString())
                    } catch (_: Exception) {}
                }
            }

            // 6. Numero di traccia (TRCK / Vorbis TRACKNUMBER / MP4 trkn)
            metadata.trackNumber?.let { trackNum ->
                if (trackNum > 0) {
                    try {
                        replaceFieldCleanly(tag, FieldKey.TRACK, trackNum.toString())
                    } catch (_: Exception) {}
                }
            }

            // 7. Numero di disco (TPOS / Vorbis DISCNUMBER / MP4 disk)
            metadata.discNumber?.let { discNum ->
                if (discNum > 0) {
                    try {
                        replaceFieldCleanly(tag, FieldKey.DISC_NO, discNum.toString())
                    } catch (_: Exception) {}
                }
            }

            // 8. Genere (TCON / Vorbis GENRE / MP4 ©gen)
            if (!metadata.genre.isNullOrBlank()) {
                try {
                    replaceFieldCleanly(tag, FieldKey.GENRE, metadata.genre)
                } catch (_: Exception) {}
            }

            // 9. Testi traccia (USLT / Vorbis LYRICS / MP4 ©lyr)
            val lyricsToWrite = metadata.syncedLyrics ?: metadata.plainLyrics
            if (!lyricsToWrite.isNullOrBlank() && options.includeLyrics) {
                try {
                    replaceFieldCleanly(tag, FieldKey.LYRICS, lyricsToWrite)
                } catch (_: Exception) {}
            }

            if (tag is AbstractID3v2Tag) {
                enforceUnicodeOnId3v2Frames(tag)
            }

            // 10. Incorporamento copertina JPEG/PNG HD (APIC / Picture Block FLAC / MP4 covr / Vorbis METADATA_BLOCK_PICTURE)
            if (options.embedCover && metadata.coverBytes != null && metadata.coverBytes.isNotEmpty()) {
                try {
                    val (mimeType, width, height) = inspectImageHeader(metadata.coverBytes)
                    val artwork = object : AndroidArtwork() {
                        // Evita UnsupportedOperationException in FlacTag e VorbisCommentTag (che altrimenti invocano ImageIO)
                        override fun setImageFromData(): Boolean = true
                    }.apply {
                        binaryData = metadata.coverBytes
                        this.mimeType = mimeType
                        description = "Cover (front)"
                        pictureType = 3 // 3 = Front Cover
                        this.width = width
                        this.height = height
                    }

                    // Crea prima il nuovo campo artwork: elimina il precedente solo se la creazione ha avuto successo
                    val artworkField = tag.createField(artwork)
                    if (artworkField != null) {
                        try {
                            tag.deleteArtworkField()
                        } catch (_: Exception) {}
                        tag.setField(artworkField)
                    }
                } catch (_: Exception) {
                    // Preserva la scrittura degli altri tag e la copertina preesistente in caso di anomalia sull'artwork
                }
            }

            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Estrae i byte della copertina già incorporata nel file audio, se presente.
     */
    fun extractEmbeddedCoverBytes(file: File): ByteArray? {
        return try {
            val audioFile = AudioFileIO.read(file)
            audioFile.tag?.firstArtwork?.binaryData?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Determina MIME type, larghezza e altezza dell'immagine dai magic bytes (JPEG/PNG) o tramite BitmapFactory,
     * con fallback sicuro a ("image/jpeg", 1000, 1000).
     */
    internal fun inspectImageHeader(bytes: ByteArray): Triple<String, Int, Int> {
        val isPng = bytes.size >= 24 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()

        val mime = if (isPng) "image/png" else "image/jpeg"

        if (isPng) {
            val w = ((bytes[16].toInt() and 0xFF) shl 24) or
                ((bytes[17].toInt() and 0xFF) shl 16) or
                ((bytes[18].toInt() and 0xFF) shl 8) or
                (bytes[19].toInt() and 0xFF)
            val h = ((bytes[20].toInt() and 0xFF) shl 24) or
                ((bytes[21].toInt() and 0xFF) shl 16) or
                ((bytes[22].toInt() and 0xFF) shl 8) or
                (bytes[23].toInt() and 0xFF)
            if (w > 0 && h > 0) {
                return Triple(mime, w, h)
            }
        }

        parseJpegDimensions(bytes)?.let { (w, h) ->
            if (w > 0 && h > 0) {
                return Triple(mime, w, h)
            }
        }

        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                val detectedMime = opts.outMimeType?.takeIf { it.isNotBlank() } ?: mime
                return Triple(detectedMime, opts.outWidth, opts.outHeight)
            }
        } catch (_: Exception) {}

        return Triple(mime, 1000, 1000)
    }

    private fun parseJpegDimensions(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        var i = 2
        while (i + 8 < bytes.size) {
            if (bytes[i] != 0xFF.toByte()) {
                i++
                continue
            }
            while (i < bytes.size && bytes[i] == 0xFF.toByte()) {
                i++
            }
            if (i >= bytes.size) break
            val marker = bytes[i].toInt() and 0xFF
            i++

            if (marker == 0xD8 || marker == 0xD9 || marker in 0xD0..0xD7 || marker == 0x01) {
                continue
            }
            if (i + 1 >= bytes.size) break
            val segmentLength = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            if (segmentLength < 2 || i + segmentLength > bytes.size) break

            // SOF0..SOF3, SOF5..SOF7, SOF9..SOF11, SOF13..SOF15
            if (marker in listOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)) {
                if (i + 6 < bytes.size) {
                    val height = ((bytes[i + 3].toInt() and 0xFF) shl 8) or (bytes[i + 4].toInt() and 0xFF)
                    val width = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
                    if (width > 0 && height > 0) {
                        return width to height
                    }
                }
            }
            i += segmentLength
        }
        return null
    }
}
