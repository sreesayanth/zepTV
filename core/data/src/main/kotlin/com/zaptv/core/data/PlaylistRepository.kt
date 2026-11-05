package com.zaptv.core.data

import com.zaptv.core.database.ChannelDao
import com.zaptv.core.database.PlaylistDao
import com.zaptv.core.database.PlaylistEntity
import com.zaptv.core.model.PlaylistPreset
import com.zaptv.core.parser.LineupBuilder
import com.zaptv.core.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val http: OkHttpClient,
    private val channelDao: ChannelDao,
    private val playlistDao: PlaylistDao,
) {

    suspend fun channelCount(): Int = channelDao.count()

    suspend fun isStale(preset: PlaylistPreset, maxAgeMillis: Long = DAY_MILLIS): Boolean {
        val last = playlistDao.get(preset.id)?.lastRefreshed ?: return true
        return System.currentTimeMillis() - last > maxAgeMillis
    }

    /** Downloads, parses and stores a playlist. Returns the number of channels. */
    suspend fun refresh(preset: PlaylistPreset): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(preset.url).header("User-Agent", USER_AGENT).build()
            val playlist = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${preset.url}")
                response.body.charStream().use { M3uParser.parse(it) }
            }
            val entries = playlist.entries.filter { preset.country == null || it.countryCode == preset.country }
            if (entries.isEmpty()) throw IOException("The channel list is empty")

            val previous = channelDao.numbersFor(preset.id).associate { it.key to it.number }
            val lineup = LineupBuilder.build(preset.id, entries, previous, preset.numberBase, preset.capacity)
            channelDao.replacePlaylist(
                playlistId = preset.id,
                channels = lineup.map { it.toEntity(preset.id) },
                sources = lineup.flatMap { it.sourceEntities() },
            )
            playlistDao.upsert(
                PlaylistEntity(preset.id, preset.name, preset.url, System.currentTimeMillis(), lineup.size)
            )
            lineup.size
        }
    }

    /** Removes a playlist and its channels. */
    suspend fun remove(playlistId: String) {
        channelDao.deletePlaylist(playlistId)
        playlistDao.delete(playlistId)
    }

    /** Ids of the playlists that are stored on the device. */
    suspend fun storedIds(): Set<String> = playlistDao.all().map { it.id }.toSet()

    /** Channel count per stored playlist, before duplicates across playlists are removed. */
    suspend fun channelCounts(): Map<String, Int> = playlistDao.all().associate { it.id to it.channelCount }

    companion object {
        const val USER_AGENT = "ZapTV/0.1 (Android TV)"
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
