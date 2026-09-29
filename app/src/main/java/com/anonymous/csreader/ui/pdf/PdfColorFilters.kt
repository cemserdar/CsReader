package com.anonymous.csreader.ui.pdf

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

object PdfColorFilters {
    // Pure AMOLED Invert: White page (#FFFFFF) -> Pure Black (#000000), Black text (#000000) -> Pure White (#FFFFFF)
    val AmoledInvertFilter = ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f,  0f,  0f, 0f, 255f,
                 0f, -1f,  0f, 0f, 255f,
                 0f,  0f, -1f, 0f, 255f,
                 0f,  0f,  0f, 1f,   0f
            )
        )
    )

    // Soft Dark Invert: Göz yormayan koyu gri zemin (#1E1E1E), yumuşak beyaz yazı (#E0E0E0)
    val DarkInvertFilter = ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -0.85f, 0f,     0f,     0f, 225f,
                0f,     -0.85f, 0f,     0f, 225f,
                0f,      0f,    -0.85f, 0f, 225f,
                0f,      0f,     0f,    1f,   0f
            )
        )
    )

    // Warm Sepia Filter: Sıcak kitap kağıdı tonu
    val SepiaFilter = ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.393f * 1.05f, 0.769f * 0.95f, 0.189f * 0.85f, 0f, 20f,
                0.349f * 0.95f, 0.686f * 0.95f, 0.168f * 0.80f, 0f, 10f,
                0.272f * 0.85f, 0.534f * 0.80f, 0.131f * 0.70f, 0f, -15f,
                0f,             0f,             0f,             1f, 0f
            )
        )
    )

    fun getFilter(themeName: String): ColorFilter? {
        return when (themeName.lowercase()) {
            "amoled" -> AmoledInvertFilter
            "dark" -> DarkInvertFilter
            "sepia" -> SepiaFilter
            else -> null
        }
    }
}
