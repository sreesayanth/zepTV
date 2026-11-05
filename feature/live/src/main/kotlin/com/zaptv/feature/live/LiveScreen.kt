package com.zaptv.feature.live

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.zaptv.core.designsystem.GoldSpinner
import com.zaptv.core.designsystem.KeyHint
import com.zaptv.core.designsystem.ZapColors
import com.zaptv.core.designsystem.ZapFonts
import com.zaptv.core.designsystem.ZapType
import com.zaptv.core.player.PlaybackState

@Composable
fun LiveRoute(onExit: () -> Unit, viewModel: LiveViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnExit by rememberUpdatedState(onExit)
    LaunchedEffect(state.exitRequested) { if (state.exitRequested) currentOnExit() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    LiveScreen(state = state, player = viewModel.player, onAction = viewModel::onAction)
}

/**
 * The home screen is live TV. Everything else is an overlay drawn on top of the picture,
 * and every key goes through [RemoteKeyHandler] so the whole app works with a D-pad, OK and BACK.
 */
@Composable
fun LiveScreen(state: LiveUiState, player: Player, onAction: (RemoteAction) -> Unit) {
    val currentOnAction by rememberUpdatedState(onAction)
    val keyHandler = remember { RemoteKeyHandler { currentOnAction(it) } }
    val focusRequester = remember { FocusRequester() }
    BackHandler { currentOnAction(RemoteAction.Back) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ZapColors.TvBlack)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyHandler.handle(it.nativeKeyEvent) }
            .focusable(),
    ) {
        VideoSurface(player = player, aspect = state.aspect, modifier = Modifier.fillMaxSize())
        when (state.lineupStatus) {
            LineupStatus.Ready -> LiveOverlays(state)
            LineupStatus.Loading -> StartupLoading(Modifier.align(Alignment.Center))
            LineupStatus.Error -> StartupError(Modifier.align(Alignment.Center))
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun LiveOverlays(state: LiveUiState) {
    val current = state.current
    val bannerShown = state.overlay == Overlay.None &&
        (state.bannerVisible || state.pendingKey != null || state.isTuning)

    Box(Modifier.fillMaxSize()) {
        if (bannerShown) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(270.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
            )
        }

        if (current != null && state.pendingKey == null) {
            val playback = state.playback
            when {
                state.isTuning && playback is PlaybackState.Loading ->
                    TuningIndicator(current, playback, Modifier.align(Alignment.Center))
                state.isUnavailable ->
                    UnavailableCard(current, state.skipCountdown, Modifier.align(Alignment.Center))
            }
        }

        if (state.statsVisible && current != null) StatsOverlay(state, Modifier.align(Alignment.TopStart))

        AnimatedVisibility(
            visible = bannerShown && state.bannerChannel != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 10 },
            exit = fadeOut(tween(180)),
        ) {
            state.bannerChannel?.let { channel ->
                ChannelBanner(
                    channel = channel,
                    isFavourite = channel.key in state.favourites,
                    groupLabel = if (state.activeGroup == ChannelGroup.All) null else groupLabel(state.activeGroup),
                    expanded = state.infoExpanded,
                )
            }
        }

        if (state.digits.isNotEmpty() && state.overlay == Overlay.None) {
            NumberEntry(state.digits, state.channels, Modifier.align(Alignment.TopEnd))
        }

        when (val overlay = state.overlay) {
            is Overlay.ChannelList -> ChannelListOverlay(state, overlay)
            is Overlay.Options -> OptionsPanel(state, overlay, Modifier.align(Alignment.CenterEnd))
            is Overlay.Exit -> ExitDialog(overlay, Modifier.align(Alignment.Center))
            Overlay.None -> Unit
        }

        state.toast?.let { ToastPill(it, Modifier.align(Alignment.TopCenter)) }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurface(player: Player, aspect: Aspect, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false
                isFocusable = false
                isFocusableInTouchMode = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                // Keep the last frame on screen while the next channel loads, instead of flashing black.
                setKeepContentOnPlayerReset(true)
                keepScreenOn = true
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.resizeMode = when (aspect) {
                Aspect.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                Aspect.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                Aspect.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        onRelease = { view -> view.player = null },
    )
}

@Composable
private fun StartupLoading(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Wordmark()
        GoldSpinner(44.dp)
        Text(stringResource(R.string.loading_lineup), style = ZapType.Body, color = ZapColors.Mist)
    }
}

@Composable
private fun StartupError(modifier: Modifier = Modifier) {
    Column(
        modifier.width(560.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Wordmark()
        Text(stringResource(R.string.lineup_error_title), style = ZapType.Title, color = ZapColors.KasavuWhite, textAlign = TextAlign.Center)
        Text(stringResource(R.string.lineup_error_body), style = ZapType.Body, color = ZapColors.Mist, textAlign = TextAlign.Center)
        KeyHint(listOf("OK"), stringResource(R.string.try_again), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Wordmark() {
    Row {
        Text("Zap", style = ZapType.ChannelNumber.copy(fontFamily = ZapFonts.Numbers), color = ZapColors.KasavuWhite)
        Text("TV", style = ZapType.ChannelNumber.copy(fontFamily = ZapFonts.Numbers), color = ZapColors.KasavuGold)
    }
}
