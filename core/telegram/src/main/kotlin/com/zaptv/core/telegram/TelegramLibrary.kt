package com.zaptv.core.telegram

import com.zaptv.core.parser.MediaName
import com.zaptv.core.parser.MediaNameParser
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.SearchMessagesFilter
import dev.g000sha256.tdl.dto.SearchMessagesFilterDocument
import dev.g000sha256.tdl.dto.SearchMessagesFilterVideo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/** A chat or channel that has videos in it. */
data class TgChat(
    val id: Long,
    val title: String,
    val isChannel: Boolean,
    /** Videos plus files; files may include a few non-videos, so treat it as approximate. */
    val videoCount: Int,
)

/** One playable video message. `(chatId, messageId)` is the stable key; [fileId] is per session. */
data class TgVideo(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val fileName: String,
    val caption: String,
    val durationSec: Int,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String,
    /** Unix seconds. */
    val date: Int,
    val thumbnailFileId: Int?,
    /** Messages sent together as an album share this; 0 when not in an album. */
    val albumId: Long,
    val name: MediaName,
) {
    /** Media3 URI for [TelegramDataSource]. */
    val uri: String get() = TelegramDataSource.uriFor(chatId, messageId)
}

data class TgVideoPage(val videos: List<TgVideo>, val nextFromMessageId: Long) {
    val hasMore: Boolean get() = nextFromMessageId != 0L
}

/**
 * Reads chats and their videos from Telegram. Shows media, never messages: only video
 * messages and video files sent as documents (so an `.mkv` sent as a file is not missed).
 */
@Singleton
class TelegramLibrary @Inject constructor(
    private val telegram: TelegramClient,
) {
    /** Every chat in the main list that has at least one video, most recent first. */
    suspend fun chatsWithVideos(maxChats: Int = 300): List<TgChat> = coroutineScope {
        val client = telegram.requireClient()
        // loadChats pages the list in from the server; it answers 404 once everything is loaded.
        while (true) {
            val loaded = client.loadChats(ChatListMain(), 100)
            if (loaded is dev.g000sha256.tdl.TdlResult.Failure) break
        }
        val ids = client.getChats(ChatListMain(), maxChats).getOrThrow().chatIds
        ids.map { id ->
            async {
                val chat = client.getChat(id).getOrNull() ?: return@async null
                val videos = count(id, SearchMessagesFilterVideo())
                val documents = count(id, SearchMessagesFilterDocument())
                if (videos + documents == 0) return@async null
                TgChat(
                    id = id,
                    title = chat.title,
                    isChannel = (chat.type as? ChatTypeSupergroup)?.isChannel == true,
                    videoCount = videos + documents,
                )
            }
        // Keeps Telegram's own order: the chats used most recently come first.
        }.mapNotNull { it.await() }
    }

    /**
     * One page of a chat's videos, newest first. Pass [fromMessageId] = previous page's
     * [TgVideoPage.nextFromMessageId] to continue. Videos and video files are paged separately
     * because Telegram filters them separately; [kind] picks which.
     */
    suspend fun videos(
        chatId: Long,
        fromMessageId: Long = 0,
        limit: Int = 50,
        kind: VideoKind = VideoKind.Videos,
    ): TgVideoPage {
        val filter: SearchMessagesFilter = when (kind) {
            VideoKind.Videos -> SearchMessagesFilterVideo()
            VideoKind.Files -> SearchMessagesFilterDocument()
        }
        val found = telegram.requireClient()
            .searchChatMessages(chatId, null, "", null, fromMessageId, 0, limit, filter)
            .getOrThrow()
        return TgVideoPage(found.messages.mapNotNull(::toVideo), found.nextFromMessageId)
    }

    /** The current file id for a message's video, e.g. before playback (ids change per session). */
    suspend fun video(chatId: Long, messageId: Long): TgVideo? =
        telegram.requireClient().getMessage(chatId, messageId).getOrNull()?.let(::toVideo)

    private suspend fun count(chatId: Long, filter: SearchMessagesFilter): Int =
        telegram.requireClient().getChatMessageCount(chatId, null, filter, false).getOrNull()?.count ?: 0

    enum class VideoKind { Videos, Files }

    companion object {
        internal fun toVideo(message: Message): TgVideo? = when (val content = message.content) {
            is MessageVideo -> {
                val v = content.video
                val caption = content.caption.text
                TgVideo(
                    chatId = message.chatId,
                    messageId = message.id,
                    fileId = v.video.id,
                    fileName = v.fileName,
                    caption = caption,
                    durationSec = v.duration,
                    width = v.width,
                    height = v.height,
                    sizeBytes = v.video.size.takeIf { it > 0 } ?: v.video.expectedSize,
                    mimeType = v.mimeType,
                    date = message.date,
                    thumbnailFileId = v.thumbnail?.file?.id,
                    albumId = message.mediaAlbumId,
                    name = MediaNameParser.parse(v.fileName.ifEmpty { caption }, caption),
                )
            }
            is MessageDocument -> {
                val d = content.document
                val isVideo = d.mimeType.startsWith("video/") || MediaNameParser.isVideoFileName(d.fileName)
                if (!isVideo) null else TgVideo(
                    chatId = message.chatId,
                    messageId = message.id,
                    fileId = d.document.id,
                    fileName = d.fileName,
                    caption = content.caption.text,
                    durationSec = 0, // unknown until the player reads the file
                    width = 0,
                    height = 0,
                    sizeBytes = d.document.size.takeIf { it > 0 } ?: d.document.expectedSize,
                    mimeType = d.mimeType,
                    date = message.date,
                    thumbnailFileId = d.thumbnail?.file?.id,
                    albumId = message.mediaAlbumId,
                    name = MediaNameParser.parse(d.fileName, content.caption.text),
                )
            }
            else -> null
        }
    }
}

private fun <T> dev.g000sha256.tdl.TdlResult<T>.getOrNull(): T? =
    (this as? dev.g000sha256.tdl.TdlResult.Success)?.result
