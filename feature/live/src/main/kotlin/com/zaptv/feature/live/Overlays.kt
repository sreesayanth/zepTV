package com.zaptv.feature.live

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.zaptv.core.designsystem.ChannelLogo
import com.zaptv.core.designsystem.Clock
import com.zaptv.core.designsystem.GoldSpinner
import com.zaptv.core.designsystem.KeyCap
import com.zaptv.core.designsystem.KeyHint
import com.zaptv.core.designsystem.OneLine
import com.zaptv.core.designsystem.Pill
import com.zaptv.core.designsystem.ZapColors
import com.zaptv.core.designsystem.ZapSpacing
import com.zaptv.core.designsystem.ZapType
import com.zaptv.core.designsystem.zapFocus
import com.zaptv.core.designsystem.zapPanel
import com.zaptv.core.model.Channel
import com.zaptv.core.model.PlaylistPresets
import com.zaptv.core.player.PlaybackState

// ------------------------------------------------------------------ banner

@Composable
internal fun ChannelBanner(
    channel: Channel,
    isFavourite: Boolean,
    groupLabel: String?,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .padding(horizontal = ZapSpacing.SafeHorizontal, vertical = ZapSpacing.SafeVertical)
            .fillMaxWidth()
            .zapPanel(RoundedCornerShape(16.dp))
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ChannelLogo(channel.logoUrl, channel.monogram, channel.hue, 72.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(channel.number.toString(), style = ZapType.ChannelNumber, color = ZapColors.KasavuGold)
                    OneLine(channel.name, ZapType.BannerName, ZapColors.KasavuWhite, Modifier.weight(1f, fill = false))
                    if (channel.isHd) Badge(stringResource(R.string.hd))
                    if (isFavourite) Text("★", style = ZapType.BannerName.copy(fontSize = 18.sp), color = ZapColors.KasavuGold)
                    if (groupLabel != null) Chip(groupLabel)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(stringResource(R.string.live), ZapColors.LiveRed, Color.White)
                    OneLine(channelDetails(channel), ZapType.Body, ZapColors.Mist)
                }
                if (expanded) {
                    Text(
                        text = listOfNotNull(
                            channel.groupTitle.takeIf { it.isNotBlank() },
                            sourcesLabel(channel.sources.size),
                            channel.tvgId,
                        ).joinToString(" · "),
                        style = ZapType.Meta,
                        color = ZapColors.Mist,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Clock()
                Clock(style = ZapType.Meta, color = ZapColors.Mist, pattern = "EEE d MMM")
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(ZapColors.Line))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            KeyHint(listOf("▲", "▼"), stringResource(R.string.hint_channels))
            KeyHint(listOf("OK"), stringResource(R.string.hint_channel_list))
            KeyHint(listOf("▶"), stringResource(R.string.hint_options))
            KeyHint(listOf("BACK"), stringResource(R.string.hint_previous_channel))
        }
    }
}

@Composable
private fun Badge(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ZapColors.Chip)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = ZapType.Label.copy(fontSize = 11.sp), color = ZapColors.Mist)
    }
}

@Composable
private fun Chip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(ZapColors.Chip)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, style = ZapType.Meta, color = ZapColors.KasavuWhite, maxLines = 1)
    }
}

internal fun channelDetails(channel: Channel): String =
    (listOf(channel.category.label) + listOfNotNull(channel.quality) + channel.labels).joinToString(" · ")

@Composable
private fun sourcesLabel(count: Int): String =
    if (count == 1) stringResource(R.string.one_source) else stringResource(R.string.sources_count, count)

@Composable
internal fun groupLabel(group: ChannelGroup): String = when (group) {
    ChannelGroup.Favourites -> stringResource(R.string.group_favourites)
    ChannelGroup.All -> stringResource(R.string.group_all)
    is ChannelGroup.OfCategory -> group.category.label
    is ChannelGroup.OfPlaylist -> PlaylistPresets.byId(group.playlistId)?.name ?: group.playlistId
}

// ------------------------------------------------------------------ loading and failure

@Composable
internal fun TuningIndicator(channel: Channel, playback: PlaybackState.Loading, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(bottom = 90.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            GoldSpinner(112.dp)
            ChannelLogo(channel.logoUrl, channel.monogram, channel.hue, 72.dp)
        }
        val text = if (playback.attempt > 0) {
            stringResource(R.string.trying_source, playback.attempt + 1, playback.sourceCount)
        } else {
            stringResource(R.string.loading_channel, channel.number, channel.name)
        }
        Text(text, style = ZapType.Body, color = ZapColors.Mist)
    }
}

