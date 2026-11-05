package com.zaptv.feature.live

import com.zaptv.core.model.Category
import com.zaptv.core.model.Channel
import com.zaptv.core.player.PlaybackState
import com.zaptv.core.player.PlaybackStats

/** Every remote button the live screen understands. */
sealed interface RemoteAction {
    data object Up : RemoteAction
    data object Down : RemoteAction
    data object Left : RemoteAction
    data object Right : RemoteAction
    data object Ok : RemoteAction
    data object OkLong : RemoteAction
    data object Back : RemoteAction
    data object ChannelUp : RemoteAction
    data object ChannelDown : RemoteAction
    data object Info : RemoteAction
    data object LastChannel : RemoteAction
    data object Favourites : RemoteAction
    data object Groups : RemoteAction
    data object Menu : RemoteAction
    data class Digit(val value: Char) : RemoteAction
}

/** A group in the channel list. */
sealed interface ChannelGroup {
    data object Favourites : ChannelGroup
    data object All : ChannelGroup
    data class OfCategory(val category: Category) : ChannelGroup
}

enum class Aspect { Fit, Fill, Zoom }

enum class OptionItem { Favourite, PictureSize, UpButton, Stats, Reload }

sealed interface Overlay {
    data object None : Overlay
    data class ChannelList(val groupIndex: Int, val focusedIndex: Int, val onGroups: Boolean) : Overlay
    data class Options(val focusedIndex: Int) : Overlay
    data class Exit(val exitFocused: Boolean) : Overlay
}

enum class LineupStatus { Loading, Ready, Error }

/** Short messages shown at the top of the screen. Text is resolved in the UI so it can be translated. */
sealed interface ToastMessage {
    data object Coach : ToastMessage
    data class AddedFavourite(val name: String) : ToastMessage
    data class RemovedFavourite(val name: String) : ToastMessage
    data class BackTo(val name: String) : ToastMessage
    data class NoChannel(val digits: String) : ToastMessage
    data class Refreshed(val count: Int) : ToastMessage
    data object RefreshFailed : ToastMessage
}

data class LiveUiState(
    val lineupStatus: LineupStatus = LineupStatus.Loading,
    val channels: List<Channel> = emptyList(),
    val favourites: Set<String> = emptySet(),
    val currentKey: String? = null,
    /** The channel the banner shows while the user is still pressing ▲/▼ quickly. */
    val pendingKey: String? = null,
    val previousKey: String? = null,
    val playback: PlaybackState = PlaybackState.Idle,
    val stats: PlaybackStats = PlaybackStats(),
    val bannerVisible: Boolean = false,
    val infoExpanded: Boolean = false,
    val digits: String = "",
    val overlay: Overlay = Overlay.None,
    val activeGroup: ChannelGroup = ChannelGroup.All,
    val toast: ToastMessage? = null,
    val skipCountdown: Int? = null,
    val upIsNextChannel: Boolean = true,
    val statsVisible: Boolean = false,
    val aspect: Aspect = Aspect.Fit,
    val exitRequested: Boolean = false,
) {
    val current: Channel? get() = channels.firstOrNull { it.key == currentKey }
    val bannerChannel: Channel? get() = channels.firstOrNull { it.key == (pendingKey ?: currentKey) }

    val isTuning: Boolean
        get() = (playback as? PlaybackState.Loading)?.channelKey == currentKey && currentKey != null

    val isUnavailable: Boolean
        get() = (playback as? PlaybackState.Unavailable)?.channelKey == currentKey && currentKey != null

    /** Groups for the channel list: Favourites, All, then each category that has channels. */
    val groups: List<ChannelGroup>
        get() = listOf(ChannelGroup.Favourites, ChannelGroup.All) +
            channels.map { it.category }.distinct().sortedBy { it.ordinal }.map { ChannelGroup.OfCategory(it) }

    fun channelsIn(group: ChannelGroup): List<Channel> = when (group) {
        ChannelGroup.All -> channels
        ChannelGroup.Favourites -> channels.filter { it.key in favourites }
        is ChannelGroup.OfCategory -> channels.filter { it.category == group.category }
    }
}
