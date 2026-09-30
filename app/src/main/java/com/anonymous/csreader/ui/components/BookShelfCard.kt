package com.anonymous.csreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.csreader.data.BookEntity
import com.anonymous.csreader.ui.theme.CsReaderTheme

/**
 * BookShelfCard:
 * 3D Kitaplık Raf görünümü için kart.
 * Gerçekçi cilt kapağı, kapak üstü ilerleme rozeti ve şık tipografi.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookShelfCard(
    book: BookEntity,
    onSelectBook: (BookEntity) -> Unit,
    onToggleFavorite: () -> Unit,
    onShowOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressPercent = (book.progress * 100).toInt().coerceIn(0, 100)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { onSelectBook(book) },
                onLongClick = onShowOptions
            )
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 3D Gerçekçi Kitap Kapağı ve İlerleme Rozeti
        Box(contentAlignment = Alignment.BottomEnd) {
            ArtisanalBookCover(
                book = book,
                size = BookCoverSize.GRID,
                modifier = Modifier.fillMaxWidth()
            )

            // İlerleme Rozeti
            if (progressPercent > 0) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "%$progressPercent",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Başlık ve Yazar
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = book.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CsReaderTheme.colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Serif
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = book.author,
                    fontSize = 11.sp,
                    color = CsReaderTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (book.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (book.favorite) Color(0xFFEF4444) else CsReaderTheme.colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * BookListRowCard:
 * Minimalist Editoryal Liste görünümü için satır kartı.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListRowCard(
    book: BookEntity,
    onSelectBook: (BookEntity) -> Unit,
    onToggleFavorite: () -> Unit,
    onShowOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressPercent = (book.progress * 100).toInt().coerceIn(0, 100)
    val isPdf = book.type.equals("pdf", ignoreCase = true)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CsReaderTheme.colors.border.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = { onSelectBook(book) },
                onLongClick = onShowOptions
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Minyatür 3D Kapak
            ArtisanalBookCover(
                book = book,
                size = BookCoverSize.COMPACT
            )

            // 2. Detaylar
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Format & Sayfa Bilgisi
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isPdf) Color(0x1AEF4444) else CsReaderTheme.colors.primary.copy(alpha = 0.12f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isPdf) "PDF" else "EPUB",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPdf) Color(0xFFEF4444) else CsReaderTheme.colors.primary
                        )
                    }

                    if (progressPercent > 0) {
                        Text(
                            text = "• %$progressPercent okundu",
                            fontSize = 11.sp,
                            color = CsReaderTheme.colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Başlık
                Text(
                    text = book.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = CsReaderTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Serif
                )

                // Yazar
                Text(
                    text = book.author,
                    fontSize = 12.sp,
                    color = CsReaderTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // İlerleme İnce Çizgisi
                LinearProgressIndicator(
                    progress = { book.progress.coerceIn(0f, 1f) },
                    color = if (isPdf) Color(0xFFEF4444) else CsReaderTheme.colors.primary,
                    trackColor = CsReaderTheme.colors.border,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }

            // 3. Aksiyonlar (Favori & Seçenekler)
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (book.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (book.favorite) Color(0xFFEF4444) else CsReaderTheme.colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onShowOptions, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Seçenekler",
                        tint = CsReaderTheme.colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

