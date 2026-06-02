package com.anonymous.csreader.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class CsReaderColors(
    val bg: Color,
    val cardBg: Color,
    val text: Color,
    val textMuted: Color,
    val border: Color,
    val primary: Color,
    val accent: Color
)

val LightColors = CsReaderColors(
    bg = Color(0xFFF3F4F6),
    cardBg = Color(0xFFFFFFFF),
    text = Color(0xFF1F2937),
    textMuted = Color(0xFF6B7280),
    border = Color(0xFFE5E7EB),
    primary = Color(0xFF3B82F6),
    accent = Color(0xFFEFF6FF)
)

val DarkColors = CsReaderColors(
    bg = Color(0xFF0F172A),
    cardBg = Color(0xFF1E293B),
    text = Color(0xFFF8FAFC),
    textMuted = Color(0xFF94A3B8),
    border = Color(0xFF334155),
    primary = Color(0xFF60A5FA),
    accent = Color(0xFF1E293B)
)

val SepiaColors = CsReaderColors(
    bg = Color(0xFFF4ECD8),
    cardBg = Color(0xFFFAF6EB),
    text = Color(0xFF5C4033),
    textMuted = Color(0xFF8C7768),
    border = Color(0xFFE3D7C1),
    primary = Color(0xFFB45309),
    accent = Color(0xFFFAF4E3)
)

val ForestColors = CsReaderColors(
    bg = Color(0xFFE8EFE9),
    cardBg = Color(0xFFF3F7F2),
    text = Color(0xFF223821),
    textMuted = Color(0xFF5D735C),
    border = Color(0xFFD2DEC5),
    primary = Color(0xFF15803D),
    accent = Color(0xFFEEF4EC)
)

val LocalCsReaderColors = staticCompositionLocalOf { LightColors }

object CsReaderTheme {
    val colors: CsReaderColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCsReaderColors.current
}

@Composable
fun CsReaderTheme(
    themeName: String,
    content: @Composable () -> Unit
) {
    val colors = when (themeName) {
        "dark" -> DarkColors
        "sepia" -> SepiaColors
        "forest" -> ForestColors
        else -> LightColors
    }

    CompositionLocalProvider(
        LocalCsReaderColors provides colors,
        content = content
    )
}
