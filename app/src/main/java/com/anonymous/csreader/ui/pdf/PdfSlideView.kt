package com.anonymous.csreader.ui.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.anonymous.csreader.data.HighlightEntity
import com.anonymous.csreader.ui.theme.CsReaderTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfSlideView(
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
    val pagerState = rememberPagerState(initialPage = currentPage) { pageCount }
    val coroutineScope = rememberCoroutineScope()
    val pageColorFilter = PdfColorFilters.getFilter(themeName)

    // Sync external currentPage changes (e.g. from slider) with pagerState
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage && currentPage in 0 until pageCount) {
            pagerState.scrollToPage(currentPage)
        }
    }

    // Sync pagerState changes to callback
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentPage) {
            onPageChange(pagerState.currentPage)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(CsReaderTheme.colors.bg)) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 12.dp
        ) { pageIndex ->
            var pageBitmap by remember { mutableStateOf<Bitmap?>(renderer.getCachedBitmap(pageIndex)) }

            LaunchedEffect(pageIndex, widthPx, heightPx) {
                if (pageBitmap == null && widthPx > 0 && heightPx > 0) {
                    pageBitmap = renderer.getPageBitmap(pageIndex, widthPx, heightPx)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Calculate page offset for depth effect
                        val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction).absoluteValue
                        val scale = 1f - (pageOffset * 0.08f).coerceIn(0f, 0.08f)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (pageOffset * 0.35f).coerceIn(0f, 0.35f)
                    }
                    .pointerInput(pageIndex) {
                        detectTapGestures(
                            onTap = { offset ->
                                val screenW = size.width.toFloat()
                                if (offset.x < screenW * 0.25f) {
                                    if (pageIndex > 0) {
                                        coroutineScope.launch { pagerState.animateScrollToPage(pageIndex - 1) }
                                    }
                                } else if (offset.x > screenW * 0.75f) {
                                    if (pageIndex + 1 < pageCount) {
                                        coroutineScope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
                                    }
                                } else {
                                    onToggleControls()
                                }
                            },
                            onLongPress = { offset ->
                                val relX = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val relY = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                onAddNoteAt(pageIndex, relX, relY)
                            }
                        )
                    }
            ) {
                if (pageBitmap != null) {
                    Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "Sayfa ${pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        colorFilter = pageColorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                    PdfPageAnnotations(
                        pageIndex = pageIndex,
                        highlights = highlights,
                        onHighlightClick = onHighlightClick
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CsReaderTheme.colors.primary)
                    }
                }
            }
        }
    }
}