@Composable
internal fun UnavailableCard(channel: Channel, countdown: Int?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(bottom = 90.dp)
            .width(540.dp)
            .zapPanel(RoundedCornerShape(18.dp))
            .padding(horizontal = 30.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Pill(stringResource(R.string.unavailable), ZapColors.WarnFill, ZapColors.WarnText)
        Text(stringResource(R.string.unavailable_title, channel.name), style = ZapType.Title, color = ZapColors.KasavuWhite)
        Text(stringResource(R.string.unavailable_body), style = ZapType.Body, color = ZapColors.Mist)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            KeyHint(listOf("▲", "▼"), stringResource(R.string.change_channel), color = ZapColors.KasavuWhite)
            KeyHint(listOf("OK"), stringResource(R.string.try_again), color = ZapColors.KasavuWhite)
        }
        if (countdown != null) {
            Text(stringResource(R.string.next_channel_in, countdown), style = ZapType.RowTitle, color = ZapColors.KasavuGold)
        }
    }
}

// ------------------------------------------------------------------ number entry, toast, stats

@Composable
internal fun NumberEntry(digits: String, channels: List<Channel>, modifier: Modifier = Modifier) {
    val number = digits.toIntOrNull()
    val exact = channels.firstOrNull { it.number == number }
    val candidates = if (exact == null) channels.filter { it.number.toString().startsWith(digits) } else emptyList()
    val progress = remember(digits) { Animatable(1f) }
    LaunchedEffect(digits) { progress.animateTo(0f, tween(2_000, easing = LinearEasing)) }

    Column(
        modifier
            .padding(top = ZapSpacing.SafeVertical, end = ZapSpacing.SafeHorizontal)
            .zapPanel(RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .widthIn(min = 200.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // 3 boxes is enough for Malayalam (1–999); other languages use 4 (Tamil 2101), English (World) 5.
            repeat(maxOf(3, digits.length)) { i ->
                val d = digits.getOrNull(i)
                Box(
                    Modifier.size(width = 38.dp, height = 56.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(d?.toString() ?: "–", style = ZapType.Digit, color = if (d != null) ZapColors.KasavuGold else Color.White.copy(alpha = 0.18f))
                }
            }
        }
        when {
            exact != null -> OneLine(exact.name, ZapType.RowTitle, ZapColors.KasavuWhite)
            candidates.isNotEmpty() -> Text(
                candidates.take(3).joinToString(", ") { it.number.toString() } + if (candidates.size > 3) "…" else "",
                style = ZapType.Meta,
                color = ZapColors.Mist,
            )
            else -> Text(stringResource(R.string.no_channel, digits), style = ZapType.Meta, color = ZapColors.Mist)
        }
        Box(Modifier.width(132.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).background(ZapColors.KasavuGold))
        }
    }
}

@Composable
internal fun ToastPill(message: ToastMessage, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(top = ZapSpacing.SafeVertical)
            .zapPanel(RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (message) {
            ToastMessage.Coach -> {
                KeyHint(listOf("▲", "▼"), stringResource(R.string.hint_channels), color = ZapColors.KasavuWhite)
                Text("·", style = ZapType.Body, color = ZapColors.Mist)
                KeyHint(listOf("OK"), stringResource(R.string.hint_channel_list), color = ZapColors.KasavuWhite)
                Text("·", style = ZapType.Body, color = ZapColors.Mist)
                KeyHint(listOf("▶"), stringResource(R.string.hint_options), color = ZapColors.KasavuWhite)
            }
            else -> Text(toastText(message), style = ZapType.Body, color = ZapColors.KasavuWhite, maxLines = 1)
        }
    }
}

@Composable
private fun toastText(message: ToastMessage): String = when (message) {
    ToastMessage.Coach -> ""
    is ToastMessage.AddedFavourite -> stringResource(R.string.added_favourite, message.name)
    is ToastMessage.RemovedFavourite -> stringResource(R.string.removed_favourite, message.name)
    is ToastMessage.BackTo -> stringResource(R.string.back_to, message.name)
    is ToastMessage.NoChannel -> stringResource(R.string.no_channel, message.digits)
    is ToastMessage.Refreshed -> stringResource(R.string.refreshed, message.count)
    ToastMessage.RefreshFailed -> stringResource(R.string.refresh_failed)
    is ToastMessage.LanguageAdded -> stringResource(R.string.language_added, message.name, message.count)
    is ToastMessage.LanguageFailed -> stringResource(R.string.language_failed, message.name)
    ToastMessage.KeepOneLanguage -> stringResource(R.string.keep_one_language)
}

@Composable
internal fun StatsOverlay(state: LiveUiState, modifier: Modifier = Modifier) {
    val stats = state.stats
    val zap = stats.zapMillis?.let { "$it ms" } ?: "…"
    Box(
        modifier
            .padding(start = ZapSpacing.SafeHorizontal, top = ZapSpacing.SafeVertical)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_line, zap, stats.videoSize ?: "–", stats.sourceIndex + 1, stats.sourceCount),
            style = ZapType.Meta.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            color = Color(0xFFCFE3DA),
        )
    }
}

