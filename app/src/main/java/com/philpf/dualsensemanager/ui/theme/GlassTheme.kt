package com.philpf.dualsensemanager.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Flat "liquid glass" palette: white at different opacities over a dark, colourful backdrop. */
object GlassColors {
    val Background = Color(0xFF07080E)
    val BackgroundTop = Color(0xFF10142A)
    val TextPrimary = Color(0xF5FFFFFF)
    val TextSecondary = Color(0xA6FFFFFF)
    val TextTertiary = Color(0x70FFFFFF)
    val TextOnLight = Color(0xFF101219)
    val Hairline = Color(0x1AFFFFFF)

    /** Fill for controls sitting on a glass panel (buttons, chips, text fields). */
    val ControlFill = Color(0x1AFFFFFF)

    /** Track of sliders and switches. */
    val Track = Color(0x29FFFFFF)

    val Accent = Color(0xFF8DB3FF)
    val Danger = Color(0xFFFF7A7A)
    val Charging = Color(0xFF7CF0A0)
}

object GlassType {
    val largeTitle = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val title = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
    val callout = TextStyle(fontSize = 14.sp, lineHeight = 19.sp)
    val caption = TextStyle(fontSize = 13.sp, lineHeight = 17.sp)
    val section = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
}

/**
 * A light-bar colour made readable as a UI colour: very dark LED colours (deep blue, dimmed
 * colours) are lifted toward white so buttons and slider fills stay visible on glass.
 */
fun Color.forDisplay(minLuminance: Float = 0.22f): Color {
    val l = luminance()
    if (l >= minLuminance) return this
    val t = ((minLuminance - l) / (1f - l)).coerceIn(0f, 0.75f)
    return lerp(this, Color.White, t)
}

/** Text colour that reads on a solid fill of [background]. */
fun contentColorOn(background: Color): Color =
    if (background.luminance() > 0.45f) GlassColors.TextOnLight else Color.White

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = GlassColors.Accent,
        onPrimary = GlassColors.TextOnLight,
        background = GlassColors.Background,
        onBackground = GlassColors.TextPrimary,
        surface = GlassColors.Background,
        onSurface = GlassColors.TextPrimary,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalContentColor provides GlassColors.TextPrimary,
            LocalTextStyle provides GlassType.body,
            content = content,
        )
    }
}
