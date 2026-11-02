package com.example.musicplayer.data.tagger.model

/**
 * Modello unificato contenente tutti i metadati arricchiti ricavati da Deezer, LRCLIB e Last.fm.
 */
data class EnrichedMetadata(
    val title: String,
    val artist: String,           // Artista specifico della traccia (incluso feat.)
    val album: String,            // Nome album (eventualmente normalizzato)
    val albumArtist: String,      // Artista principale dell'album (TPE2 / aART)
    val releaseYear: Int? = null, // Anno di rilascio (TYER / TDRC)
    val trackNumber: Int? = null, // Numero traccia (TRCK)
    val discNumber: Int? = null,  // Numero disco (TPOS)
    val genre: String? = null,    // Genere musicale
    val coverUrl: String? = null, // URL copertina HD (1000x1000 da Deezer cover_xl)
    val coverBytes: ByteArray? = null, // Byte JPEG scaricati per l'incorporamento
    val plainLyrics: String? = null,   // Testo semplice non sincronizzato
    val syncedLyrics: String? = null,  // Testo sincronizzato con timestamp [mm:ss.xx]
    val recordType: String? = null,    // Tipo di rilascio ("album", "ep", "single", "compile")
    val source: String = "Deezer + LRCLIB"
) {
    val hasSyncedLyrics: Boolean get() = !syncedLyrics.isNullOrBlank()
    val hasPlainLyrics: Boolean get() = !plainLyrics.isNullOrBlank()
    val hasCover: Boolean get() = coverBytes != null && coverBytes.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EnrichedMetadata

        if (title != other.title) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (albumArtist != other.albumArtist) return false
        if (releaseYear != other.releaseYear) return false
        if (trackNumber != other.trackNumber) return false
        if (discNumber != other.discNumber) return false
        if (genre != other.genre) return false
        if (coverUrl != other.coverUrl) return false
        if (coverBytes != null) {
            if (other.coverBytes == null) return false
            if (!coverBytes.contentEquals(other.coverBytes)) return false
        } else if (other.coverBytes != null) return false
        if (plainLyrics != other.plainLyrics) return false
        if (syncedLyrics != other.syncedLyrics) return false
        if (recordType != other.recordType) return false
        if (source != other.source) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + albumArtist.hashCode()
        result = 31 * result + (releaseYear ?: 0)
        result = 31 * result + (trackNumber ?: 0)
        result = 31 * result + (discNumber ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (coverUrl?.hashCode() ?: 0)
        result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
        result = 31 * result + (plainLyrics?.hashCode() ?: 0)
        result = 31 * result + (syncedLyrics?.hashCode() ?: 0)
        result = 31 * result + (recordType?.hashCode() ?: 0)
        result = 31 * result + source.hashCode()
        return result
    }
}
