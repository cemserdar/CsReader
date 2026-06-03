package com.anonymous.csreader.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.anonymous.csreader.data.BookEntity
import com.anonymous.csreader.ui.theme.CsReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NativePdfReader(
    book: BookEntity,
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val pageTransition by viewModel.pageTransitionState.collectAsState()
    // Start in full-screen mode — user taps center to show/hide controls
    var showControls by remember { mutableStateOf(false) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Full screen logic
    val view = LocalView.current
    LaunchedEffect(showControls) {
        val window = (view.context as? Activity)?.window
        window?.let {
            val controller = WindowCompat.getInsetsController(it, view)
            if (showControls) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val window = (view.context as? Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(book.uri) {
        try {
            val fileName = File(book.uri).name
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                fileDescriptor = fd
                val renderer = PdfRenderer(fd)
                pdfRenderer = renderer
                pageCount = renderer.pageCount
            } else {
                errorMsg = "Dosya bulunamadı: $fileName"
            }
        } catch (e: Exception) {
            errorMsg = "PDF yüklenirken hata oluştu: ${e.message}"
        }

        onDispose {
            pdfRenderer?.close()
            fileDescriptor?.close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CsReaderTheme.colors.bg)
    ) {
        if (errorMsg != null) {
            Text(
                text = errorMsg!!,
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (pageCount == 0) {
            CircularProgressIndicator(
                color = CsReaderTheme.colors.primary,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val pagerState = rememberPagerState(pageCount = { pageCount })

            // The main pager - swipe enabled for slide/fade, disabled for none
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = pageTransition != "none"
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            when (pageTransition) {
                                "fade" -> {
                                    alpha = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
                                }
                                "none" -> {
                                    // if none, scrolling is disabled anyway via userScrollEnabled, but just in case
                                    alpha = if (pageOffset == 0f) 1f else 0f
                                }
                                else -> {
                                    // "slide" (default HorizontalPager behavior)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    PdfPageComposable(
                        pdfRenderer = pdfRenderer,
                        pageIndex = page
                    )
                }
            }

            // Left tap zone — go to previous page
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .align(Alignment.CenterStart)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        coroutineScope.launch {
                            if (pagerState.currentPage > 0) {
                                if (pageTransition == "none")
                                    pagerState.scrollToPage(pagerState.currentPage - 1)
                                else
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    }
            )

            // Right tap zone — go to next page
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .align(Alignment.CenterEnd)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        coroutineScope.launch {
                            if (pagerState.currentPage < pageCount - 1) {
                                if (pageTransition == "none")
                                    pagerState.scrollToPage(pagerState.currentPage + 1)
                                else
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
            )

            // Center tap zone — toggle full screen
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(Alignment.Center)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showControls = !showControls
                    }
            )
        }

        // Top Bar
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CsReaderTheme.colors.cardBg)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Geri", tint = CsReaderTheme.colors.text, modifier = Modifier.size(26.dp))
                }
                Text(
                    text = book.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CsReaderTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                // Fake space for balance
                Spacer(modifier = Modifier.width(48.dp))
            }
        }
    }
}

@Composable
fun PdfPageComposable(pdfRenderer: PdfRenderer?, pageIndex: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            pdfRenderer?.let { renderer ->
                try {
                    val page = renderer.openPage(pageIndex)
                    val displayDensity = 2.5f
                    val w = (page.width * displayDensity).toInt()
                    val h = (page.height * displayDensity).toInt()

                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    
                    withContext(Dispatchers.Main) {
                        bitmap = bmp
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Sayfa ${pageIndex + 1}",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )
    } else {
        CircularProgressIndicator()
    }
}
