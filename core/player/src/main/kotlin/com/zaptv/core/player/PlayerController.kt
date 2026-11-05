package com.zaptv.core.player

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.zaptv.core.model.Channel
import com.zaptv.core.model.StreamSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What the player is doing for the channel the user asked for. */
sealed interface PlaybackState {
    data object Idle : PlaybackState
    /** [attempt] is the source being tried (0-based) out of [sourceCount]. */
    data class Loading(val channelKey: String, val attempt: Int, val sourceCount: Int) : PlaybackState
    data class Playing(val channelKey: String) : PlaybackState
    /** Every source failed. */
    data class Unavailable(val channelKey: String) : PlaybackState
}

/** Numbers for the "Stats for nerds" overlay. */
data class PlaybackStats(
    val zapMillis: Long? = null,
    val sourceIndex: Int = 0,
    val sourceCount: Int = 0,
    val videoSize: String? = null,
)

/**
 * Owns the app's single ExoPlayer. It is created once and reused for every channel change:
 * recreating players or surfaces costs hundreds of milliseconds and flashes black.
 * Must be used from the main thread.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(PlaybackStats())
    val stats: StateFlow<PlaybackStats> = _stats.asStateFlow()

    private var current: Channel? = null
    private var sourceIndex = 0
    private var tuneStartedAt = 0L
    private var firstFrameSeen = false

    val player: ExoPlayer by lazy { createPlayer() }

    fun play(channel: Channel) {
        current = channel
        sourceIndex = 0
        tuneStartedAt = SystemClock.elapsedRealtime()
        startCurrentSource()
    }

    /** Plays the same channel again from its first source. */
    fun retry() {
        current?.let { play(it) }
    }

    fun stop() {
        player.stop()
        _state.value = PlaybackState.Idle
    }

    private fun startCurrentSource() {
        val channel = current ?: return
        val source = channel.sources.getOrNull(sourceIndex)
        if (source == null) {
            player.stop()
            _state.value = PlaybackState.Unavailable(channel.key)
            return
        }
        firstFrameSeen = false
        _state.value = PlaybackState.Loading(channel.key, sourceIndex, channel.sources.size)
        _stats.value = PlaybackStats(sourceIndex = sourceIndex, sourceCount = channel.sources.size)
        player.setMediaSource(mediaSourceFor(source))
        player.prepare()
        player.playWhenReady = true
    }

    private fun mediaSourceFor(source: StreamSource): MediaSource {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(source.userAgent ?: DEFAULT_USER_AGENT)
        source.referrer?.let { dataSourceFactory.setDefaultRequestProperties(mapOf("Referer" to it)) }

        val item = MediaItem.Builder().setUri(source.url).apply {
            if (looksLikeHls(source.url)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        return DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(item)
    }

    private fun looksLikeHls(url: String): Boolean {
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return path.endsWith(".m3u8") || path.endsWith(".m3u") || "m3u8" in url.lowercase(Locale.ROOT)
    }

    private fun createPlayer(): ExoPlayer {
        // Start rendering once ~1 s is buffered instead of the default 2.5 s: faster zapping on live TV.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                addListener(listener)
            }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val channel = current ?: return
            if (playbackState == Player.STATE_READY && _state.value !is PlaybackState.Unavailable) {
                _state.value = PlaybackState.Playing(channel.key)
            }
        }

        override fun onRenderedFirstFrame() {
            if (firstFrameSeen) return
            firstFrameSeen = true
            val format = player.videoFormat
            _stats.value = _stats.value.copy(
                zapMillis = SystemClock.elapsedRealtime() - tuneStartedAt,
                videoSize = format?.let { "${it.width}×${it.height}" },
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                // Fell behind the live edge (e.g. after a long stall): jump back to live.
                player.seekToDefaultPosition()
                player.prepare()
                return
            }
            // Try the channel's next source; after the last one, report it unavailable.
            sourceIndex++
            startCurrentSource()
        }
    }

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Android TV) ZapTV/0.1"
    }
}
