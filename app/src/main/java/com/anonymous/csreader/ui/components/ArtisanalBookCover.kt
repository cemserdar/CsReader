package com.anonymous.csreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.csreader.data.BookEntity
import kotlin.math.abs

enum class BookCoverSize {
    HERO,   // Large showcase (125dp x 180dp)
    GRID,   // Shelf 2-column grid (full width x 210dp)
    COMPACT // List row item (68dp x 98dp)
}

data class CoverPalette(
    val gradientStart: Color,
    val gradientEnd: Color,
    val accentColor: Color,
    val textColor: Color,
    val spineDarkColor: Color
)

object CoverPaletteGenerator {
    private val palettes = listOf(
        // 1. Velvet Obsidian
        CoverPalette(
            gradientStart = Color(0xFF0F172A),
            gradientEnd = Color(0xFF1E293B),
            accentColor = Color(0xFFF59E0B),
            textColor = Color(0xFFF8FAFC),
            spineDarkColor = Color(0xFF020617)
        ),
        // 2. Imperial Crimson
        CoverPalette(
            gradientStart = Color(0xFF450A0A),
            gradientEnd = Color(0xFF7F1D1D),
            accentColor = Color(0xFFFDE047),
            textColor = Color(0xFFFFF1F2),
            spineDarkColor = Color(0xFF1C0303)
        ),
        // 3. Nordic Pine & Sage
        CoverPalette(
            gradientStart = Color(0xFF064E3B),
            gradientEnd = Color(0xFF047857),
            accentColor = Color(0xFF6EE7B7),
            textColor = Color(0xFFECFDF5),
            spineDarkColor = Color(0xFF022C22)
        ),
        // 4. Vintage Terracotta Amber
        CoverPalette(
            gradientStart = Color(0xFF7C2D12),
            gradientEnd = Color(0xFF9A3412),
            accentColor = Color(0xFFFDBA74),
            textColor = Color(0xFFFFF7ED),
            spineDarkColor = Color(0xFF431407)
        ),
        // 5. Royal Electric Iris
        CoverPalette(
            gradientStart = Color(0xFF312E81),
            gradientEnd = Color(0xFF4338CA),
            accentColor = Color(0xFFA5B4FC),
            textColor = Color(0xFFEEF2FF),
            spineDarkColor = Color(0xFF1E1B4B)
        ),
        // 6. Deep Espresso Leather
        CoverPalette(
            gradientStart = Color(0xFF271B12),
            gradientEnd = Color(0xFF442D1D),
            accentColor = Color(0xFFFCD34D),
            textColor = Color(0xFFFAF5EE),
            spineDarkColor = Color(0xFF120B06)
        ),
        // 7. Midnight Teal
        CoverPalette(
            gradientStart = Color(0xFF134E4A),
            gradientEnd = Color(0xFF0F766E),
            accentColor = Color(0xFF5EEAD4),
            textColor = Color(0xFFF0FDFA),
            spineDarkColor = Color(0xFF042F2E)
        )
    )

    fun getPaletteForBook(title: String): CoverPalette {
        val hash = abs(title.hashCode())
        return palettes[hash % palettes.size]
    }
}

/**
 * ArtisanalBookCover:
 * Gerçek bir ciltli kitabın 3D omurga kıvrımını, üst düzey editoryal tipografisini
 * ve kâğıt kenar derinliğini taklit eden özgün kapak bileşeni.
 */
@Composable
fun ArtisanalBookCover(
    book: BookEntity,
    size: BookCoverSize = BookCoverSize.GRID,
    modifier: Modifier = Modifier
) {
    val palette = remember(book.title) { CoverPaletteGenerator.getPaletteForBook(book.title) }
    val isEpub = book.type.equals("epub", ignoreCase = true)

    // Boyut oranları
    val (coverWidth: Dp, coverHeight: Dp) = when (size) {
        BookCoverSize.HERO -> 120.dp to 175.dp
        BookCoverSize.GRID -> 140.dp to 200.dp
        BookCoverSize.COMPACT -> 66.dp to 96.dp
    }

    // Gerçek cilt şekli: Sol kenar (omurga) düz-dik, sağ kenar (sayfa açılışı) yuvarlatılmış
    val bookCoverShape = RoundedCornerShape(
        topStart = 3.dp,
        bottomStart = 3.dp,
        topEnd = 10.dp,
        bottomEnd = 10.dp
    )

    Box(
        modifier = modifier
            .size(width = coverWidth, height = coverHeight)
            .shadow(
                elevation = if (size == BookCoverSize.HERO) 10.dp else 6.dp,
                shape = bookCoverShape,
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.Black.copy(alpha = 0.6f)
            )
            .clip(bookCoverShape)
            .background(
                Brush.verticalGradient(
                    listOf(palette.gradientStart, palette.gradientEnd)
                )
            )
            .border(0.75.dp, Color.White.copy(alpha = 0.15f), bookCoverShape)
    ) {
        // 1. 3D Gerçekçi Omurga ve Sayfa Kıvrım Işığı (Book Spine Crease Gradient)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (size == BookCoverSize.COMPACT) 10.dp else 18.dp)
                .align(Alignment.CenterStart)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            palette.spineDarkColor.copy(alpha = 0.85f),
                            palette.spineDarkColor.copy(alpha = 0.45f),
                            Color.White.copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 2. Sağ Sayfa Kenarı Ayracı Çizgisi (Deckle edge shadow)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.5.dp)
                .align(Alignment.CenterEnd)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                    )
                )
        )

        // 3. Kapak İçi Editoryal Düzen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (size == BookCoverSize.COMPACT) 10.dp else 16.dp,
                    end = if (size == BookCoverSize.COMPACT) 6.dp else 12.dp,
                    top = if (size == BookCoverSize.COMPACT) 8.dp else 12.dp,
                    bottom = if (size == BookCoverSize.COMPACT) 8.dp else 12.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Üst Bölüm: Format Rozeti (EPUB / PDF)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(0.5.dp, palette.accentColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isEpub) "EPUB" else "PDF",
                        fontSize = if (size == BookCoverSize.COMPACT) 7.sp else 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentColor,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Orta Bölüm: Sanatsal Geometrik Motif veya Baş Harf Damgası
            if (size != BookCoverSize.COMPACT) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Minimalist Geometrik Damga
                    val initial = book.title.trim().take(1).uppercase()
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, palette.accentColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.accentColor,
                            fontFamily = FontFamily.Serif
                        )
                    }
                }
            }

            // Alt Bölüm: Başlık ve Yazar
            Column {
                Text(
                    text = book.title,
                    fontSize = when (size) {
                        BookCoverSize.HERO -> 13.sp
                        BookCoverSize.GRID -> 12.sp
                        BookCoverSize.COMPACT -> 9.sp
                    },
                    fontWeight = FontWeight.Bold,
                    color = palette.textColor,
                    maxLines = if (size == BookCoverSize.COMPACT) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = if (size == BookCoverSize.COMPACT) 11.sp else 15.sp,
                    fontFamily = FontFamily.Serif
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = book.author,
                    fontSize = if (size == BookCoverSize.COMPACT) 7.sp else 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textColor.copy(alpha = 0.70f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.3.sp
                )
            }
        }

        // 4. Favori İse: Üstten Sarkan Şık İpek Ayraç Kurdelesi
        if (book.favorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp)
                    .width(10.dp)
                    .height(18.dp)
                    .background(Color(0xFFE11D48), RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                    .shadow(2.dp)
            )
        }
    }
}