// ------------------------------------------------------------------ channel list

@Composable
internal fun ChannelListOverlay(state: LiveUiState, overlay: Overlay.ChannelList) {
    val groups = state.groups
    val groupIndex = overlay.groupIndex.coerceIn(0, groups.lastIndex)
    val group = groups[groupIndex]
    val channels = state.channelsIn(group)
    val focused = overlay.focusedIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))

    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxHeight()
                .width(560.dp)
                .background(Brush.horizontalGradient(0.7f to ZapColors.Solid, 1f to ZapColors.Solid.copy(alpha = 0.9f))),
        ) {
            Column(
                Modifier.width(172.dp).fillMaxHeight().padding(start = 40.dp, end = 10.dp, top = ZapSpacing.SafeVertical, bottom = ZapSpacing.SafeVertical),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.groups).uppercase(),
                    style = ZapType.Label,
                    color = ZapColors.Mist,
                    modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 8.dp),
                )
                // With several languages there are more groups than fit on screen, so the column scrolls.
                val groupListState = rememberLazyListState()
                LaunchedEffect(groupIndex) { groupListState.scrollToItem((groupIndex - 4).coerceAtLeast(0)) }
                LazyColumn(
                    state = groupListState,
                    userScrollEnabled = false,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(groups) { index, g ->
                        GroupRow(
                            label = groupLabel(g),
                            count = state.channelsIn(g).size,
                            focused = overlay.onGroups && index == groupIndex,
                            selected = !overlay.onGroups && index == groupIndex,
                        )
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(ZapColors.Line))
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(start = 14.dp, end = 18.dp, top = ZapSpacing.SafeVertical, bottom = 20.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OneLine(groupLabel(group), ZapType.BannerName, ZapColors.KasavuWhite, Modifier.weight(1f))
                    Clock(style = ZapType.RowNumber, color = ZapColors.Mist)
                }
                Spacer(Modifier.height(8.dp))
                if (channels.isEmpty()) {
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.favourites_empty_title), style = ZapType.RowTitle.copy(fontSize = 17.sp), color = ZapColors.KasavuWhite)
                        Text(stringResource(R.string.favourites_empty_body), style = ZapType.Body, color = ZapColors.Mist)
                    }
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(groupIndex, focused) { listState.scrollToItem((focused - 3).coerceAtLeast(0)) }
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = false,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                    ) {
                        itemsIndexed(channels, key = { _, c -> c.key }) { index, channel ->
                            ChannelRow(
                                channel = channel,
                                focused = !overlay.onGroups && index == focused,
                                playing = channel.key == state.currentKey,
                                favourite = channel.key in state.favourites,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KeyHint(listOf("OK"), stringResource(R.string.watch))
                    KeyHint(listOf("◀"), stringResource(R.string.groups))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.hold), style = ZapType.Meta, color = ZapColors.Mist)
                        KeyCap("OK")
                        Text(stringResource(R.string.favourite), style = ZapType.Meta, color = ZapColors.Mist)
                    }
                }
            }
        }
        if (!overlay.onGroups) {
            channels.getOrNull(focused)?.let { PeekCard(it, Modifier.align(Alignment.BottomEnd)) }
        }
    }
}

