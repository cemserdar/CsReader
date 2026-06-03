package com.anonymous.csreader.ui.screens

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anonymous.csreader.data.*
import com.anonymous.csreader.ui.theme.CsReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.asset.FileAsset
import org.readium.r2.streamer.Streamer
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.pdf.PdfNavigatorFragment

// ViewModel
class ReaderViewModel(
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao,
    private val settingsManager: SettingsManager,
    val book: BookEntity
) : ViewModel() {
    val themeState = MutableStateFlow(settingsManager.theme)
    val fontSizeState = MutableStateFlow(settingsManager.fontSize)
    val pageTransitionState = MutableStateFlow(settingsManager.pageTransition)

    val highlightsState: StateFlow<List<HighlightEntity>> = highlightDao.getHighlightsForBook(book.id)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateProgress(progress: Float, lastCfi: String?, lastPage: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = book.copy(
                progress = progress,
                lastCfi = lastCfi ?: book.lastCfi,
                lastPage = lastPage ?: book.lastPage,
                lastRead = System.currentTimeMillis()
            )
            bookDao.updateBook(updated)
        }
    }

    fun addHighlight(text: String, cfiRange: String?, page: Int?, color: String, onComplete: (HighlightEntity) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val newHl = HighlightEntity(
                id = "hl_${System.currentTimeMillis()}_${(1000..9999).random()}",
                bookId = book.id,
                cfiRange = cfiRange,
                page = page,
                text = text,
                note = null,
                color = color,
                date = System.currentTimeMillis()
            )
            highlightDao.insertHighlight(newHl)
            withContext(Dispatchers.Main) { onComplete(newHl) }
        }
    }
}

@Composable
fun ReaderScreen(
    bookDao: BookDao,
    highlightDao: HighlightDao,
    settingsManager: SettingsManager,
    book: BookEntity,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return
    val viewModel: ReaderViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderViewModel(bookDao, highlightDao, settingsManager, book) as T
    })

    var publication by remember { mutableStateOf<Publication?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }

    // Parse book with Readium Streamer
    LaunchedEffect(book.uri) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(book.uri)
                val asset = FileAsset(file)
                val streamer = Streamer(context)
                val pub = streamer.open(asset, allowUserInteraction = false)
                publication = pub.getOrNull()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        loading = false
    }

    Box(modifier = Modifier.fillMaxSize().background(CsReaderTheme.colors.bg)) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (publication != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize().clickable { showControls = !showControls },
                factory = { ctx ->
                    FragmentContainerView(ctx).apply {
                        id = android.view.View.generateViewId()
                        val fragmentManager = activity.supportFragmentManager
                        val isEpub = book.type == "epub"
                        
                        if (isEpub) {
                            val factory = EpubNavigatorFragment.createFactory(
                                publication = publication!!,
                                initialLocator = null,
                                config = EpubNavigatorFragment.Configuration()
                            )
                            fragmentManager.fragmentFactory = factory
                            fragmentManager.commit {
                                replace(id, EpubNavigatorFragment::class.java, null)
                            }
                        } else {
                            val factory = PdfNavigatorFragment.createFactory(
                                publication = publication!!,
                                initialLocator = null
                            )
                            fragmentManager.fragmentFactory = factory
                            fragmentManager.commit {
                                replace(id, PdfNavigatorFragment::class.java, null)
                            }
                        }
                    }
                }
            )
        } else {
            Text("Kitap yüklenemedi.", modifier = Modifier.align(Alignment.Center), color = Color.Red)
        }

        // Simplified Header Control Bar
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
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Geri", tint = CsReaderTheme.colors.text)
                }
                Text(
                    text = book.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CsReaderTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
