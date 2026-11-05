package com.zaptv.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/** The TV palette (UI_UX_DESIGN.md, section 4). Gold and cream come from the kasavu saree border. */
object ZapColors {
    val TvBlack = Color(0xFF000000)
    val NightInk = Color(0xFF070B0D)
    val Solid = Color(0xF7050B0B)            // full-screen overlays, 97 %
    val Panel = Color(0xDB090F11)            // banner, cards, toasts, 86 %
    val Line = Color(0x17FFFFFF)             // 9 %
    val Chip = Color(0x1FFFFFFF)             // 12 %
    val LogoTile = Color(0x24FFFFFF)
    val KasavuWhite = Color(0xFFF4F1E8)
    val Mist = Color(0xFFA3AFAC)
    val KasavuGold = Color(0xFFE6BE5A)
    val FocusCream = Color(0xFFF4ECD6)
    val FocusInk = Color(0xFF17130A)
    val FocusMuted = Color(0xFF5E5440)
    val LiveRed = Color(0xFFE5484D)
    val WarnText = Color(0xFFFF9A9D)
    val WarnFill = Color(0x2EE5484D)
}

/** Overscan-safe margins: nothing important goes outside these. */
object ZapSpacing {
    val SafeHorizontal = 48.dp
    val SafeVertical = 27.dp
}

object ZapFonts {
    val Ui = FontFamily(
        Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
        Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
    )
    val Numbers = FontFamily(
        Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
        Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
    )
}

/** Type scale in TV dp (1 sp = 1 dp at the default font scale). */
object ZapType {
    private const val TABULAR = "tnum"
    val ChannelNumber = TextStyle(fontFamily = ZapFonts.Numbers, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, fontFeatureSettings = TABULAR)
    val Title = TextStyle(fontFamily = ZapFonts.Ui, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 31.sp)
    val BannerName = TextStyle(fontFamily = ZapFonts.Ui, fontWeight = FontWeight.Bold, fontSize = 21.sp)
    val RowTitle = TextStyle(fontFamily = ZapFonts.Ui, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    val Body = TextStyle(fontFamily = ZapFonts.Ui, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val Meta = TextStyle(fontFamily = ZapFonts.Ui, fontWeight = FontWeight.Normal, fontSize = 13.sp)
    val Label = TextStyle(fontFamily = ZapFonts.Ui, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.2.sp)
    val KeyCap = TextStyle(fontFamily = ZapFonts.Ui, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    val Clock = TextStyle(fontFamily = ZapFonts.Numbers, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, fontFeatureSettings = TABULAR)
    val RowNumber = TextStyle(fontFamily = ZapFonts.Numbers, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, fontFeatureSettings = TABULAR)
    val Digit = TextStyle(fontFamily = ZapFonts.Numbers, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, fontFeatureSettings = TABULAR)
    val Monogram = TextStyle(fontFamily = ZapFonts.Numbers, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
}

@Composable
fun ZapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ZapColors.KasavuGold,
            onPrimary = ZapColors.FocusInk,
            background = ZapColors.NightInk,
            onBackground = ZapColors.KasavuWhite,
            surface = ZapColors.NightInk,
            onSurface = ZapColors.KasavuWhite,
            onSurfaceVariant = ZapColors.Mist,
        ),
        typography = Typography(
            titleLarge = ZapType.Title,
            bodyLarge = ZapType.Body,
            bodyMedium = ZapType.Body,
            bodySmall = ZapType.Meta,
            labelSmall = ZapType.Label,
        ),
        content = content,
    )
}
