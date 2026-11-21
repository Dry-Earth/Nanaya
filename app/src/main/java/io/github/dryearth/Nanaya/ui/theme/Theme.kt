package io.github.dryearth.Nanaya.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Glass {
    val Ink = Color(0xFF07080B)
    val InkSoft = Color(0xFF0B0E13)
    val Panel = Color(0xFF11151B)
    val PanelHi = Color(0xFF161B22)
    val Text = Color(0xFFEDF0F5)
    val Muted = Color(0xFFB4BECC)
    val Faint = Color(0xFF8E99A8)
    val Line = Color(0x1FFFFFFF)
    val LineSoft = Color(0x0DFFFFFF)
    val Accent = Color(0xFFAFC6FF)
    val AccentDim = Color(0x33AFC6FF)
    val Mint = Color(0xFF8ADBB4)
    val Amber = Color(0xFFF0C674)
    val Rose = Color(0xFFF28B8B)
    val Ice = Color(0xFFBFE6F2)
    val Violet = Color(0xFFC3B3FF)
}

private val Scheme = darkColorScheme(
    primary = Glass.Accent,
    onPrimary = Color(0xFF08101F),
    secondary = Glass.Mint,
    onSecondary = Color(0xFF06140D),
    tertiary = Glass.Amber,
    background = Glass.Ink,
    onBackground = Glass.Text,
    surface = Glass.Panel,
    onSurface = Glass.Text,
    surfaceVariant = Glass.PanelHi,
    onSurfaceVariant = Glass.Muted,
    outline = Glass.Line,
    error = Glass.Rose,
)

private val ButlerTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 44.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun SolidButlerTheme(content: @Composable () -> Unit) {
    // The material is designed dark; light mode keeps the same type scale for now.
    val dark = true || isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) Scheme else Scheme,
        typography = ButlerTypography,
        content = content,
    )
}
