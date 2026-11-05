package com.zaptv.core.telegram

/**
 * The arithmetic behind streaming a Telegram file while it downloads.
 *
 * TDLib downloads one contiguous window at a time: bytes `[downloadOffset, downloadOffset +
 * downloadedPrefixSize)` are on disk, everything else is a hole. For a read at [position] this
 * decides how many bytes can be read now and whether the download has to be moved there.
 * Pure Kotlin so the awkward cases are pinned down by unit tests.
 */
object DownloadWindow {

    /** How far ahead of the window's end a read may be and still just wait for the bytes. */
    const val DEFAULT_SLACK_BYTES = 1L shl 20

    data class Decision(
        /** Bytes readable at the position right now; 0 means wait. */
        val readable: Long,
        /** True when TDLib should be asked to download from the position. */
        val moveDownload: Boolean,
    )

    fun decide(
        position: Long,
        fileSize: Long,
        downloadOffset: Long,
        downloadedPrefixSize: Long,
        isCompleted: Boolean,
        isDownloadingActive: Boolean,
        /** The offset this reader last asked TDLib for, or -1 if it has not asked yet. */
        requestedOffset: Long,
        slackBytes: Long = DEFAULT_SLACK_BYTES,
    ): Decision {
        if (fileSize in 1..position) return Decision(readable = 0, moveDownload = false)
        if (isCompleted) return Decision(readable = (fileSize - position).coerceAtLeast(0), moveDownload = false)

        val windowEnd = downloadOffset + downloadedPrefixSize
        if (position in downloadOffset until windowEnd) {
            return Decision(readable = windowEnd - position, moveDownload = false)
        }

        // A hole. Wait if the active download is about to reach the position, or if this reader
        // already asked for it (TDLib keeps one download per file, the last request wins).
        val windowIsComing = isDownloadingActive && position >= windowEnd && position - windowEnd <= slackBytes
        val alreadyAsked = requestedOffset == position && isDownloadingActive
        return Decision(readable = 0, moveDownload = !windowIsComing && !alreadyAsked)
    }
}
