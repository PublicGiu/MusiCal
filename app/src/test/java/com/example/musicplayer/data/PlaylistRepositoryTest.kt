package com.example.musicplayer.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class PlaylistRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository(): PlaylistRepository {
        val mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(tempFolder.root)
        return PlaylistRepository(mockContext)
    }

    @Test
    fun testLoadPlaylistsReturnsFavoritesWhenFileNotExists() {
        val repository = createRepository()
        val list = repository.loadPlaylists()
        assertEquals(1, list.size)
        assertEquals(FAVORITES_PLAYLIST_ID, list[0].id)
        assertEquals(FAVORITES_PLAYLIST_NAME, list[0].name)
        assertTrue(list[0].trackIds.isEmpty())
        assertTrue(list[0].isFavorites)
    }

    @Test
    fun testCreateAndLoadPlaylist() {
        val repository = createRepository()
        val playlist = repository.createPlaylist("I Miei Preferiti")

        assertEquals("I Miei Preferiti", playlist.name)
        assertTrue(playlist.id.isNotBlank())
        assertTrue(playlist.trackIds.isEmpty())

        val all = repository.loadPlaylists()
        assertEquals(2, all.size)
        assertEquals(FAVORITES_PLAYLIST_ID, all[0].id)
        assertEquals("I Miei Preferiti", all[1].name)
    }

    @Test
    fun testAddTrackToPlaylistAndPreventDuplicates() {
        val repository = createRepository()
        val playlist = repository.createPlaylist("Rock Classics")

        val addedFirst = repository.addTrackToPlaylist(playlist.id, 101L)
        assertTrue(addedFirst)

        val afterFirstAdd = repository.loadPlaylists().first { it.id == playlist.id }
        assertEquals(listOf(101L), afterFirstAdd.trackIds)

        // Tentativo di aggiungere lo stesso brano duplicato
        val addedSecond = repository.addTrackToPlaylist(playlist.id, 101L)
        assertFalse(addedSecond)

        // Aggiunta di un secondo brano differente
        val addedOther = repository.addTrackToPlaylist(playlist.id, 202L)
        assertTrue(addedOther)

        val finalPlaylist = repository.loadPlaylists().first { it.id == playlist.id }
        assertEquals(listOf(101L, 202L), finalPlaylist.trackIds)
    }

    @Test
    fun testRemoveTrackFromPlaylist() {
        val repository = createRepository()
        val playlist = repository.createPlaylist("Workout")
        repository.addTrackToPlaylist(playlist.id, 1L)
        repository.addTrackToPlaylist(playlist.id, 2L)

        repository.removeTrackFromPlaylist(playlist.id, 1L)
        val updated = repository.loadPlaylists().first { it.id == playlist.id }
        assertEquals(listOf(2L), updated.trackIds)
    }

    @Test
    fun testDeletePlaylist() {
        val repository = createRepository()
        val p1 = repository.createPlaylist("Playlist 1")
        val p2 = repository.createPlaylist("Playlist 2")

        assertEquals(3, repository.loadPlaylists().size)

        val deleted = repository.deletePlaylist(p1.id)
        assertTrue(deleted)
        val remaining = repository.loadPlaylists()
        assertEquals(2, remaining.size)
        assertEquals(FAVORITES_PLAYLIST_ID, remaining[0].id)
        assertEquals(p2.id, remaining[1].id)
    }

    @Test
    fun testDeleteFavoritesPlaylistReturnsFalseAndIsNoOp() {
        val repository = createRepository()
        val deleted = repository.deletePlaylist(FAVORITES_PLAYLIST_ID)
        assertFalse(deleted)
        val all = repository.loadPlaylists()
        assertEquals(1, all.size)
        assertEquals(FAVORITES_PLAYLIST_ID, all[0].id)
    }

    @Test
    fun testFavoritesPlaylistAutoCreatedWhenFileContainsOnlyCustomPlaylists() {
        val repository = createRepository()
        // Save file with only custom playlists without favorites
        val custom = Playlist(id = "custom_1", name = "Custom Only", trackIds = listOf(10L))
        repository.savePlaylists(listOf(custom))

        val loaded = repository.loadPlaylists()
        assertEquals(2, loaded.size)
        assertEquals(FAVORITES_PLAYLIST_ID, loaded[0].id)
        assertEquals("custom_1", loaded[1].id)
        assertEquals(listOf(10L), loaded[1].trackIds)
    }

    @Test
    fun testAddAndRemoveTrackFromFavoritesPlaylist() {
        val repository = createRepository()
        val added = repository.addTrackToPlaylist(FAVORITES_PLAYLIST_ID, 500L)
        assertTrue(added)

        val afterAdd = repository.loadPlaylists().first { it.id == FAVORITES_PLAYLIST_ID }
        assertEquals(listOf(500L), afterAdd.trackIds)

        repository.removeTrackFromPlaylist(FAVORITES_PLAYLIST_ID, 500L)
        val afterRemove = repository.loadPlaylists().first { it.id == FAVORITES_PLAYLIST_ID }
        assertTrue(afterRemove.trackIds.isEmpty())
    }

    @Test
    fun testCustomPlaylistCoverPersistenceAndRemoval() {
        val repository = createRepository()
        val created = repository.createPlaylist("Chill Vibes", "file:///storage/cover1.jpg")
        assertEquals("file:///storage/cover1.jpg", created.coverUri)

        val loadedAfterCreate = repository.loadPlaylists().first { it.id == created.id }
        assertEquals("file:///storage/cover1.jpg", loadedAfterCreate.coverUri)
        assertEquals("file:///storage/cover1.jpg", loadedAfterCreate.effectiveCoverUri)

        val updated = repository.updatePlaylistCoverUri(created.id, "file:///storage/cover2.jpg")
        assertTrue(updated)
        val loadedAfterUpdate = repository.loadPlaylists().first { it.id == created.id }
        assertEquals("file:///storage/cover2.jpg", loadedAfterUpdate.coverUri)

        val removed = repository.updatePlaylistCoverUri(created.id, null)
        assertTrue(removed)
        val loadedAfterRemove = repository.loadPlaylists().first { it.id == created.id }
        assertEquals(null, loadedAfterRemove.coverUri)
    }

    @Test
    fun testFavoritesPlaylistCoverCannotBeModified() {
        val repository = createRepository()
        val updated = repository.updatePlaylistCoverUri(FAVORITES_PLAYLIST_ID, "file:///storage/hacked.jpg")
        assertFalse(updated)

        val fav = repository.loadPlaylists().first { it.id == FAVORITES_PLAYLIST_ID }
        assertEquals(null, fav.coverUri)
        assertEquals(null, fav.effectiveCoverUri)
    }
}