@Composable
private fun GroupRow(label: String, count: Int, focused: Boolean, selected: Boolean) {
    val color = when {
        focused -> ZapColors.FocusInk
        selected -> ZapColors.KasavuGold
        else -> ZapColors.KasavuWhite
    }
    Row(
        Modifier.fillMaxWidth().zapFocus(focused, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneLine(label, if (selected || focused) ZapType.RowTitle else ZapType.Body, color, Modifier.weight(1f))
        Text(count.toString(), style = ZapType.RowNumber.copy(fontSize = 13.sp), color = if (focused) ZapColors.FocusMuted else if (selected) ZapColors.KasavuGold else ZapColors.Mist)
    }
}

@Composable
private fun ChannelRow(channel: Channel, focused: Boolean, playing: Boolean, favourite: Boolean) {
    val primary = if (focused) ZapColors.FocusInk else ZapColors.KasavuWhite
    val secondary = if (focused) ZapColors.FocusMuted else ZapColors.Mist
    Row(
        Modifier.fillMaxWidth().height(46.dp).zapFocus(focused).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            channel.number.toString(),
            style = ZapType.RowNumber,
            color = when {
                focused -> ZapColors.FocusMuted
                playing -> ZapColors.KasavuGold
                else -> ZapColors.Mist
            },
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
        ChannelLogo(channel.logoUrl, channel.monogram, channel.hue, 30.dp)
        Column(Modifier.weight(1f)) {
            OneLine(channel.name, ZapType.RowTitle, primary)
            OneLine(channelDetails(channel), ZapType.Meta, secondary)
        }
        if (favourite) Text("★", style = ZapType.RowTitle, color = if (focused) ZapColors.FocusInk else ZapColors.KasavuGold)
        if (playing) PlayingBars(if (focused) ZapColors.FocusInk else ZapColors.KasavuGold)
    }
}

/** Three moving bars that mark the channel that is playing. */
@Composable
private fun PlayingBars(color: Color) {
    val transition = rememberInfiniteTransition(label = "bars")
    Row(Modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(0, 300, 550).forEach { offset ->
            val scale by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(800, delayMillis = offset, easing = LinearEasing), RepeatMode.Reverse),
                label = "bar",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .background(color),
            )
        }
    }
}

@Composable
private fun PeekCard(channel: Channel, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(end = ZapSpacing.SafeHorizontal, bottom = ZapSpacing.SafeVertical)
            .width(330.dp)
            .zapPanel(RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "${stringResource(R.string.now).uppercase()} · ${channel.number}",
            style = ZapType.Label,
            color = ZapColors.KasavuGold,
        )
        OneLine(channel.name, ZapType.RowTitle.copy(fontSize = 17.sp), ZapColors.KasavuWhite)
        Text(channelDetails(channel), style = ZapType.Meta, color = ZapColors.Mist)
        Text(
            listOfNotNull(channel.groupTitle.takeIf { it.isNotBlank() }, sourcesLabel(channel.sources.size)).joinToString(" · "),
            style = ZapType.Meta,
            color = ZapColors.Mist,
        )
    }
}

// ------------------------------------------------------------------ options

