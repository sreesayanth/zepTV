package com.zaptv.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Translucent panel used by the banner, cards and toasts. */
fun Modifier.zapPanel(shape: Shape = RoundedCornerShape(16.dp)): Modifier =
    this.clip(shape).background(ZapColors.Panel).border(1.dp, ZapColors.Line, shape)

/**
 * The focus look from the design spec: cream fill, 2 dp gold ring, 3 % lift.
 * Focus is drawn from app state (not the Compose focus system) so it matches the remote logic exactly.
 */
fun Modifier.zapFocus(focused: Boolean, shape: Shape = RoundedCornerShape(10.dp), scale: Float = 1.03f): Modifier =
    if (focused) {
        this.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(ZapColors.FocusCream)
            .border(2.dp, ZapColors.KasavuGold, shape)
    } else {
        this.clip(shape)
    }

/** Channel logo, with a coloured monogram while it loads or when there is no logo. */
@Composable
fun ChannelLogo(
    logoUrl: String?,
    monogram: String,
    hue: Float,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var loaded by remember(logoUrl) { mutableStateOf(false) }
    val shape = RoundedCornerShape(size * 0.22f)
    val background = if (loaded) Modifier.background(ZapColors.LogoTile) else Modifier.background(monogramBrush(hue))
    Box(
        modifier = modifier.size(size).clip(shape).then(background),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                text = monogram,
                style = ZapType.Monogram.copy(fontSize = (size.value * 0.38f).sp),
                color = Color.White,
                maxLines = 1,
            )
        }
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(size * 0.1f),
                onSuccess = { loaded = true },
                onError = { loaded = false },
            )
        }
    }
}

private fun monogramBrush(hue: Float) = Brush.linearGradient(
    listOf(
        Color.hsl(hue, 0.62f, 0.46f),
        Color.hsl((hue + 28f) % 360f, 0.68f, 0.27f),
    )
)

/** A small key label, like the ones printed on a remote: OK, BACK, ▲. */
@Composable
fun KeyCap(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(ZapColors.Chip)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ZapType.KeyCap, color = ZapColors.KasavuWhite)
    }
}

/** "[▲][▼] Channels": keys followed by what they do. */
@Composable
fun KeyHint(keys: List<String>, label: String, color: Color = ZapColors.Mist, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        keys.forEach { KeyCap(it) }
        Text(label, style = ZapType.Meta, color = color, modifier = Modifier.padding(start = 3.dp), maxLines = 1)
    }
}

/** Uppercase pill such as LIVE or UNAVAILABLE. */
@Composable
fun Pill(text: String, background: Color, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(5.dp)).background(background).padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text.uppercase(Locale.getDefault()), style = ZapType.Label.copy(fontSize = 11.sp), color = color)
    }
}

/** Gold spinner ring shown while a channel loads. */
@Composable
fun GoldSpinner(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Canvas(modifier = modifier.size(size).rotate(angle)) {
        val stroke = 4.dp.toPx()
        drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, useCenter = false, style = Stroke(stroke))
        drawArc(ZapColors.KasavuGold, -90f, 70f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Live clock, updated every 10 seconds. */
@Composable
fun Clock(style: TextStyle = ZapType.Clock, color: Color = ZapColors.KasavuWhite, pattern: String = "h:mm a") {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }
    val format = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }
    val text = format.format(Date(now))
    // "9:42 pm" reads more calmly than "9:42 PM"; only times get lower-cased, not dates.
    Text(if ('a' in pattern) text.lowercase(Locale.getDefault()) else text, style = style, color = color, maxLines = 1)
}

/** One-line text that ends in "…" instead of being cut off. */
@Composable
fun OneLine(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Thin progress bar. */
@Composable
fun ProgressLine(fraction: Float, width: Dp, modifier: Modifier = Modifier, color: Color = ZapColors.KasavuGold) {
    Box(modifier.size(width = width, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.14f))) {
        Box(Modifier.fillMaxHeight().width(width * fraction.coerceIn(0f, 1f)).background(color))
    }
}
