package com.zaptv.feature.live

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.zaptv.core.data.ChannelRepository
import com.zaptv.core.data.PlaylistRepository
import com.zaptv.core.data.SettingsRepository
import com.zaptv.core.model.Channel
import com.zaptv.core.model.PlaylistPreset
import com.zaptv.core.model.PlaylistPresets
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** Downloads and removals of playlists run one at a time. */
    private val lineupMutex = Mutex()

    init {
        viewModelScope.launch {
            combine(channelRepository.channels, channelRepository.favourites) { channels, favourites -> channels to favourites }
                .collect { (channels, favourites) ->
                    val playing = _state.value.current
                    _state.update {
                        it.copy(
                            channels = channels,
                            favourites = favourites,
                            lineupStatus = if (channels.isNotEmpty()) LineupStatus.Ready else it.lineupStatus,
                        )
                    }
                    when {
                        channels.isEmpty() -> Unit
                        _state.value.currentKey == null -> startFirstChannel(channels)
                        // The playing channel's language was turned off, or it is now shown under another
                        // language: keep watching the same channel if it is still there, otherwise start over.
                        channels.none { it.key == _state.value.currentKey } -> {
                            val same = playing?.tvgId?.let { id -> channels.firstOrNull { it.tvgId.equals(id, ignoreCase = true) } }
                            if (same != null) tune(same.key) else startFirstChannel(channels, ignoreLast = true)
                        }
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                _state.update {
                    it.copy(upIsNextChannel = s.upIsNextChannel, statsVisible = s.statsVisible, enabledPlaylists = s.enabledPlaylists)
                }
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
            is Overlay.Languages -> onLanguagesAction(overlay, action)
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
                    OptionItem.Languages ->
                        if (action == RemoteAction.Left) close() else _state.update { it.copy(overlay = Overlay.Languages(0)) }
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

    private fun onLanguagesAction(overlay: Overlay.Languages, action: RemoteAction) {
        val presets = PlaylistPresets.all
        val backToOptions = { _state.update { it.copy(overlay = Overlay.Options(OptionItem.Languages.ordinal)) } }
        when (action) {
            RemoteAction.Up -> _state.update { it.copy(overlay = overlay.copy(focusedIndex = (overlay.focusedIndex - 1).coerceAtLeast(0))) }
            RemoteAction.Down -> _state.update { it.copy(overlay = overlay.copy(focusedIndex = (overlay.focusedIndex + 1).coerceAtMost(presets.lastIndex))) }
            RemoteAction.Ok, RemoteAction.Right -> presets.getOrNull(overlay.focusedIndex)?.let { toggleLanguage(it) }
            RemoteAction.Left, RemoteAction.Back -> backToOptions()
            RemoteAction.Menu -> _state.update { it.copy(overlay = Overlay.None) }
            else -> Unit
        }
    }

    /** Turns a language on (downloads its channels) or off (removes them). At least one stays on. */
    private fun toggleLanguage(preset: PlaylistPreset) {
        val s = _state.value
        if (preset.id in s.loadingPlaylists) return
        val turningOn = preset.id !in s.enabledPlaylists
        if (!turningOn && s.enabledPlaylists.size <= 1) {
            showToast(ToastMessage.KeepOneLanguage)
            return
        }
        val enabled = if (turningOn) s.enabledPlaylists + preset.id else s.enabledPlaylists - preset.id
        _state.update { it.copy(enabledPlaylists = enabled) }
        viewModelScope.launch {
            settingsRepository.setEnabledPlaylists(enabled)
            lineupMutex.withLock {
                if (!turningOn) {
                    playlistRepository.remove(preset.id)
                    return@withLock
                }
                _state.update { it.copy(loadingPlaylists = it.loadingPlaylists + preset.id) }
                playlistRepository.refresh(preset)
                    .onSuccess { count -> showToast(ToastMessage.LanguageAdded(preset.name, count)) }
                    .onFailure {
                        // Don't leave a language "on" that has no channels; the user can try again.
                        val current = settingsRepository.settings.first().enabledPlaylists
                        settingsRepository.setEnabledPlaylists(current - preset.id)
                        showToast(ToastMessage.LanguageFailed(preset.name))
                    }
                _state.update { it.copy(loadingPlaylists = it.loadingPlaylists - preset.id) }
            }
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

    private suspend fun startFirstChannel(channels: List<Channel>, ignoreLast: Boolean = false) {
        val settings = settingsRepository.settings.first()
        val last = if (ignoreLast) null else channels.firstOrNull { it.key == settings.lastChannelKey }
        val start = last ?: channels.first()
        tune(start.key, bannerMillis = FIRST_BANNER_MS)
        if (!settings.coachSeen) {
            showToast(ToastMessage.Coach, COACH_MS)
            settingsRepository.setCoachSeen()
        }
    }

    // ---------------------------------------------------------------- number entry

    private fun digit(value: Char) {
        var digits = _state.value.digits
        if (digits.length >= MAX_DIGITS) digits = ""
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

    /**
     * Downloads every turned-on language that is missing or more than a day old (all of them when
     * [force]), one after another in number order, so Malayalam is ready and playing first.
     * Languages that were turned off are removed.
     */
    private fun loadLineup(force: Boolean) {
        viewModelScope.launch {
            lineupMutex.withLock {
                val enabled = settingsRepository.settings.first().enabledPlaylists
                val presets = PlaylistPresets.all.filter { it.id in enabled }.ifEmpty { listOf(PlaylistPresets.Malayalam) }
                val wanted = presets.mapTo(HashSet()) { it.id }
                for (id in playlistRepository.storedIds() - wanted) playlistRepository.remove(id)

                val hadChannels = playlistRepository.channelCount() > 0
                if (!hadChannels) _state.update { it.copy(lineupStatus = LineupStatus.Loading) }
                var failed = false
                for (preset in presets) {
                    if (!force && !playlistRepository.isStale(preset)) continue
                    _state.update { it.copy(loadingPlaylists = it.loadingPlaylists + preset.id) }
                    playlistRepository.refresh(preset).onFailure { failed = true }
                    _state.update { it.copy(loadingPlaylists = it.loadingPlaylists - preset.id) }
                }

                val hasChannels = playlistRepository.channelCount() > 0
                when {
                    !hasChannels -> _state.update { it.copy(lineupStatus = LineupStatus.Error) }
                    force && failed -> showToast(ToastMessage.RefreshFailed)
                    force -> showToast(ToastMessage.Refreshed(channelRepository.channels.first().size))
                }
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
        /** Up to 5 digits: English (World) uses 10001 and up. */
        const val MAX_DIGITS = 5
    }
}