@Composable
internal fun OptionsPanel(state: LiveUiState, overlay: Overlay.Options, modifier: Modifier = Modifier) {
    val channel = state.current
    Column(
        modifier
            .fillMaxHeight()
            .width(340.dp)
            .background(Brush.horizontalGradient(0f to ZapColors.Solid.copy(alpha = 0.9f), 0.3f to ZapColors.Solid))
            .padding(start = 22.dp, end = 40.dp, top = ZapSpacing.SafeVertical, bottom = 24.dp),
    ) {
        if (channel != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChannelLogo(channel.logoUrl, channel.monogram, channel.hue, 40.dp)
                Column {
                    OneLine("${channel.number} ${channel.name}", ZapType.RowTitle.copy(fontSize = 17.sp), ZapColors.KasavuWhite)
                    Text(stringResource(R.string.options), style = ZapType.Meta, color = ZapColors.Mist)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            OptionItem.entries.forEachIndexed { index, item ->
                val isFavourite = channel != null && channel.key in state.favourites
                val (label, value) = when (item) {
                    OptionItem.Favourite -> stringResource(if (isFavourite) R.string.option_remove_favourite else R.string.option_add_favourite) to null
                    OptionItem.PictureSize -> stringResource(R.string.option_picture_size) to stringResource(
                        when (state.aspect) {
                            Aspect.Fit -> R.string.aspect_fit
                            Aspect.Fill -> R.string.aspect_fill
                            Aspect.Zoom -> R.string.aspect_zoom
                        }
                    )
                    OptionItem.UpButton -> stringResource(R.string.option_up_button) to
                        stringResource(if (state.upIsNextChannel) R.string.up_next else R.string.up_previous)
                    OptionItem.Stats -> stringResource(R.string.option_stats) to
                        stringResource(if (state.statsVisible) R.string.on else R.string.off)
                    OptionItem.Languages -> stringResource(R.string.option_languages) to
                        stringResource(R.string.languages_on_count, state.enabledPlaylists.count { PlaylistPresets.byId(it) != null })
                    OptionItem.Reload -> stringResource(R.string.option_reload) to null
                }
                OptionRow(label, value, focused = index == overlay.focusedIndex)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KeyHint(listOf("▲", "▼"), stringResource(R.string.move))
            KeyHint(listOf("OK"), stringResource(R.string.select))
            KeyHint(listOf("BACK"), stringResource(R.string.close))
        }
    }
}

// ------------------------------------------------------------------ languages

@Composable
internal fun LanguagesPanel(state: LiveUiState, overlay: Overlay.Languages, modifier: Modifier = Modifier) {
    val presets = PlaylistPresets.all
    val focused = overlay.focusedIndex.coerceIn(0, presets.lastIndex)
    Column(
        modifier
            .fillMaxHeight()
            .width(380.dp)
            .background(Brush.horizontalGradient(0f to ZapColors.Solid.copy(alpha = 0.9f), 0.3f to ZapColors.Solid))
            .padding(start = 22.dp, end = 40.dp, top = ZapSpacing.SafeVertical, bottom = 24.dp),
    ) {
        Text(stringResource(R.string.option_languages), style = ZapType.RowTitle.copy(fontSize = 17.sp), color = ZapColors.KasavuWhite)
        Text(stringResource(R.string.languages_subtitle), style = ZapType.Meta, color = ZapColors.Mist)
        Spacer(Modifier.height(12.dp))
        val listState = rememberLazyListState()
        LaunchedEffect(focused) { listState.scrollToItem((focused - 4).coerceAtLeast(0)) }
        LazyColumn(
            state = listState,
            userScrollEnabled = false,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            itemsIndexed(presets, key = { _, p -> p.id }) { index, preset ->
                val label = if (preset.nativeName.isEmpty()) preset.name else "${preset.name} · ${preset.nativeName}"
                val value = when {
                    preset.id in state.loadingPlaylists -> stringResource(R.string.language_loading)
                    preset.id in state.enabledPlaylists -> stringResource(R.string.language_on_from, preset.numberBase + 1)
                    else -> stringResource(R.string.off)
                }
                OptionRow(label, value, focused = index == focused)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KeyHint(listOf("▲", "▼"), stringResource(R.string.move))
            KeyHint(listOf("OK"), stringResource(R.string.language_toggle))
            KeyHint(listOf("BACK"), stringResource(R.string.back))
        }
    }
}

@Composable
private fun OptionRow(label: String, value: String?, focused: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).zapFocus(focused).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneLine(label, ZapType.Body, if (focused) ZapColors.FocusInk else ZapColors.KasavuWhite, Modifier.weight(1f))
        if (value != null) {
            Text(
                if (focused) "‹ $value ›" else value,
                style = ZapType.Meta,
                color = if (focused) ZapColors.FocusMuted else ZapColors.Mist,
                maxLines = 1,
            )
        }
    }
}

// ------------------------------------------------------------------ exit

@Composable
internal fun ExitDialog(overlay: Overlay.Exit, modifier: Modifier = Modifier) {
    Column(
        modifier.width(440.dp).zapPanel(RoundedCornerShape(18.dp)).padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.exit_question), style = ZapType.Title, color = ZapColors.KasavuWhite)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DialogButton(stringResource(R.string.keep_watching), focused = !overlay.exitFocused)
            DialogButton(stringResource(R.string.exit), focused = overlay.exitFocused)
        }
    }
}

@Composable
private fun DialogButton(label: String, focused: Boolean) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .zapFocus(focused, shape)
            .then(if (focused) Modifier else Modifier.background(ZapColors.Chip))
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(label, style = ZapType.RowTitle, color = if (focused) ZapColors.FocusInk else ZapColors.KasavuWhite)
    }
}
