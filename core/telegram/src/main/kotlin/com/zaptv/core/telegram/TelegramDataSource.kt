package com.zaptv.core.telegram

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import dev.g000sha256.tdl.dto.File
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile

/**
 * Plays a Telegram video while it downloads. ExoPlayer reads `tg://message/<chatId>/<messageId>`;
 * this asks TDLib to download from the byte ExoPlayer wants and blocks the loader thread until
 * those bytes are on disk. A seek reopens the source at a new position, which moves TDLib's
 * download there, so seeking never waits for the bytes in between.
 *
 * Written from the TDLib documentation (`downloadFile` with an offset, `updateFile`'s
 * `downloadOffset` / `downloadedPrefixSize`); the window maths is in [DownloadWindow].
 */
@OptIn(UnstableApi::class)
class TelegramDataSource(
    private val telegram: TelegramClient,
    private val library: TelegramLibrary,
) : BaseDataSource(/* isNetwork = */ true) {

    private var uri: Uri? = null
    private var fileId = 0
    private var fileSize = 0L
    private var position = 0L
    private var bytesRemaining = 0L
    private var requestedOffset = -1L
    private var reader: RandomAccessFile? = null
    private var readerPath: String? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val (chatId, messageId) = parse(dataSpec.uri)
        transferInitializing(dataSpec)

        val video = runBlocking { library.video(chatId, messageId) }
            ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        fileId = video.fileId
        val file = runBlocking { telegram.requireClient().getFile(fileId).getOrThrow() }
        telegram.onFile(file)
        fileSize = file.size.takeIf { it > 0 } ?: file.expectedSize

        position = dataSpec.position
        if (fileSize > 0 && position > fileSize) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        requestedOffset = -1
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            fileSize > 0 -> fileSize - position
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val waitStarted = SystemClock.elapsedRealtime()
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            val file = telegram.cachedFile(fileId)
                ?: runBlocking { telegram.requireClient().getFile(fileId).getOrThrow() }.also(telegram::onFile)
            val local = file.local
            val decision = DownloadWindow.decide(
                position = position,
                fileSize = fileSize,
                downloadOffset = local.downloadOffset,
                downloadedPrefixSize = local.downloadedPrefixSize,
                isCompleted = local.isDownloadingCompleted,
                isDownloadingActive = local.isDownloadingActive,
                requestedOffset = requestedOffset,
            )
            if (decision.readable > 0 && local.path.isNotEmpty()) {
                val toRead = minOf(length.toLong(), decision.readable, bytesRemaining.takeIf { it >= 0 } ?: Long.MAX_VALUE).toInt()
                val read = readAt(local.path, buffer, offset, toRead)
                if (read > 0) {
                    position += read
                    if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
                    bytesTransferred(read)
                    return read
                }
            }
            if (fileSize > 0 && position >= fileSize) return C.RESULT_END_OF_INPUT
            if (decision.moveDownload) startDownload(file, position)
            if (SystemClock.elapsedRealtime() - waitStarted > READ_TIMEOUT_MS) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
            }
            telegram.awaitFileUpdate(WAIT_SLICE_MS)
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            reader?.close()
        } catch (_: IOException) {
        }
        reader = null
        readerPath = null
        // The download keeps going: the next open (a seek, or the player's read-ahead) wants it.
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun startDownload(file: File, offset: Long) {
        requestedOffset = offset
        val updated = runBlocking {
            telegram.requireClient().downloadFile(file.id, PLAYBACK_PRIORITY, offset, 0, false).getOrThrow()
        }
        telegram.onFile(updated)
    }

    private fun readAt(path: String, buffer: ByteArray, offset: Int, length: Int): Int {
        if (readerPath != path) {
            reader?.close()
            reader = RandomAccessFile(path, "r")
            readerPath = path
        }
        val r = reader!!
        r.seek(position)
        return r.read(buffer, offset, length)
    }

    class Factory(
        private val telegram: TelegramClient,
        private val library: TelegramLibrary,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = TelegramDataSource(telegram, library)
    }

    companion object {
        const val SCHEME = "tg"
        private const val PLAYBACK_PRIORITY = 32 // highest TDLib priority
        private const val WAIT_SLICE_MS = 250L
        private const val READ_TIMEOUT_MS = 30_000L

        fun uriFor(chatId: Long, messageId: Long): String = "$SCHEME://message/$chatId/$messageId"

        internal fun parse(uri: Uri): Pair<Long, Long> {
            val segments = uri.pathSegments
            val chatId = segments.getOrNull(0)?.toLongOrNull()
            val messageId = segments.getOrNull(1)?.toLongOrNull()
            if (uri.scheme != SCHEME || uri.host != "message" || chatId == null || messageId == null) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
            }
            return chatId to messageId
        }
    }
}
