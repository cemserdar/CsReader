package com.anonymous.csreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.csreader.data.BookEntity
import com.anonymous.csreader.ui.theme.CsReaderTheme

/**
 * HeroContinueReadingCard:
 * Kullanıcının en son okuduğu eseri ön plana çıkaran,
 * 3D gerçekçi cilt kapağı ve modern tipografisiyle dikkat çeken vitrin kartı.
 */
@Composable
fun HeroContinueReadingCard(
    book: BookEntity,
    onContinueReading: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressPercent = (book.progress * 100).toInt().coerceIn(0, 100)
    val isPdf = book.type.equals("pdf", ignoreCase = true)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = CsReaderTheme.colors.border.copy(alpha = 0.7f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable { onContinueReading() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            CsReaderTheme.colors.primary.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        radius = 800f
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Sol: 3D Artisanal Book Cover
                ArtisanalBookCover(
                    book = book,
                    size = BookCoverSize.HERO
                )

                // 2. Sağ: Detaylar ve Okumaya Devam Et Butonu
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Rozet: "Şu An Okunan"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Text(
                            text = "ŞU AN OKUNAN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CsReaderTheme.colors.primary,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Başlık
                    Text(
                        text = book.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = CsReaderTheme.colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 22.sp
                    )

                    // Yazar
                    Text(
                        text = book.author,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = CsReaderTheme.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // İlerleme Çubuğu ve Yüzde
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPdf && (book.lastPage ?: 0) > 0) "Sayfa ${(book.lastPage ?: 0) + 1}" else "İlerleme",
                                fontSize = 11.sp,
                                color = CsReaderTheme.colors.textMuted
                            )
                            Text(
                                text = "%$progressPercent",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CsReaderTheme.colors.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        LinearProgressIndicator(
                            progress = { book.progress.coerceIn(0f, 1f) },
                            color = CsReaderTheme.colors.primary,
                            trackColor = CsReaderTheme.colors.border,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // "Devam Et" Butonu
                    Button(
                        onClick = onContinueReading,
                        colors = ButtonDefaults.buttonColors(containerColor = CsReaderTheme.colors.primary),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Kaldığın Yerden Oku",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

