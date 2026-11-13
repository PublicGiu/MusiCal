package com.example.musicplayer.data.backup

import android.content.Context
import android.net.Uri
import com.example.musicplayer.data.AppLanguage
import com.example.musicplayer.data.AppPreferences
import com.example.musicplayer.data.AudioSourceMode
import com.example.musicplayer.data.AudioTrack
import com.example.musicplayer.data.FAVORITES_PLAYLIST_ID
import com.example.musicplayer.data.FAVORITES_PLAYLIST_NAME
import com.example.musicplayer.data.Playlist
import com.example.musicplayer.data.PlaylistRepository
import com.example.musicplayer.data.StatsRepository
import com.example.musicplayer.data.ThemeMode
import com.example.musicplayer.data.TrackStats
import com.example.musicplayer.data.lastfm.cache.PendingScrobbleDao
import com.example.musicplayer.data.lastfm.cache.PendingScrobbleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class BackupRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: AppPreferences
    private lateinit var playlistRepo: PlaylistRepository
    private lateinit var statsRepo: StatsRepository
    private lateinit var fakeDao: FakePendingScrobbleDao
    private lateinit var repository: BackupRepository

    private class FakePendingScrobbleDao : PendingScrobbleDao {
        val items = mutableListOf<PendingScrobbleEntity>()
        private var nextId = 1L

        override suspend fun insert(scrobble: PendingScrobbleEntity): Long {
            val assigned = scrobble.copy(id = nextId++)
            items.add(assigned)
            return assigned.id
        }

        override suspend fun getAllPendingAsc(): List<PendingScrobbleEntity> = items.toList()

        override fun getPendingCountFlow(): Flow<Int> = flowOf(items.size)

        override suspend fun getPendingCount(): Int = items.size

        override suspend fun deleteById(id: Long) {
            items.removeAll { it.id == id }
        }

        override suspend fun deleteByIds(ids: List<Long>) {
            items.removeAll { it.id in ids }
        }

        override suspend fun delete(entity: PendingScrobbleEntity) {
            items.remove(entity)
        }

        override suspend fun clearAll() {
            items.clear()
        }
    }

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(tempFolder.root)

        mockPrefs = mock(AppPreferences::class.java)
        `when`(mockPrefs.isAutoplayEnabled()).thenReturn(true)
        `when`(mockPrefs.getThemeMode()).thenReturn(ThemeMode.DARK)
        `when`(mockPrefs.getAppLanguage()).thenReturn(AppLanguage.ITALIAN)
        `when`(mockPrefs.isDynamicColorEnabled()).thenReturn(true)
        `when`(mockPrefs.getAudioSourceMode()).thenReturn(AudioSourceMode.INTELLIGENT)
        `when`(mockPrefs.getCustomFolders()).thenReturn(setOf("Music/Rock/"))
        `when`(mockPrefs.isEqualizerEnabled()).thenReturn(true)
        `when`(mockPrefs.getEqualizerPreset()).thenReturn(2)
        `when`(mockPrefs.getEqualizerBandLevels()).thenReturn("100,0,-100")
        `when`(mockPrefs.getBassBoostStrength()).thenReturn(400)
        `when`(mockPrefs.getVirtualizerStrength()).thenReturn(250)
        `when`(mockPrefs.getIgnoredDuplicateKeys()).thenReturn(setOf("key1"))

        playlistRepo = PlaylistRepository(mockContext)
        statsRepo = StatsRepository(mockContext)
        fakeDao = FakePendingScrobbleDao()

        repository = BackupRepository(
            context = mockContext,
            appPreferences = mockPrefs,
            playlistRepository = playlistRepo,
            statsRepository = statsRepo,
            pendingScrobbleDao = fakeDao
        )
    }

    private fun makeTrack(
        id: Long,
        title: String,
        artist: String,
        album: String = "Album",
        duration: Long = 200_000L,
        displayName: String = "$title.mp3",
        relativePath: String = "Music/"
    ) = AudioTrack(
        id = id,
        title = title,
        artist = artist,
        duration = duration,
        uri = mock(Uri::class.java),
        albumId = id * 10,
        album = album,
        albumArtUri = null,
        displayName = displayName,
        relativePath = relativePath
    )

    @Test
    fun exportBuildsValidPortableJsonWithoutCredentials() = runBlocking {
        val t1 = makeTrack(id = 101L, title = "Song One", artist = "Artist A", displayName = "song_one.flac", relativePath = "Music/A/")
        playlistRepo.savePlaylists(
            listOf(
                Playlist(FAVORITES_PLAYLIST_ID, FAVORITES_PLAYLIST_NAME, listOf(101L)),
                Playlist("pl_1", "Workout", listOf(101L))
            )
        )
        statsRepo.saveStats(mapOf(101L to TrackStats(101L, playCount = 7, lastPlayedTimestamp = 123456789L)))
        fakeDao.insert(PendingScrobbleEntity(artist = "Artist A", track = "Song One", album = "Album", timestamp = 1700000000L))

        val json = repository.buildBackupJson(listOf(t1))

        assertEquals(BackupRepository.BACKUP_VERSION, json.getInt("version"))
        assertTrue(json.has("playlists"))
        assertTrue(json.has("listeningStats"))
        assertTrue(json.has("pendingScrobbles"))
        assertTrue(json.has("preferences"))
        assertFalse(json.toString().contains("sessionKey"))
        assertFalse(json.toString().contains("sharedSecret"))

        val playlists = json.getJSONArray("playlists")
        assertEquals(2, playlists.length())
        val exportedTrack = playlists.getJSONObject(1).getJSONArray("tracks").getJSONObject(0)
        assertEquals("Music/A/", exportedTrack.getString("relativePath"))
        assertEquals("song_one.flac", exportedTrack.getString("displayName"))
        assertEquals("Song One", exportedTrack.getString("title"))
        assertEquals("Artist A", exportedTrack.getString("artist"))
        assertEquals(200_000L, exportedTrack.getLong("durationMs"))
    }

    @Test(expected = BackupException::class)
    fun importRejectsMissingVersion() = runBlocking {
        val invalid = JSONObject("""{"playlists": []}""")
        repository.importBackupJson(invalid, emptyList())
        Unit
    }

    @Test(expected = BackupException::class)
    fun importRejectsUnsupportedFutureVersion() = runBlocking {
        val future = JSONObject("""{"version": 99, "playlists": []}""")
        repository.importBackupJson(future, emptyList())
        Unit
    }

    @Test
    fun importMatchesByPathThenFallbackMetadataAndAvoidsDuplicates() = runBlocking {
        // Esporta con ID vecchi (101, 102, 103)
        val oldT1 = makeTrack(101L, "Track Alpha", "Band X", duration = 180_000L, displayName = "alpha.mp3", relativePath = "Music/X/")
        val oldT2 = makeTrack(102L, "Track Beta", "Band Y", duration = 240_000L, displayName = "beta_old.mp3", relativePath = "OldDir/")
        val oldT3 = makeTrack(103L, "Missing Song", "Ghost Band", duration = 150_000L, displayName = "missing.mp3", relativePath = "Music/Ghost/")

        playlistRepo.savePlaylists(
            listOf(
                Playlist(FAVORITES_PLAYLIST_ID, FAVORITES_PLAYLIST_NAME, listOf(101L)),
                Playlist("pl_mix", "Roadtrip", listOf(101L, 102L, 103L))
            )
        )
        statsRepo.saveStats(
            mapOf(
                101L to TrackStats(101L, playCount = 5, lastPlayedTimestamp = 5000L),
                102L to TrackStats(102L, playCount = 3, lastPlayedTimestamp = 4000L)
            )
        )
        fakeDao.insert(PendingScrobbleEntity(artist = "Band X", track = "Track Alpha", album = "Album", timestamp = 1700000100L))

        val exportedJson = repository.buildBackupJson(listOf(oldT1, oldT2, oldT3))

        // Simula nuovo dispositivo: libreria con ID completamente nuovi, t2 rinominato ma stesso titolo/artista/durata (~1s di scarto), t3 assente
        val newT1 = makeTrack(9001L, "Track Alpha", "Band X", duration = 180_000L, displayName = "ALPHA.MP3", relativePath = "music/x")
        val newT2 = makeTrack(9002L, "track beta", "band y", duration = 241_000L, displayName = "renamed_beta.flac", relativePath = "NewDir/")

        // Pulisci stato locale prima dell'importazione
        playlistRepo.savePlaylists(listOf(Playlist(FAVORITES_PLAYLIST_ID, FAVORITES_PLAYLIST_NAME, emptyList())))
        statsRepo.saveStats(emptyMap())
        fakeDao.clearAll()

        val summary = repository.importBackupJson(exportedJson, listOf(newT1, newT2))

        assertEquals(1, summary.importedPlaylistsCount)
        assertEquals(0, summary.updatedPlaylistsCount)
        assertEquals(1, summary.favoritesAddedCount)
        assertEquals(2, summary.restoredStatsCount)
        assertEquals(1, summary.restoredPendingScrobblesCount)
        assertTrue(summary.preferencesRestored)
        assertEquals(listOf("Ghost Band - Missing Song"), summary.unmatchedTrackLabels)

        val restoredPlaylists = playlistRepo.loadPlaylists()
        val fav = restoredPlaylists.first { it.isFavorites }
        assertEquals(listOf(9001L), fav.trackIds)
        val mix = restoredPlaylists.first { it.name == "Roadtrip" }
        assertEquals(listOf(9001L, 9002L), mix.trackIds)

        val restoredStats = statsRepo.loadStats()
        assertEquals(5, restoredStats[9001L]?.playCount)
        assertEquals(3, restoredStats[9002L]?.playCount)
        assertEquals(1, fakeDao.items.size)
        verify(mockPrefs).setThemeMode(ThemeMode.DARK)

        // Seconda importazione identica: non deve duplicare nulla
        val secondSummary = repository.importBackupJson(exportedJson, listOf(newT1, newT2))
        assertEquals(0, secondSummary.importedPlaylistsCount)
        assertEquals(0, secondSummary.updatedPlaylistsCount)
        assertEquals(0, secondSummary.favoritesAddedCount)
        assertEquals(0, secondSummary.restoredStatsCount)
        assertEquals(0, secondSummary.restoredPendingScrobblesCount)
        assertEquals(listOf(9001L, 9002L), playlistRepo.loadPlaylists().first { it.name == "Roadtrip" }.trackIds)
        assertEquals(1, fakeDao.items.size)
    }
}
