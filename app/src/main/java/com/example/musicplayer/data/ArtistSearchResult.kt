package com.example.musicplayer.data

import android.net.Uri

/**
 * Risultato di ricerca per un artista presente nella libreria locale.
 *
 * @param name Nome dell'artista.
 * @param trackCount Numero totale di brani presenti nella libreria appartenenti all'artista.
 * @param albumCount Numero totale di album dell'artista.
 * @param photoUrl URL della foto profilo remota (se presente in cache).
 * @param fallbackCoverUri URI locale di fallback della copertina di un brano dell'artista.
 */
data class ArtistSearchResult(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val photoUrl: String? = null,
    val fallbackCoverUri: Uri? = null
)
