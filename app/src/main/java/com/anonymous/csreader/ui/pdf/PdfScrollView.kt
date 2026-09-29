package com.anonymous.csreader.ui.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.csreader.data.HighlightEntity
import com.anonymous.csreader.ui.theme.CsReaderTheme

@Composable
fun PdfScrollView(
    renderer: PdfPageRenderer,
    currentPage: Int,
    highlights: List<HighlightEntity>,
    themeName: String = "light",
    onPageChange: (Int) -> Unit,
    onToggleControls: () -> Unit,
    onAddNoteAt: (Int, Float, Float) -> Unit,
    onHighlightClick: (HighlightEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val pageCount = renderer.pageCount
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage)
    val pageColorFilter = PdfColorFilters.getFilter(themeName)

    val pageBgColor = when (themeName.lowercase()) {
        "amoled" -> Color.Black
        "dark" -> Color(0xFF1E1E1E)
        "sepia" -> Color(0xFFF4ECD8)
        else -> Color.White
    }

    // Sync scroll position with currentPage
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex != currentPage) {
            onPageChange(listState.firstVisibleItemIndex)
        }
    }

    // Scroll to page if external change (e.g. from slider)
    LaunchedEffect(currentPage) {
        if (listState.firstVisibleItemIndex != currentPage && !listState.isScrollInProgress) {
            listState.scrollToItem(currentPage)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(CsReaderTheme.colors.bg)) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(pageCount, key = { it }) { pageIndex ->
                var pageBitmap by remember { mutableStateOf<Bitmap?>(renderer.getCachedBitmap(pageIndex)) }

                LaunchedEffect(pageIndex, widthPx, heightPx) {
                    if (pageBitmap == null && widthPx > 0) {
                        pageBitmap = renderer.getPageBitmap(pageIndex, widthPx, (widthPx * 1.414f).toInt())
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .shadow(4.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(pageBgColor)
                        .pointerInput(pageIndex) {
                            detectTapGestures(
                                onTap = { onToggleControls() },
                                onLongPress = { offset ->
                                    val relX = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val relY = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    onAddNoteAt(pageIndex, relX, relY)
                                }
                            )
                        }
                ) {
                    if (pageBitmap != null) {
                        Column {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(pageBitmap!!.width.toFloat() / pageBitmap!!.height.toFloat())) {
                                Image(
                                    bitmap = pageBitmap!!.asImageBitmap(),
                                    contentDescription = "Sayfa ${pageIndex + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    colorFilter = pageColorFilter,
                                    modifier = Modifier.fillMaxSize()
                                )
                                PdfPageAnnotations(
                                    pageIndex = pageIndex,
                                    highlights = highlights,
                                    onHighlightClick = onHighlightClick
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(500.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = CsReaderTheme.colors.primary)
                        }
                    }

                    // Floating page badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${pageIndex + 1} / $pageCount",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
