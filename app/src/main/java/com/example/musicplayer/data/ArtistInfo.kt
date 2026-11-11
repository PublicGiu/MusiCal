package com.example.musicplayer.data

/**
 * Modello dati immutabile contenente le informazioni monografiche di un artista.
 *
 * @param name Nome dell'artista.
 * @param photoUrl URL della foto profilo promozionale autentica in alta risoluzione (es. Deezer o Wikipedia).
 * @param biography Estratto biografico enciclopedico (es. Wikipedia o Last.fm).
 * @param source Indicazione della sorgente informativa (es. "Deezer / Wikipedia", "Last.fm", "Personalizzata").
 * @param isCustomPhoto Indica se la foto profilo è stata impostata manualmente dall'utente.
 * @param isCustomBio Indica se la biografia è stata modificata o impostata manualmente dall'utente.
 * @param lastUpdated Timestamp di ultimo aggiornamento locale della cache.
 */
data class ArtistInfo(
    val name: String,
    val photoUrl: String? = null,
    val biography: String? = null,
    val source: String? = null,
    val isCustomPhoto: Boolean = false,
    val isCustomBio: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
