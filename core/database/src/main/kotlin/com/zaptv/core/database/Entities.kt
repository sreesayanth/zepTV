package com.zaptv.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val lastRefreshed: Long?,
    val channelCount: Int,
)

@Entity(
    tableName = "channels",
    indices = [Index("playlistId"), Index("number")],
)
data class ChannelEntity(
    @PrimaryKey val key: String,
    val playlistId: String,
    val number: Int,
    val name: String,
    val category: String,
    val groupTitle: String,
    val logoUrl: String?,
    val tvgId: String?,
    val quality: String?,
    /** Labels joined with "|", e.g. "Not 24/7|Geo-blocked". */
    val labels: String,
)

@Entity(
    tableName = "stream_sources",
    indices = [Index("channelKey")],
)
data class StreamSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelKey: String,
    val url: String,
    val userAgent: String?,
    val referrer: String?,
    val quality: String?,
    /** Lower plays first. */
    val priority: Int,
)

/** Kept in its own table so favourites survive a playlist refresh. */
@Entity(tableName = "favourites")
data class FavouriteEntity(
    @PrimaryKey val channelKey: String,
    val addedAt: Long,
)

data class ChannelWithSources(
    @Embedded val channel: ChannelEntity,
    @Relation(parentColumn = "key", entityColumn = "channelKey")
    val sources: List<StreamSourceEntity>,
)

data class KeyNumber(val key: String, val number: Int)
