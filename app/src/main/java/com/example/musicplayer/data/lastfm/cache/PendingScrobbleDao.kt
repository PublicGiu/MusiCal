package com.example.musicplayer.data.lastfm.cache

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) per la gestione della cache offline degli scrobble.
 */
@Dao
interface PendingScrobbleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pendingScrobble: PendingScrobbleEntity): Long

    @Query("SELECT * FROM pending_scrobbles ORDER BY timestamp ASC")
    suspend fun getAllPendingAsc(): List<PendingScrobbleEntity>

    @Query("SELECT COUNT(*) FROM pending_scrobbles")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_scrobbles")
    suspend fun getPendingCount(): Int

    @Query("DELETE FROM pending_scrobbles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_scrobbles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Delete
    suspend fun delete(entity: PendingScrobbleEntity)

    @Query("DELETE FROM pending_scrobbles")
    suspend fun clearAll()
}
