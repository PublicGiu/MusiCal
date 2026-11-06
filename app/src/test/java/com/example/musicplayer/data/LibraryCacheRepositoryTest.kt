package com.example.musicplayer.data

import android.content.Context
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class LibraryCacheRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun mockUri(uriStr: String): Uri {
        val mockUri = mock(Uri::class.java)
        `when`(mockUri.toString()).thenReturn(uriStr)
        return mockUri
    }

    private fun createRepository(): LibraryCacheRepository {
        val mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(tempFolder.root)
        return LibraryCacheRepository(
            context = mockContext,
            uriParser = { str -> mockUri(str) }
        )
    }

    private fun createTrack(
        id: Long,
        title: String,
        albumId: Long = 10L,
        album: String = "Test Album",
        artist: String = "Test Artist",
        albumArtist: String = "Test Artist",
        trackNumber: Int = 1,
        discNumber: Int = 1,
        duration: Long = 180000L,
        relativePath: String = "Music/"
    ): AudioTrack {
        return AudioTrack(
            id = id,
            title = title,
            artist = artist,
            albumArtist = albumArtist,
            duration = duration,
            uri = mockUri("content://media/external/audio/media/$id"),
            albumId = albumId,
            album = album,
            albumArtUri = mockUri("content://media/external/audio/albumart/$albumId"),
            dateAdded = 1700000000L,
            genre = "Rock",
            trackNumber = trackNumber,
            discNumber = discNumber,
            displayName = "$title.mp3",
            relativePath = relativePath
        )
    }

    @Test
    fun testLoadCacheReturnsNullWhenFileNotExists() {
        val repository = createRepository()
        val result = repository.loadCache(AudioSourceMode.INTELLIGENT, emptySet())
        assertNull(result)
    }

    @Test
    fun testSaveAndLoadCacheSuccess() {
        val repository = createRepository()
        val track1 = createTrack(1L, "Song 1", trackNumber = 1)
        val track2 = createTrack(2L, "Song 2", trackNumber = 2)
        val tracks = listOf(track1, track2)

        val album = Album(
            id = 10L,
            title = "Test Album",
            artist = "Test Artist",
            albumArtUri = mockUri("content://media/external/audio/albumart/10"),
            tracks = tracks
        )
        val albums = listOf(album)

        repository.saveCache(
            sourceMode = AudioSourceMode.INTELLIGENT,
            customFolders = emptySet(),
            tracks = tracks,
            albums = albums
        )

        val loaded = repository.loadCache(AudioSourceMode.INTELLIGENT, emptySet())
        assertNotNull(loaded)

        val (loadedTracks, loadedAlbums) = loaded!!
        assertEquals(2, loadedTracks.size)
        assertEquals("Song 1", loadedTracks[0].title)
        assertEquals("Song 2", loadedTracks[1].title)
        assertEquals(1, loadedTracks[0].trackNumber)
        assertEquals(2, loadedTracks[1].trackNumber)
        assertEquals("Rock", loadedTracks[0].genre)
        assertEquals("Music/", loadedTracks[0].relativePath)
        assertEquals("content://media/external/audio/media/1", loadedTracks[0].uri.toString())

        assertEquals(1, loadedAlbums.size)
        assertEquals(10L, loadedAlbums[0].id)
        assertEquals("Test Album", loadedAlbums[0].title)
        assertEquals(2, loadedAlbums[0].tracks.size)
        assertEquals("Song 1", loadedAlbums[0].tracks[0].title)
    }

    @Test
    fun testLoadCacheReturnsNullWhenSourceModeMismatch() {
        val repository = createRepository()
        val track = createTrack(1L, "Song 1")
        val album = Album(10L, "Album", "Artist", null, listOf(track))

        repository.saveCache(
            sourceMode = AudioSourceMode.INTELLIGENT,
            customFolders = emptySet(),
            tracks = listOf(track),
            albums = listOf(album)
        )

        // Richiesta con modalità diversa (MUSIC_ONLY): deve invalidare e restituire null
        val loaded = repository.loadCache(AudioSourceMode.MUSIC_ONLY, emptySet())
        assertNull(loaded)
    }

    @Test
    fun testLoadCacheReturnsNullWhenCustomFoldersMismatch() {
        val repository = createRepository()
        val track = createTrack(1L, "Song 1")
        val album = Album(10L, "Album", "Artist", null, listOf(track))

        repository.saveCache(
            sourceMode = AudioSourceMode.CUSTOM_FOLDERS,
            customFolders = setOf("Music/FolderA/"),
            tracks = listOf(track),
            albums = listOf(album)
        )

        // Cartelle diverse: deve restituire null
        val loaded = repository.loadCache(AudioSourceMode.CUSTOM_FOLDERS, setOf("Music/FolderB/"))
        assertNull(loaded)
    }

    @Test
    fun testClearCacheDeletesFile() {
        val repository = createRepository()
        val track = createTrack(1L, "Song 1")
        val album = Album(10L, "Album", "Artist", null, listOf(track))

        repository.saveCache(
            sourceMode = AudioSourceMode.INTELLIGENT,
            customFolders = emptySet(),
            tracks = listOf(track),
            albums = listOf(album)
        )

        val beforeClear = repository.loadCache(AudioSourceMode.INTELLIGENT, emptySet())
        assertNotNull(beforeClear)

        repository.clearCache()
        val afterClear = repository.loadCache(AudioSourceMode.INTELLIGENT, emptySet())
        assertNull(afterClear)
    }
}
