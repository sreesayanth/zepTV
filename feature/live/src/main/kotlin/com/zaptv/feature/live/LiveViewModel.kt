package com.zaptv.feature.live

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.zaptv.core.data.ChannelRepository
import com.zaptv.core.data.PlaylistPresets
import com.zaptv.core.data.PlaylistRepository
import com.zaptv.core.data.SettingsRepository
import com.zaptv.core.model.Channel
import com.zaptv.core.player.PlaybackState
import com.zaptv.core.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val playlistRepository: PlaylistRepository,
    private val settingsRepository: SettingsRepository,
    private val playerController: PlayerController,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    val player: Player get() = playerController.player

    private var zapJob: Job? = null
    private var bannerJob: Job? = null
    private var digitJob: Job? = null
    private var skipJob: Job? = null
    private var toastJob: Job? = null
    private var lastZapAt = 0L
    private var lastBackAt = 0L
    private var lastDirection = 1
    private var stoppedByLifecycle = false

    init {
        viewModelScope.launch {
            combine(channelRepository.channels, channelRepository.favourites) { channels, favourites -> channels to favourites }
                .collect { (channels, favourites) ->
                    _state.update {
                        it.copy(
                            channels = channels,
                            favourites = favourites,
                            lineupStatus = if (channels.isNotEmpty()) LineupStatus.Ready else it.lineupStatus,
                        )
                    }
                    if (channels.isNotEmpty() && _state.value.currentKey == null) startFirstChannel(channels)
                }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                _state.update { it.copy(upIsNextChannel = s.upIsNextChannel, statsVisible = s.statsVisible) }
            }
        }
        viewModelScope.launch { playerController.state.collect(::onPlaybackChanged) }
        viewModelScope.launch { playerController.stats.collect { stats -> _state.update { it.copy(stats = stats) } } }
        loadLineup(force = false)
    }

    // ---------------------------------------------------------------- lifecycle

    fun onStop() {
        stoppedByLifecycle = true
        zapJob?.cancel()
        skipJob?.cancel()
        playerController.stop()
    }

    fun onStart() {
        if (!stoppedByLifecycle) return
        stoppedByLifecycle = false
        _state.value.current?.let {
            playerController.play(it)
            showBanner()
        }
    }

    override fun onCleared() {
        playerController.stop()
    }

    // ---------------------------------------------------------------- input

    fun onAction(action: RemoteAction) {
        val s = _state.value
        if (s.lineupStatus != LineupStatus.Ready || s.channels.isEmpty()) {
            when (action) {
                RemoteAction.Ok -> if (s.lineupStatus == LineupStatus.Error) loadLineup(force = false)
                RemoteAction.Back -> _state.update { it.copy(exitRequested = true) }
                else -> Unit
            }
            return
        }
        when (val overlay = s.overlay) {
            Overlay.None -> onLiveAction(action)
            is Overlay.ChannelList -> onListAction(overlay, action)
            is Overlay.Options -> onOptionsAction(overlay, action)
            is Overlay.Exit -> onExitAction(overlay, action)
        }
    }

    private fun onLiveAction(action: RemoteAction) {
        val s = _state.value
        val up = if (s.upIsNextChannel) 1 else -1
        when (action) {
            RemoteAction.Up -> zap(up)
            RemoteAction.Down -> zap(-up)
            RemoteAction.ChannelUp -> zap(1)
            RemoteAction.ChannelDown -> zap(-1)
            RemoteAction.Ok -> when {
                s.digits.isNotEmpty() -> commitDigits()
                s.isUnavailable -> retry()
                else -> openList(onGroups = false)
            }
            RemoteAction.OkLong -> s.current?.let { toggleFavourite(it) }
            RemoteAction.Left, RemoteAction.Groups -> openList(onGroups = true)
            RemoteAction.Right, RemoteAction.Menu -> openOptions()
            RemoteAction.Favourites -> openList(onGroups = false, group = ChannelGroup.Favourites)
            RemoteAction.Info -> {
                if (s.infoExpanded) hideBanner()
                else {
                    _state.update { it.copy(infoExpanded = true) }
                    showBanner(INFO_MS)
                }
            }
            RemoteAction.LastChannel -> recall()
            RemoteAction.Back -> onLiveBack()
            is RemoteAction.Digit -> digit(action.value)
        }
    }

    /** BACK goes to the previous channel; twice within a second asks before exiting. */
    private fun onLiveBack() {
        val s = _state.value
        when {
            s.digits.isNotEmpty() -> {
                digitJob?.cancel()
                _state.update { it.copy(digits = "") }
            }
            s.infoExpanded -> hideBanner()
            else -> {
                val now = SystemClock.uptimeMillis()
                if (now - lastBackAt < DOUBLE_BACK_MS) {
                    lastBackAt = 0
                    _state.update { it.copy(overlay = Overlay.Exit(exitFocused = false)) }
                } else {
                    lastBackAt = now
                    if (s.previousKey != null && s.previousKey != s.currentKey) recall()
                    else _state.update { it.copy(overlay = Overlay.Exit(exitFocused = false)) }
                }
            }
        }
    }

    private fun onListAction(overlay: Overlay.ChannelList, action: RemoteAction) {
        val s = _state.value
        val groups = s.groups
        val groupIndex = overlay.groupIndex.coerceIn(0, groups.lastIndex)
        val channels = s.channelsIn(groups[groupIndex])
        fun currentIndexIn(index: Int) = s.channelsIn(groups[index]).indexOfFirst { it.key == s.currentKey }.coerceAtLeast(0)

        val next: Overlay = when (action) {
            RemoteAction.Up -> when {
                overlay.onGroups -> (groupIndex - 1).coerceAtLeast(0).let { overlay.copy(groupIndex = it, focusedIndex = currentIndexIn(it)) }
                channels.isEmpty() -> overlay
                else -> overlay.copy(focusedIndex = (overlay.focusedIndex - 1).mod(channels.size))
            }
            RemoteAction.Down -> when {
                overlay.onGroups -> (groupIndex + 1).coerceAtMost(groups.lastIndex).let { overlay.copy(groupIndex = it, focusedIndex = currentIndexIn(it)) }
                channels.isEmpty() -> overlay
                else -> overlay.copy(focusedIndex = (overlay.focusedIndex + 1).mod(channels.size))
            }
            RemoteAction.ChannelUp -> overlay.copy(focusedIndex = (overlay.focusedIndex - LIST_PAGE).coerceAtLeast(0))
            RemoteAction.ChannelDown -> overlay.copy(focusedIndex = (overlay.focusedIndex + LIST_PAGE).coerceAtMost((channels.size - 1).coerceAtLeast(0)))
            RemoteAction.Left -> if (overlay.onGroups) Overlay.None else overlay.copy(onGroups = true)
            RemoteAction.Right -> if (overlay.onGroups && channels.isNotEmpty()) overlay.copy(onGroups = false) else overlay
            RemoteAction.Ok -> {
                if (overlay.onGroups) {
                    if (channels.isNotEmpty()) overlay.copy(onGroups = false) else overlay
                } else {
                    val channel = channels.getOrNull(overlay.focusedIndex) ?: return
                    _state.update { it.copy(overlay = Overlay.None, activeGroup = groups[groupIndex]) }
                    tune(channel.key)
                    return
                }
            }
            RemoteAction.OkLong -> {
                if (!overlay.onGroups) channels.getOrNull(overlay.focusedIndex)?.let { toggleFavourite(it) }
                overlay
            }
            RemoteAction.Back, RemoteAction.Groups -> Overlay.None
            is RemoteAction.Digit -> {
                _state.update { it.copy(overlay = Overlay.None) }
                digit(action.value)
                return
            }
            else -> overlay
        }
        _state.update { it.copy(overlay = next) }
    }

    private fun onOptionsAction(overlay: Overlay.Options, action: RemoteAction) {
        val s = _state.value
        val items = OptionItem.entries
        val item = items[overlay.focusedIndex.coerceIn(0, items.lastIndex)]
        val close = { _state.update { it.copy(overlay = Overlay.None) } }
        when (action) {
            RemoteAction.Up -> _state.update { it.copy(overlay = overlay.copy(focusedIndex = (overlay.focusedIndex - 1).coerceAtLeast(0))) }
            RemoteAction.Down -> _state.update { it.copy(overlay = overlay.copy(focusedIndex = (overlay.focusedIndex + 1).coerceAtMost(items.lastIndex))) }
            RemoteAction.Left, RemoteAction.Right, RemoteAction.Ok -> {
                val delta = if (action == RemoteAction.Left) -1 else 1
                when (item) {
                    OptionItem.Favourite ->
                        if (action == RemoteAction.Left) close() else s.current?.let { toggleFavourite(it) }
                    OptionItem.PictureSize -> {
                        val all = Aspect.entries
                        _state.update { it.copy(aspect = all[(it.aspect.ordinal + delta).mod(all.size)]) }
                    }
                    OptionItem.UpButton -> viewModelScope.launch { settingsRepository.setUpIsNextChannel(!s.upIsNextChannel) }
                    OptionItem.Stats -> viewModelScope.launch { settingsRepository.setStatsVisible(!s.statsVisible) }
                    OptionItem.Reload -> {
                        close()
                        if (action != RemoteAction.Left) loadLineup(force = true)
                    }
                }
            }
            RemoteAction.Back, RemoteAction.Menu -> close()
            else -> Unit
        }
    }

    private fun onExitAction(overlay: Overlay.Exit, action: RemoteAction) {
        when (action) {
            RemoteAction.Left -> _state.update { it.copy(overlay = overlay.copy(exitFocused = false)) }
            RemoteAction.Right -> _state.update { it.copy(overlay = overlay.copy(exitFocused = true)) }
            RemoteAction.Ok ->
                if (overlay.exitFocused) _state.update { it.copy(exitRequested = true) }
                else _state.update { it.copy(overlay = Overlay.None) }
            RemoteAction.Back -> _state.update { it.copy(overlay = Overlay.None) }
            else -> Unit
        }
    }

    // ---------------------------------------------------------------- channel changes

    /**
     * Single press: tune straight away. Presses less than 400 ms apart only move the banner,
     * and the channel tunes 350 ms after the last one, so flicking 101 → 109 loads one stream, not nine.
     */
    private fun zap(direction: Int) {
        val s = _state.value
        val lineup = s.channelsIn(s.activeGroup).ifEmpty { s.channels }
        if (lineup.isEmpty()) return
        lastDirection = direction
        skipJob?.cancel()
        digitJob?.cancel()

        val from = s.pendingKey ?: s.currentKey
        val position = lineup.indexOfFirst { it.key == from }
        val nextIndex = if (position < 0) {
            if (direction > 0) 0 else lineup.lastIndex
        } else {
            (position + direction).mod(lineup.size)
        }
        val target = lineup[nextIndex]
        _state.update { it.copy(pendingKey = target.key, digits = "", infoExpanded = false, skipCountdown = null) }
        showBanner()

        val now = SystemClock.uptimeMillis()
        val rapid = now - lastZapAt < RAPID_ZAP_MS
        lastZapAt = now
        zapJob?.cancel()
        if (rapid) {
            zapJob = viewModelScope.launch {
                delay(ZAP_SETTLE_MS)
                zapJob = null
                tune(target.key)
            }
        } else {
            tune(target.key)
        }
    }

    private fun tune(key: String, bannerMillis: Long = BANNER_MS) {
        val s = _state.value
        val channel = s.channels.firstOrNull { it.key == key } ?: return
        zapJob?.cancel()
        zapJob = null
        skipJob?.cancel()
        val previous = if (key != s.currentKey) s.currentKey else s.previousKey
        _state.update { it.copy(currentKey = key, pendingKey = null, previousKey = previous, skipCountdown = null) }
        playerController.play(channel)
        showBanner(bannerMillis)
        viewModelScope.launch { settingsRepository.setLastChannel(key) }
    }

    private fun retry() {
        skipJob?.cancel()
        _state.update { it.copy(skipCountdown = null) }
        playerController.retry()
        showBanner()
    }

    private fun recall() {
        val s = _state.value
        val previous = s.channels.firstOrNull { it.key == s.previousKey } ?: return
        showToast(ToastMessage.BackTo(previous.name))
        tune(previous.key)
    }

    private suspend fun startFirstChannel(channels: List<Channel>) {
        val settings = settingsRepository.settings.first()
        val start = channels.firstOrNull { it.key == settings.lastChannelKey } ?: channels.first()
        tune(start.key, bannerMillis = FIRST_BANNER_MS)
        if (!settings.coachSeen) {
            showToast(ToastMessage.Coach, COACH_MS)
            settingsRepository.setCoachSeen()
        }
    }

    // ---------------------------------------------------------------- number entry

    private fun digit(value: Char) {
        var digits = _state.value.digits
        if (digits.length >= 3) digits = ""
        digits += value
        _state.update { it.copy(digits = digits) }
        digitJob?.cancel()
        digitJob = viewModelScope.launch {
            delay(DIGIT_COMMIT_MS)
            digitJob = null
            commitDigits()
        }
    }

    private fun commitDigits() {
        digitJob?.cancel()
        digitJob = null
        val raw = _state.value.digits
        if (raw.isEmpty()) return
        _state.update { it.copy(digits = "") }
        val number = raw.toIntOrNull()
        val channel = _state.value.channels.firstOrNull { it.number == number }
        if (channel == null) {
            showToast(ToastMessage.NoChannel(raw))
        } else {
            _state.update { it.copy(activeGroup = ChannelGroup.All) }
            tune(channel.key)
        }
    }

    // ---------------------------------------------------------------- overlays and messages

    private fun openList(onGroups: Boolean, group: ChannelGroup = _state.value.activeGroup) {
        val s = _state.value
        val groups = s.groups
        val groupIndex = groups.indexOf(group).takeIf { it >= 0 } ?: groups.indexOf(ChannelGroup.All)
        val focused = s.channelsIn(groups[groupIndex]).indexOfFirst { it.key == s.currentKey }.coerceAtLeast(0)
        bannerJob?.cancel()
        _state.update {
            it.copy(
                overlay = Overlay.ChannelList(groupIndex, focused, onGroups),
                bannerVisible = false,
                infoExpanded = false,
            )
        }
    }

    private fun openOptions() {
        bannerJob?.cancel()
        _state.update { it.copy(overlay = Overlay.Options(0), bannerVisible = false, infoExpanded = false) }
    }

    private fun toggleFavourite(channel: Channel) {
        val isFavourite = channel.key in _state.value.favourites
        viewModelScope.launch { channelRepository.setFavourite(channel.key, !isFavourite) }
        showToast(if (isFavourite) ToastMessage.RemovedFavourite(channel.name) else ToastMessage.AddedFavourite(channel.name))
    }

    private fun showBanner(millis: Long = BANNER_MS) {
        _state.update { it.copy(bannerVisible = true) }
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            delay(millis)
            _state.update { it.copy(bannerVisible = false, infoExpanded = false) }
        }
    }

    private fun hideBanner() {
        bannerJob?.cancel()
        _state.update { it.copy(bannerVisible = false, infoExpanded = false) }
    }

    private fun showToast(message: ToastMessage, millis: Long = TOAST_MS) {
        _state.update { it.copy(toast = message) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(millis)
            _state.update { if (it.toast == message) it.copy(toast = null) else it }
        }
    }

    private fun onPlaybackChanged(playback: PlaybackState) {
        _state.update { it.copy(playback = playback) }
        if (playback is PlaybackState.Unavailable && playback.channelKey == _state.value.currentKey) startSkipCountdown()
    }

    /** When every source fails, count down and move on, so nobody is left looking at an error. */
    private fun startSkipCountdown() {
        skipJob?.cancel()
        skipJob = viewModelScope.launch {
            for (seconds in SKIP_SECONDS downTo 1) {
                _state.update { it.copy(skipCountdown = seconds) }
                delay(1_000)
            }
            _state.update { it.copy(skipCountdown = null) }
            val s = _state.value
            if (s.overlay == Overlay.None && s.isUnavailable) zap(lastDirection)
        }
    }

    private fun loadLineup(force: Boolean) {
        viewModelScope.launch {
            val preset = PlaylistPresets.Default
            val hasChannels = playlistRepository.channelCount() > 0
            if (!force && hasChannels && !playlistRepository.isStale(preset)) return@launch
            if (!hasChannels) _state.update { it.copy(lineupStatus = LineupStatus.Loading) }
            playlistRepository.refresh(preset)
                .onSuccess { count -> if (force) showToast(ToastMessage.Refreshed(count)) }
                .onFailure {
                    if (!hasChannels) _state.update { it.copy(lineupStatus = LineupStatus.Error) }
                    else if (force) showToast(ToastMessage.RefreshFailed)
                }
        }
    }

    private companion object {
        const val BANNER_MS = 4_500L
        const val INFO_MS = 8_000L
        const val FIRST_BANNER_MS = 9_000L
        const val COACH_MS = 7_000L
        const val TOAST_MS = 2_600L
        const val RAPID_ZAP_MS = 400L
        const val ZAP_SETTLE_MS = 350L
        const val DIGIT_COMMIT_MS = 2_000L
        const val DOUBLE_BACK_MS = 1_000L
        const val SKIP_SECONDS = 5
        const val LIST_PAGE = 8
    }
}
