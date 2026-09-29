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

// 1. Minimalist Aydınlık (Nordic Paper / Minimalist Studio)
val LightColors = CsReaderColors(
    bg = Color(0xFFF8F9FA),
    cardBg = Color(0xFFFFFFFF),
    text = Color(0xFF111827),
    textMuted = Color(0xFF6B7280),
    border = Color(0xFFE5E7EB),
    primary = Color(0xFF4F46E5), // Modern Electric Indigo
    accent = Color(0xFFEEF2FF)
)

// 2. Modern Karanlık (Obsidian Midnight / Linear Dark)
val DarkColors = CsReaderColors(
    bg = Color(0xFF0B0F17),
    cardBg = Color(0xFF131B2B),
    text = Color(0xFFF1F5F9),
    textMuted = Color(0xFF94A3B8),
    border = Color(0xFF1E293B),
    primary = Color(0xFF818CF8),
    accent = Color(0xFF1E293B)
)

// 3. Saf AMOLED (OLED Pure Black - Sıfır Işık & Pil Tasarrufu)
val AmoledColors = CsReaderColors(
    bg = Color(0xFF000000),
    cardBg = Color(0xFF0E0E10),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFFA1A1AA),
    border = Color(0xFF222226),
    primary = Color(0xFF818CF8),
    accent = Color(0xFF18181B)
)

// 4. Sıcak Sepya (Warm Bookshelf / Italian Cream)
val SepiaColors = CsReaderColors(
    bg = Color(0xFFF7F2E7),
    cardBg = Color(0xFFFCF9F2),
    text = Color(0xFF3F2E1E),
    textMuted = Color(0xFF82705E),
    border = Color(0xFFEADBCE),
    primary = Color(0xFFC2410C),
    accent = Color(0xFFFBF4E8)
)

// 5. Doğa Yeşili (Nordic Sage / Zen Forest)
val ForestColors = CsReaderColors(
    bg = Color(0xFFEFF4F0),
    cardBg = Color(0xFFF8FAF8),
    text = Color(0xFF172D1E),
    textMuted = Color(0xFF566E5C),
    border = Color(0xFFD6E3D8),
    primary = Color(0xFF059669),
    accent = Color(0xFFE8F3EB)
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
    val colors = when (themeName.lowercase()) {
        "amoled" -> AmoledColors
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
