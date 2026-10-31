package com.example.musicplayer.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Executors

/**
 * BitmapLoader personalizzato per AndroidX Media3 su Android 16 (API 36).
 *
 * Risolve la copertina dell'album per la notifica MediaStyle di sistema e la schermata di blocco
 * utilizzando l'API moderna ContentResolver.loadThumbnail, con fallback su openInputStream
 * ed estrazione diretta di frame ID3/FLAC tramite MediaMetadataRetriever.
 */
@OptIn(UnstableApi::class)
class Media3BitmapLoader(private val context: Context) : BitmapLoader {

    private val executor = Executors.newSingleThreadExecutor()

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        executor.execute {
            try {
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (bitmap != null) {
                    future.set(bitmap)
                } else {
                    future.setException(IllegalArgumentException("Impossibile decodificare bitmap da byte array"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        executor.execute {
            try {
                val bitmap = loadBitmapFromUri(context, uri)
                if (bitmap != null) {
                    future.set(bitmap)
                } else {
                    future.setException(IllegalArgumentException("Nessuna copertina trovata per $uri"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        if (metadata.artworkData != null) {
            return decodeBitmap(metadata.artworkData!!)
        }
        val uri = metadata.artworkUri ?: return null
        return loadBitmap(uri)
    }

    companion object {
        /**
         * Tenta di caricare il bitmap della copertina con strategie progressive su Android 16.
         */
        fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
            val contentResolver = context.contentResolver

            // 1. API moderna Android 10+ (API 29..36) per MediaStore
            try {
                return contentResolver.loadThumbnail(uri, Size(512, 512), null)
            } catch (_: Exception) {
            }

            // 2. Stream diretto (supporta file://, content:// legacy e risorse)
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) return bitmap
                }
            } catch (_: Exception) {
            }

            // 3. Fallback tramite MediaMetadataRetriever su file audio
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, uri)
                    val rawPicture = retriever.embeddedPicture
                    if (rawPicture != null) {
                        val bitmap = BitmapFactory.decodeByteArray(rawPicture, 0, rawPicture.size)
                        if (bitmap != null) return bitmap
                    }
                }
            } catch (_: Exception) {
            }

            return null
        }
    }
}
