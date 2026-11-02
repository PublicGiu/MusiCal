package com.example.musicplayer.data.tagger.writer

import com.example.musicplayer.data.tagger.model.EnrichedMetadata
import com.example.musicplayer.data.tagger.repository.EnricherOptions
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.File

/**
 * Gestisce la scrittura fisica dei metadati e dell'artwork all'interno dei file audio.
 * Supporta MP3 (ID3v2), FLAC (Vorbis Comments), M4A/MP4 (atomi iTunes) e OGG.
 */
object AudioTagWriter {

    /**
     * Scrive i campi di metadati e incorpora l'eventuale copertina nel file audio specificato.
     */
    fun writeTags(
        file: File,
        metadata: EnrichedMetadata,
        options: EnricherOptions = EnricherOptions()
    ): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag ?: audioFile.createDefaultTag()

            // 1. Titolo traccia (TIT2 / Vorbis TITLE / MP4 ©nam)
            if (metadata.title.isNotBlank()) {
                tag.setField(FieldKey.TITLE, metadata.title)
            }

            // 2. Artista specifico della traccia (TPE1 / Vorbis ARTIST / MP4 ©ART)
            if (metadata.artist.isNotBlank()) {
                tag.setField(FieldKey.ARTIST, metadata.artist)
            }

            // 3. Titolo dell'album (TALB / Vorbis ALBUM / MP4 ©alb)
            if (metadata.album.isNotBlank()) {
                tag.setField(FieldKey.ALBUM, metadata.album)
            }

            // 4. Artista principale dell'album (TPE2 / Vorbis ALBUMARTIST / MP4 aART)
            // Risolve la frammentazione degli album in Android MediaStore!
            if (metadata.albumArtist.isNotBlank()) {
                tag.setField(FieldKey.ALBUM_ARTIST, metadata.albumArtist)
            }

            // 5. Anno di uscita (TYER / TDRC / Vorbis DATE / MP4 ©day)
            metadata.releaseYear?.let { year ->
                if (year > 0) {
                    tag.setField(FieldKey.YEAR, year.toString())
                }
            }

            // 6. Numero di traccia (TRCK / Vorbis TRACKNUMBER / MP4 trkn)
            metadata.trackNumber?.let { trackNum ->
                if (trackNum > 0) {
                    try {
                        tag.setField(FieldKey.TRACK, trackNum.toString())
                    } catch (_: Exception) {}
                }
            }

            // 7. Numero di disco (TPOS / Vorbis DISCNUMBER / MP4 disk)
            metadata.discNumber?.let { discNum ->
                if (discNum > 0) {
                    try {
                        tag.setField(FieldKey.DISC_NO, discNum.toString())
                    } catch (_: Exception) {}
                }
            }

            // 8. Genere (TCON / Vorbis GENRE / MP4 ©gen)
            if (!metadata.genre.isNullOrBlank()) {
                tag.setField(FieldKey.GENRE, metadata.genre)
            }

            // 9. Testi traccia (USLT / Vorbis LYRICS / MP4 ©lyr)
            val lyricsToWrite = metadata.syncedLyrics ?: metadata.plainLyrics
            if (!lyricsToWrite.isNullOrBlank() && options.includeLyrics) {
                try {
                    tag.setField(FieldKey.LYRICS, lyricsToWrite)
                } catch (_: Exception) {}
            }

            // 10. Incorporamento copertina JPEG HD (APIC / Picture Block / MP4 covr)
            if (options.embedCover && metadata.coverBytes != null && metadata.coverBytes.isNotEmpty()) {
                try {
                    tag.deleteArtworkField()
                    val artwork = AndroidArtwork()
                    artwork.binaryData = metadata.coverBytes
                    artwork.mimeType = "image/jpeg"
                    artwork.description = "Cover (front)"
                    artwork.pictureType = 3 // 3 = Front Cover
                    tag.setField(artwork)
                } catch (_: Exception) {
                    // Preserva la scrittura degli altri tag anche in caso di anomalia sull'artwork
                }
            }

            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
