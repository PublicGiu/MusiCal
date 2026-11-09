package com.example.musicplayer.data.tagger.writer

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.tagger.model.EnrichedMetadata
import com.example.musicplayer.data.tagger.repository.EnricherOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Gestisce la pipeline di scrittura su disco nel rispetto delle regole di Scoped Storage (Android 16).
 * Supporta operazioni singole e batch tramite MediaStore.createWriteRequest.
 */
class StorageWriteManager(private val context: Context) {

    /**
     * Prepara il file temporaneo in cache applicando i nuovi tag e copertina.
     */
    suspend fun prepareTrackInCache(
        track: AudioTrack,
        metadata: EnrichedMetadata,
        options: EnricherOptions
    ): File? = withContext(Dispatchers.IO) {
        try {
            val extension = track.displayName.substringAfterLast('.', "mp3")
            val tempFile = File(context.cacheDir, "tag_${track.id}_${System.currentTimeMillis()}.$extension")

            context.contentResolver.openInputStream(track.uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            val success = AudioTagWriter.writeTags(tempFile, metadata, options)
            if (success) {
                tempFile
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Verifica se per la lista di URI è necessaria la richiesta di autorizzazione tramite MediaStore.
     * Restituisce un IntentSender se occorre il consenso nativo di Android 16, oppure null se la scrittura è già permessa.
     */
    fun createWriteRequestIntentSender(uris: List<Uri>): IntentSender? {
        return try {
            MediaStore.createWriteRequest(context.contentResolver, uris).intentSender
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Crea un IntentSender per la richiesta di eliminazione definitiva di file tramite MediaStore (Android 16).
     */
    fun createDeleteRequestIntentSender(uris: List<Uri>): IntentSender? {
        return try {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sovrascrive il file audio originale con il file preparato in cache,
     * aggiorna il database MediaStore e salva i testi .lrc localmente.
     */
    suspend fun commitTrackWrite(
        track: AudioTrack,
        preparedFile: File,
        metadata: EnrichedMetadata
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Scrittura fisica sullo stream del MediaStore
            context.contentResolver.openOutputStream(track.uri, "rwt")?.use { output ->
                FileInputStream(preparedFile).use { input ->
                    input.copyTo(output)
                }
                output.flush()
            } ?: return@withContext false

            // 2. Salvataggio locale del file .lrc sincronizzato nella memoria privata dell'app
            if (!metadata.syncedLyrics.isNullOrBlank()) {
                saveLyricsLocally(track.id, metadata.syncedLyrics)
            } else if (metadata.isInstrumental) {
                saveLyricsLocally(track.id, "[instrumental:true]\n")
            }

            // 3. Aggiornamento esplicito delle colonne nel database MediaStore
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, metadata.title)
                    put(MediaStore.Audio.Media.ARTIST, metadata.artist)
                    put(MediaStore.Audio.Media.ALBUM, metadata.album)
                    put(MediaStore.Audio.Media.ALBUM_ARTIST, metadata.albumArtist)
                    val mediaStoreTrack = if ((metadata.discNumber ?: 1) > 1 && (metadata.trackNumber ?: 0) > 0) {
                        (metadata.discNumber!! * 1000) + metadata.trackNumber!!
                    } else {
                        metadata.trackNumber
                    }
                    mediaStoreTrack?.let { put(MediaStore.Audio.Media.TRACK, it) }
                    metadata.discNumber?.let { put(MediaStore.Audio.Media.DISC_NUMBER, it) }
                    metadata.releaseYear?.let { put(MediaStore.Audio.Media.YEAR, it) }
                }
                context.contentResolver.update(track.uri, values, null, null)
            } catch (_: Exception) {
                // Se l'aggiornamento diretto dei campi DB non è supportato da qualche colonna, si affida al MediaScanner
            }

            // 4. Notifica e attesa completamento scansione MediaScannerConnection sul file effettivo nello storage
            try {
                val externalDir = android.os.Environment.getExternalStorageDirectory()
                val physicalFile = if (track.relativePath.isNotBlank() && track.displayName.isNotBlank()) {
                    File(externalDir, "${track.relativePath}${track.displayName}")
                } else null

                val targetPath = physicalFile?.takeIf { it.exists() }?.absolutePath
                if (targetPath != null) {
                    withTimeoutOrNull(3000L) {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(targetPath),
                                null
                            ) { _, _ ->
                                if (continuation.isActive) {
                                    continuation.resume(Unit)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            preparedFile.delete()
        }
    }

    /**
     * Memorizza i testi sincronizzati (.lrc) o semplici nella directory filesDir/lyrics/
     */
    fun saveLyricsLocally(trackId: Long, lyricsContent: String) {
        try {
            val lyricsDir = File(context.filesDir, "lyrics").apply { mkdirs() }
            val lrcFile = File(lyricsDir, "$trackId.lrc")
            lrcFile.writeText(lyricsContent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Recupera il testo sincronizzato salvato localmente per una traccia (se presente).
     */
    fun loadLocalLyrics(trackId: Long): String? {
        return try {
            val lrcFile = File(context.filesDir, "lyrics/$trackId.lrc")
            if (lrcFile.exists() && lrcFile.length() > 0) {
                lrcFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Converte e ridimensiona un'immagine selezionata da URI a un array di byte JPEG di alta qualità (max 1200x1200).
 * Ottimizzato per l'incorporamento pulito nei tag ID3/Vorbis senza eccedere i limiti di memoria.
 */
fun loadResizedJpegBytesFromUri(context: Context, uri: Uri, maxDimension: Int = 1200): ByteArray? {
    return try {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { s ->
                val exif = ExifInterface(s)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            val original = BitmapFactory.decodeStream(stream) ?: return null
            val rotated = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> {
                    val m = android.graphics.Matrix().apply { postRotate(90f) }
                    Bitmap.createBitmap(original, 0, 0, original.width, original.height, m, true).also {
                        if (it != original) original.recycle()
                    }
                }
                ExifInterface.ORIENTATION_ROTATE_180 -> {
                    val m = android.graphics.Matrix().apply { postRotate(180f) }
                    Bitmap.createBitmap(original, 0, 0, original.width, original.height, m, true).also {
                        if (it != original) original.recycle()
                    }
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    val m = android.graphics.Matrix().apply { postRotate(270f) }
                    Bitmap.createBitmap(original, 0, 0, original.width, original.height, m, true).also {
                        if (it != original) original.recycle()
                    }
                }
                else -> original
            }

            val width = rotated.width
            val height = rotated.height
            val scaled = if (width > maxDimension || height > maxDimension) {
                val ratio = width.toFloat() / height.toFloat()
                val (newW, newH) = if (ratio > 1f) {
                    maxDimension to (maxDimension / ratio).toInt()
                } else {
                    (maxDimension * ratio).toInt() to maxDimension
                }
                Bitmap.createScaledBitmap(rotated, newW.coerceAtLeast(1), newH.coerceAtLeast(1), true).also {
                    if (it != rotated) rotated.recycle()
                }
            } else {
                rotated
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 92, baos)
            scaled.recycle()
            baos.toByteArray()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
