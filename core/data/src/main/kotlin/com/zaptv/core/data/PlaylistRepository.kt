package com.zaptv.core.data

import com.zaptv.core.database.ChannelDao
import com.zaptv.core.database.PlaylistDao
import com.zaptv.core.database.PlaylistEntity
import com.zaptv.core.parser.LineupBuilder
import com.zaptv.core.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** A channel list the app knows about out of the box. Only URLs are stored, never stream links. */
data class PlaylistPreset(val id: String, val name: String, val url: String)

object PlaylistPresets {
    val Malayalam = PlaylistPreset("iptv-org-mal", "Malayalam", "https://iptv-org.github.io/iptv/languages/mal.m3u")
    val India = PlaylistPreset("iptv-org-in", "All of India", "https://iptv-org.github.io/iptv/countries/in.m3u")
    val WorldNews = PlaylistPreset("iptv-org-news", "World news", "https://iptv-org.github.io/iptv/categories/news.m3u")

    val Default = Malayalam
}

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
            if (playlist.entries.isEmpty()) throw IOException("The channel list is empty")

            val previous = channelDao.numbersFor(preset.id).associate { it.key to it.number }
            val lineup = LineupBuilder.build(preset.id, playlist.entries, previous)
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

    companion object {
        const val USER_AGENT = "ZapTV/0.1 (Android TV)"
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
