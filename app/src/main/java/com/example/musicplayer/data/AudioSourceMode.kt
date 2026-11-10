package com.example.musicplayer.data

import com.example.musicplayer.R

/**
 * Modalità di selezione delle directory di origine per la scansione dei file musicali.
 */
enum class AudioSourceMode(
    val label: String,
    val description: String,
    val labelResId: Int,
    val descriptionResId: Int
) {
    /**
     * Scansione intelligente (predefinita): analizza i file audio del dispositivo
     * filtrando automaticamente note vocali WhatsApp/Telegram, registrazioni audio del telefono,
     * suonerie, notifiche e clip audio brevissime (<15s) o lunghissime (>20min) prive di metadati musicali.
     */
    INTELLIGENT(
        label = "Scansione intelligente",
        description = "Filtra automaticamente note vocali WhatsApp, registrazioni telefoniche e suoni di sistema",
        labelResId = R.string.source_mode_intelligent_recommended,
        descriptionResId = R.string.source_mode_intelligent_desc
    ),

    /**
     * Considera esclusivamente i brani presenti nella cartella Music/ del dispositivo
     * e relative sottocartelle (comportamento originario).
     */
    MUSIC_ONLY(
        label = "Cartella Music",
        description = "Esclusivamente i file nella cartella Music/ e sottocartelle",
        labelResId = R.string.source_mode_music,
        descriptionResId = R.string.source_mode_music_desc
    ),

    /**
     * Scansiona tutti i file audio presenti nel dispositivo senza alcun filtro di directory.
     */
    ALL(
        label = "Tutto",
        description = "Tutti i file audio indicizzati nel dispositivo senza restrizioni di percorso",
        labelResId = R.string.source_mode_all,
        descriptionResId = R.string.source_mode_all_desc
    ),

    /**
     * Scansiona esclusivamente una o più directory personalizzate scelte dall'utente.
     */
    CUSTOM_FOLDERS(
        label = "Cartelle a scelta",
        description = "Solo i file all'interno delle cartelle specifiche scelte manualmente",
        labelResId = R.string.source_mode_custom,
        descriptionResId = R.string.source_mode_custom_desc
    )
}
