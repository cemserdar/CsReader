package com.anonymous.csreader.ui.pdf

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.anonymous.csreader.data.HighlightEntity
import com.anonymous.csreader.ui.theme.CsReaderTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PdfCurlView(
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
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val pageCount = renderer.pageCount

    // Drag offset in pixels
    val dragOffset = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Preload current, next, and previous page bitmaps
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var nextBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var prevBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Screen dimensions
    var viewWidth by remember { mutableIntStateOf(1080) }
    var viewHeight by remember { mutableIntStateOf(1920) }

    val pageColorFilter = PdfColorFilters.getFilter(themeName)
    val pageBackFaceColor = when (themeName.lowercase()) {
        "amoled" -> Color(0xFF121212)
        "dark" -> Color(0xFF1E1E1E)
        "sepia" -> Color(0xFFEDE3D0)
        else -> Color(0xFFF9F7F1)
    }

    LaunchedEffect(currentPage, viewWidth, viewHeight) {
        if (viewWidth > 0 && viewHeight > 0) {
            currentBitmap = renderer.getPageBitmap(currentPage, viewWidth, viewHeight)
            if (currentPage + 1 < pageCount) {
                nextBitmap = renderer.getPageBitmap(currentPage + 1, viewWidth, viewHeight)
            }
            if (currentPage - 1 >= 0) {
                prevBitmap = renderer.getPageBitmap(currentPage - 1, viewWidth, viewHeight)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CsReaderTheme.colors.bg)
            .pointerInput(currentPage, pageCount) {
                detectTapGestures(
                    onTap = { offset ->
                        val screenW = size.width.toFloat()
                        if (offset.x < screenW * 0.25f) {
                            // Turn previous
                            if (currentPage > 0) {
                                coroutineScope.launch {
                                    dragOffset.snapTo(0f)
                                    dragOffset.animateTo(
                                        targetValue = screenW,
                                        animationSpec = tween(380, easing = FastOutSlowInEasing)
                                    )
                                    onPageChange(currentPage - 1)
                                    dragOffset.snapTo(0f)
                                }
                            }
                        } else if (offset.x > screenW * 0.75f) {
                            // Turn next
                            if (currentPage + 1 < pageCount) {
                                coroutineScope.launch {
                                    dragOffset.snapTo(0f)
                                    dragOffset.animateTo(
                                        targetValue = -screenW,
                                        animationSpec = tween(380, easing = FastOutSlowInEasing)
                                    )
                                    onPageChange(currentPage + 1)
                                    dragOffset.snapTo(0f)
                                }
                            }
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
            .pointerInput(currentPage, pageCount) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        val screenW = size.width.toFloat()
                        val currentVal = dragOffset.value
                        val threshold = screenW * 0.22f

                        coroutineScope.launch {
                            if (currentVal < -threshold && currentPage + 1 < pageCount) {
                                dragOffset.animateTo(
                                    targetValue = -screenW,
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                )
                                onPageChange(currentPage + 1)
                                dragOffset.snapTo(0f)
                            } else if (currentVal > threshold && currentPage > 0) {
                                dragOffset.animateTo(
                                    targetValue = screenW,
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                )
                                onPageChange(currentPage - 1)
                                dragOffset.snapTo(0f)
                            } else {
                                dragOffset.animateTo(0f, animationSpec = tween(200))
                            }
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        coroutineScope.launch { dragOffset.animateTo(0f) }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val screenW = size.width.toFloat()
                        val newVal = (dragOffset.value + dragAmount.x).coerceIn(
                            if (currentPage + 1 < pageCount) -screenW else 0f,
                            if (currentPage > 0) screenW else 0f
                        )
                        coroutineScope.launch { dragOffset.snapTo(newVal) }
                    }
                )
            }
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        SideEffect {
            viewWidth = widthPx
            viewHeight = heightPx
        }

        val screenW = widthPx.toFloat()
        val curOffset = dragOffset.value
        val progress = if (screenW > 0) (curOffset / screenW).coerceIn(-1f, 1f) else 0f

        if (progress < 0) {
            // Turning forward: Underneath page is NEXT page
            if (currentPage + 1 < pageCount && nextBitmap != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = nextBitmap!!.asImageBitmap(),
                        contentDescription = "Sonraki Sayfa",
                        contentScale = ContentScale.Fit,
                        colorFilter = pageColorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                    PdfPageAnnotations(
                        pageIndex = currentPage + 1,
                        highlights = highlights,
                        onHighlightClick = onHighlightClick
                    )
                    // Shadow cast by turning page onto next page
                    val shadowAlpha = ((1f - abs(progress)) * 0.45f).coerceIn(0f, 0.45f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(80.dp)
                            .align(Alignment.CenterStart)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Black.copy(alpha = shadowAlpha), Color.Transparent)
                                )
                            )
                    )
                }
            }

            // Top curling page: CURRENT page rotating to left around (0, 0.5)
            val curlAngle = progress * 180f // 0 down to -180 deg
            if (currentBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            rotationY = curlAngle
                            cameraDistance = 14f * density
                        }
                ) {
                    if (curlAngle >= -90f) {
                        // Front face of page
                        Image(
                            bitmap = currentBitmap!!.asImageBitmap(),
                            contentDescription = "Mevcut Sayfa",
                            contentScale = ContentScale.Fit,
                            colorFilter = pageColorFilter,
                            modifier = Modifier.fillMaxSize()
                        )
                        PdfPageAnnotations(
                            pageIndex = currentPage,
                            highlights = highlights,
                            onHighlightClick = onHighlightClick
                        )
                        // Spine curl dynamic shadow
                        val spineShadowAlpha = (abs(progress) * 0.5f).coerceIn(0f, 0.5f)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = spineShadowAlpha))
                                    )
                                )
                        )
                    } else {
                        // Back face of page (paper back texture)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationY = 180f }
                                .background(pageBackFaceColor)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.25f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.1f)
                                            )
                                        )
                                    )
                                }
                        )
                    }
                }
            }
        } else if (progress > 0) {
            // Turning backward: CURRENT page stays below
            if (currentBitmap != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = currentBitmap!!.asImageBitmap(),
                        contentDescription = "Mevcut Sayfa",
                        contentScale = ContentScale.Fit,
                        colorFilter = pageColorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                    PdfPageAnnotations(
                        pageIndex = currentPage,
                        highlights = highlights,
                        onHighlightClick = onHighlightClick
                    )
                }
            }

            // PREVIOUS page curls over the current page
            val curlAngle = -180f + (progress * 180f) // -180 up to 0 deg
            if (currentPage > 0 && prevBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            rotationY = curlAngle
                            cameraDistance = 14f * density
                        }
                ) {
                    if (curlAngle >= -90f) {
                        Image(
                            bitmap = prevBitmap!!.asImageBitmap(),
                            contentDescription = "Önceki Sayfa",
                            contentScale = ContentScale.Fit,
                            colorFilter = pageColorFilter,
                            modifier = Modifier.fillMaxSize()
                        )
                        PdfPageAnnotations(
                            pageIndex = currentPage - 1,
                            highlights = highlights,
                            onHighlightClick = onHighlightClick
                        )
                    } else {
                        // Back of previous page
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationY = 180f }
                                .background(pageBackFaceColor)
                        )
                    }
                }
            }
        } else {
            // Idle state: Current page static with interactive annotations
            if (currentBitmap != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = currentBitmap!!.asImageBitmap(),
                        contentDescription = "Sayfa ${currentPage + 1}",
                        contentScale = ContentScale.Fit,
                        colorFilter = pageColorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                    PdfPageAnnotations(
                        pageIndex = currentPage,
                        highlights = highlights,
                        onHighlightClick = onHighlightClick
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CsReaderTheme.colors.primary)
                }
            }
        }
    }
}
