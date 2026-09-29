package com.anonymous.csreader.ui.pdf

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import com.anonymous.csreader.data.HighlightEntity
import com.anonymous.csreader.ui.theme.CsReaderTheme

@Composable
fun PdfFadeView(
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
    val pageColorFilter = PdfColorFilters.getFilter(themeName)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CsReaderTheme.colors.bg)
            .pointerInput(currentPage, pageCount) {
                detectTapGestures(
                    onTap = { offset ->
                        val screenW = size.width.toFloat()
                        if (offset.x < screenW * 0.25f) {
                            if (currentPage > 0) onPageChange(currentPage - 1)
                        } else if (offset.x > screenW * 0.75f) {
                            if (currentPage + 1 < pageCount) onPageChange(currentPage + 1)
                        } else {
                            onToggleControls()
                        }
                    },
                    onLongPress = { offset ->
                        val relX = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val relY = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                        onAddNoteAt(currentPage, relX, relY)
                    }
                )
            }
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight

        Crossfade(
            targetState = currentPage,
            animationSpec = tween(320),
            label = "PageFade"
        ) { targetPage ->
            var pageBitmap by remember(targetPage) { mutableStateOf<Bitmap?>(renderer.getCachedBitmap(targetPage)) }

            LaunchedEffect(targetPage, widthPx, heightPx) {
                if (pageBitmap == null && widthPx > 0 && heightPx > 0) {
                    pageBitmap = renderer.getPageBitmap(targetPage, widthPx, heightPx)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (pageBitmap != null) {
                    Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "Sayfa ${targetPage + 1}",
                        contentScale = ContentScale.Fit,
                        colorFilter = pageColorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                    PdfPageAnnotations(
                        pageIndex = targetPage,
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
