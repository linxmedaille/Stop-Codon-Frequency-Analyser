package com.tapflow.autoclicker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tapflow.core.ThemeMode

@Immutable
data class TapflowColors(
    val isDark: Boolean,
    /** Flat background colour under the aurora. */
    val base: Color,
    /** Aurora palettes, one per dock page (Single, Multi, Chain, Settings). */
    val aurora: List<List<Color>>,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    /** Glass fill tints. */
    val glass: Color,
    val glassStrong: Color,
    val glassFallback: Color,
    /** Specular rim: bright at the top-left, fading towards the bottom-right. */
    val rimLight: Color,
    val rimDark: Color,
    val shadow: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    /** The dark selected pill, like the selected filter chip in the reference. */
    val chip: Color,
    val onChip: Color,
    val hairline: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val scrim: Color,
)

val LightColors = TapflowColors(
    isDark = false,
    base = Color(0xFFF4F1EC),
    aurora = listOf(
        listOf(Color(0xFFC9B8FF), Color(0xFFFFD2BA), Color(0xFFFFC4DD), Color(0xFFBCD6FF)),
        listOf(Color(0xFFFFC6A0), Color(0xFFFFB3BC), Color(0xFFFFE3A0), Color(0xFFD8C9FF)),
        listOf(Color(0xFFA6E7D0), Color(0xFFB4D6FF), Color(0xFFD6F0A0), Color(0xFFCDC0FF)),
        listOf(Color(0xFFBED2FF), Color(0xFFDCDDE6), Color(0xFFD3C8FF), Color(0xFFC0EDDD)),
    ),
    ink = Color(0xFF131313),
    inkSecondary = Color(0xFF5D5955),
    inkTertiary = Color(0xFF99948E),
    glass = Color(0x8CFFFFFF),
    glassStrong = Color(0xC7FFFFFF),
    glassFallback = Color(0xF0FBFAF8),
    rimLight = Color(0xF2FFFFFF),
    rimDark = Color(0x33FFFFFF),
    shadow = Color(0x2E2A2140),
    accent = Color(0xFF5A2B82),
    onAccent = Color.White,
    accentSoft = Color(0x1F5A2B82),
    chip = Color(0xFF3A3A3C),
    onChip = Color.White,
    hairline = Color(0x1A000000),
    success = Color(0xFF1FA463),
    warning = Color(0xFFE08A00),
    danger = Color(0xFFE5484D),
    scrim = Color(0x52141018),
)

val DarkColors = TapflowColors(
    isDark = true,
    base = Color(0xFF0B0B0F),
    aurora = listOf(
        listOf(Color(0xFF5B3DF5), Color(0xFFE0558F), Color(0xFF2E6BFF), Color(0xFF8E4DF0)),
        listOf(Color(0xFFF06A35), Color(0xFFE6406E), Color(0xFFE8A015), Color(0xFF7B4DF0)),
        listOf(Color(0xFF14B890), Color(0xFF2A6CF0), Color(0xFF79BF22), Color(0xFF5B3DF5)),
        listOf(Color(0xFF3360F0), Color(0xFF4C5670), Color(0xFF6E4DF0), Color(0xFF14A38A)),
    ),
    ink = Color(0xFFF5F5F7),
    inkSecondary = Color(0xFFB1B0B8),
    inkTertiary = Color(0xFF75747C),
    glass = Color(0x24FFFFFF),
    glassStrong = Color(0x3DFFFFFF),
    glassFallback = Color(0xF21C1B22),
    rimLight = Color(0x80FFFFFF),
    rimDark = Color(0x14FFFFFF),
    shadow = Color(0x66000000),
    accent = Color(0xFFC4A2FF),
    onAccent = Color(0xFF1C0D33),
    accentSoft = Color(0x2EC4A2FF),
    chip = Color(0xFFF2F2F4),
    onChip = Color(0xFF141414),
    hairline = Color(0x1FFFFFFF),
    success = Color(0xFF3DDC84),
    warning = Color(0xFFFFB224),
    danger = Color(0xFFFF6369),
    scrim = Color(0x8C000000),
)

object TfType {
    /** Big profile-style page titles. */
    val display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.2).sp,
    )

    /** Editorial serif for statements and big numbers. */
    val editorial = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.6).sp,
    )

    val number = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 46.sp,
        lineHeight = 50.sp,
        letterSpacing = (-1.4).sp,
    )

    val title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.3).sp,
    )

    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
    )

    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp,
    )

    val caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    )

    val kicker = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp,
    )

    val block = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.2).sp,
    )
}

val LocalTapflowColors = staticCompositionLocalOf { LightColors }

object Tf {
    val colors: TapflowColors
        @Composable @ReadOnlyComposable get() = LocalTapflowColors.current
}

@Composable
fun isDarkFor(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun TapflowTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(primary = colors.accent, onPrimary = colors.onAccent, background = colors.base, surface = colors.base)
    } else {
        lightColorScheme(primary = colors.accent, onPrimary = colors.onAccent, background = colors.base, surface = colors.base)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalTapflowColors provides colors,
            LocalContentColor provides colors.ink,
            content = content,
        )
    }
}
