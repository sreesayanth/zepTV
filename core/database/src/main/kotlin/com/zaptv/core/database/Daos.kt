package com.zaptv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChannelDao {

    @Transaction
    @Query("SELECT * FROM channels ORDER BY number")
    abstract fun observeLineup(): Flow<List<ChannelWithSources>>

    @Query("SELECT `key`, number FROM channels WHERE playlistId = :playlistId")
    abstract suspend fun numbersFor(playlistId: String): List<KeyNumber>

    @Query("SELECT COUNT(*) FROM channels")
    abstract suspend fun count(): Int

    @Insert
    protected abstract suspend fun insertChannels(channels: List<ChannelEntity>)

    @Insert
    protected abstract suspend fun insertSources(sources: List<StreamSourceEntity>)

    @Query("DELETE FROM stream_sources WHERE channelKey IN (SELECT `key` FROM channels WHERE playlistId = :playlistId)")
    protected abstract suspend fun deleteSourcesFor(playlistId: String)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    protected abstract suspend fun deleteChannelsFor(playlistId: String)

    /** Swaps a playlist's channels in one transaction, so the lineup is never half-updated. */
    @Transaction
    open suspend fun replacePlaylist(
        playlistId: String,
        channels: List<ChannelEntity>,
        sources: List<StreamSourceEntity>,
    ) {
        deleteSourcesFor(playlistId)
        deleteChannelsFor(playlistId)
        channels.chunked(500).forEach { insertChannels(it) }
        sources.chunked(500).forEach { insertSources(it) }
    }

    /** Removes a playlist's channels, e.g. when its language is turned off. */
    @Transaction
    open suspend fun deletePlaylist(playlistId: String) {
        deleteSourcesFor(playlistId)
        deleteChannelsFor(playlistId)
    }
}

@Dao
interface PlaylistDao {
    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists")
    suspend fun all(): List<PlaylistEntity>

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface FavouriteDao {
    @Query("SELECT channelKey FROM favourites ORDER BY addedAt")
    fun observeKeys(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favourite: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE channelKey = :key")
    suspend fun remove(key: String)
}
