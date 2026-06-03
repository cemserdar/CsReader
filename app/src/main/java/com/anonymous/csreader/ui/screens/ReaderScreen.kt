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
import org.readium.r2.navigator.Decoration
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.shared.util.toUrl

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
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderViewModel(bookDao, highlightDao, settingsManager, book) as T
    })

    var publication by remember { mutableStateOf<Publication?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }

    // Parse book with Readium PublicationOpener (v3.1.1)
    LaunchedEffect(book.uri) {
        withContext(Dispatchers.IO) {
            try {
                val httpClient = DefaultHttpClient()
                val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
                val pdfFactory = PdfiumDocumentFactory(context)
                val publicationParser = DefaultPublicationParser(
                    context, 
                    httpClient, 
                    assetRetriever, 
                    pdfFactory = pdfFactory
                )
                val opener = PublicationOpener(
                    publicationParser = publicationParser
                )
                
                val file = File(book.uri)
                val url = file.toUrl() // Using Readium extension
                
                if (url != null) {
                    val assetResult = assetRetriever.retrieve(url)
                    val asset = assetResult.getOrNull()
                    
                    if (asset != null) {
                        val pubResult = opener.open(asset, allowUserInteraction = false)
                        publication = pubResult.getOrNull()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        loading = false
    }

    val highlights by viewModel.highlightsState.collectAsState()
    val scope = rememberCoroutineScope()
    var viewId by remember { mutableStateOf(android.view.View.generateViewId()) }

    Box(modifier = Modifier.fillMaxSize().background(CsReaderTheme.colors.bg)) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (publication != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize().clickable { showControls = !showControls },
                factory = { ctx ->
                    FragmentContainerView(ctx).apply {
                        id = viewId
                        val fragmentManager = activity.supportFragmentManager
                        val isEpub = book.type == "epub"
                        
                        val factory = if (isEpub) {
                            EpubNavigatorFactory(publication!!, EpubNavigatorFactory.Configuration()).createFragmentFactory(
                                initialLocator = null
                            )
                        } else {
                            val pdfFactory = PdfNavigatorFactory(publication!!, PdfiumEngineProvider())
                            pdfFactory.createFragmentFactory(initialLocator = null)
                        }
                        
                        fragmentManager.fragmentFactory = factory
                        val fragmentClass = if (isEpub) EpubNavigatorFragment::class.java else PdfNavigatorFragment::class.java
                        
                        fragmentManager.commit {
                            replace(id, fragmentClass, null)
                        }
                        
                        post {
                            val fragment = fragmentManager.findFragmentById(id)
                            if (fragment is DecorableNavigator) {
                                val decorations = highlights.mapNotNull { hl ->
                                    try {
                                        val locatorJson = org.json.JSONObject(hl.cfiRange!!)
                                        val locator = Locator.fromJSON(locatorJson)
                                        if (locator != null) {
                                            Decoration(
                                                id = hl.id,
                                                locator = locator,
                                                style = Decoration.Style.Highlight(tint = android.graphics.Color.YELLOW)
                                            )
                                        } else null
                                    } catch (e: Exception) { null }
                                }
                                scope.launch {
                                    fragment.applyDecorations(decorations, "highlights")
                                }
                            }
                        }
                    }
                },
                update = { view ->
                    val fragmentManager = activity.supportFragmentManager
                    val fragment = fragmentManager.findFragmentById(view.id)
                    if (fragment is DecorableNavigator) {
                        val decorations = highlights.mapNotNull { hl ->
                            try {
                                val locatorJson = org.json.JSONObject(hl.cfiRange!!)
                                val locator = Locator.fromJSON(locatorJson)
                                if (locator != null) {
                                    Decoration(
                                        id = hl.id,
                                        locator = locator,
                                        style = Decoration.Style.Highlight(tint = android.graphics.Color.YELLOW)
                                    )
                                } else null
                            } catch (e: Exception) { null }
                        }
                        scope.launch {
                            fragment.applyDecorations(decorations, "highlights")
                        }
                    }
                }
            )
        } else {
            Text("Kitap yüklenemedi.", modifier = Modifier.align(Alignment.Center), color = Color.Red)
        }

        // Header Control Bar
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
        
        // Floating action button for highlighting text
        if (showControls) {
            FloatingActionButton(
                onClick = {
                    val fragment = activity.supportFragmentManager.findFragmentById(viewId)
                    if (fragment is SelectableNavigator) {
                        scope.launch {
                            val selection = fragment.currentSelection()
                            if (selection != null) {
                                val locator = selection.locator
                                val text = locator.text.highlight ?: ""
                                val cfi = locator.toJSON().toString()
                                viewModel.addHighlight(text, cfi, locator.locations.position, "#FFFF00") {
                                    fragment.clearSelection()
                                    Toast.makeText(context, "Vurgulandı!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Önce metin seçin.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = CsReaderTheme.colors.primary,
                contentColor = CsReaderTheme.colors.bg
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Vurgula")
            }
        }
    }
}
