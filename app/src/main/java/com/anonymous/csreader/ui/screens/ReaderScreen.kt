package com.anonymous.csreader.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anonymous.csreader.data.*
import com.anonymous.csreader.ui.pdf.PdfNoteDetailDialog
import com.anonymous.csreader.ui.pdf.PdfReaderContainer
import com.anonymous.csreader.ui.theme.CsReaderTheme
import com.anonymous.csreader.utils.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.KeyEvent
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

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

    fun setPageTransition(transition: String) {
        settingsManager.pageTransition = transition
        pageTransitionState.value = transition
    }

    fun setFontSize(size: Int) {
        settingsManager.fontSize = size
        fontSizeState.value = size
    }

    fun setTheme(theme: String) {
        settingsManager.theme = theme
        themeState.value = theme
    }

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

    fun addHighlight(text: String, cfiRange: String?, page: Int?, color: String, note: String?, onComplete: (HighlightEntity) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val newHl = HighlightEntity(
                id = "hl_${System.currentTimeMillis()}_${(1000..9999).random()}",
                bookId = book.id,
                cfiRange = cfiRange,
                page = page,
                text = text,
                note = note?.takeIf { it.isNotBlank() },
                color = color,
                date = System.currentTimeMillis()
            )
            highlightDao.insertHighlight(newHl)
            withContext(Dispatchers.Main) { onComplete(newHl) }
        }
    }

    fun updateHighlight(highlight: HighlightEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            highlightDao.updateHighlight(highlight)
        }
    }

    fun deleteHighlight(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            highlightDao.deleteHighlightById(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    val pageTransition by viewModel.pageTransitionState.collectAsState()
    val fontSize by viewModel.fontSizeState.collectAsState()
    val themeName by viewModel.themeState.collectAsState()

    // 1. PDF Documents: Handled by custom high-performance Compose PDF Engine
    if (book.type == "pdf") {
        PdfReaderContainer(
            book = book,
            viewModel = viewModel,
            pageTransition = pageTransition,
            onBack = onBack,
            onPageTransitionChange = { viewModel.setPageTransition(it) }
        )
        return
    }

    // 2. EPUB Documents: Handled by Readium Kotlin Toolkit with enhanced controls
    val ttsManager = remember { TtsManager(context) }
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    var publication by remember { mutableStateOf<Publication?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }

    var highlightSelection by remember { mutableStateOf<org.readium.r2.navigator.Selection?>(null) }
    var showHighlightDialog by remember { mutableStateOf(false) }
    var activeHighlightForEdit by remember { mutableStateOf<HighlightEntity?>(null) }
    var showTransitionMenu by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var currentEpubProgress by remember { mutableFloatStateOf(book.progress) }

    // Parse EPUB publication
    LaunchedEffect(book.uri) {
        withContext(Dispatchers.IO) {
            try {
                val httpClient = DefaultHttpClient()
                val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
                val pdfFactory = org.readium.adapter.pdfium.document.PdfiumDocumentFactory(context)
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
                val url = file.toUrl()

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
    val viewId = remember(pageTransition, fontSize, themeName) { android.view.View.generateViewId() }

    LaunchedEffect(highlights, pageTransition, fontSize, themeName, viewId) {
        kotlinx.coroutines.delay(300)
        val fragmentManager = activity.supportFragmentManager
        val fragment = fragmentManager.findFragmentById(viewId)
        if (fragment is DecorableNavigator) {
            val decorations = highlights.mapNotNull { hl ->
                try {
                    val locatorJson = org.json.JSONObject(hl.cfiRange!!)
                    val locator = Locator.fromJSON(locatorJson)
                    if (locator != null) {
                        val parsedColor = try { android.graphics.Color.parseColor(hl.color) } catch (e: Exception) { android.graphics.Color.YELLOW }
                        Decoration(
                            id = hl.id,
                            locator = locator,
                            style = Decoration.Style.Highlight(tint = parsedColor)
                        )
                    } else null
                } catch (e: Exception) { null }
            }
            fragment.applyDecorations(decorations, "highlights")
        }
    }

    CsReaderTheme(themeName = themeName) {
        Box(modifier = Modifier.fillMaxSize().background(CsReaderTheme.colors.bg)) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (publication != null) {
                key(pageTransition, fontSize, themeName) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize().clickable { showControls = !showControls },
                        factory = { ctx ->
                            FragmentContainerView(ctx).apply {
                                id = viewId
                                val fragmentManager = activity.supportFragmentManager

                                val initialLocator = try {
                                    book.lastCfi?.let { Locator.fromJSON(org.json.JSONObject(it)) }
                                } catch (e: Exception) { null }

                                val readiumTheme = when (themeName.lowercase()) {
                                    "amoled", "dark" -> org.readium.r2.navigator.preferences.Theme.DARK
                                    "sepia" -> org.readium.r2.navigator.preferences.Theme.SEPIA
                                    else -> org.readium.r2.navigator.preferences.Theme.LIGHT
                                }

                                val prefs = if (themeName.lowercase() == "amoled") {
                                    EpubPreferences(
                                        scroll = pageTransition == "scroll",
                                        fontSize = fontSize.toDouble() / 16.0,
                                        theme = org.readium.r2.navigator.preferences.Theme.DARK,
                                        backgroundColor = org.readium.r2.navigator.preferences.Color(0xFF000000.toInt()),
                                        textColor = org.readium.r2.navigator.preferences.Color(0xFFFFFFFF.toInt())
                                    )
                                } else {
                                    EpubPreferences(
                                        scroll = pageTransition == "scroll",
                                        fontSize = fontSize.toDouble() / 16.0,
                                        theme = readiumTheme
                                    )
                                }
                            val factory = EpubNavigatorFactory(publication!!, EpubNavigatorFactory.Configuration()).createFragmentFactory(
                                initialLocator = initialLocator,
                                initialPreferences = prefs
                            )

                            fragmentManager.fragmentFactory = factory
                            fragmentManager.commit {
                                replace(id, EpubNavigatorFragment::class.java, null)
                            }

                            post {
                                val fragment = fragmentManager.findFragmentById(id)

                                if (fragment is VisualNavigator) {
                                    fragment.addInputListener(object : InputListener {
                                        override fun onTap(event: TapEvent): Boolean {
                                            showControls = !showControls
                                            return true
                                        }
                                        override fun onDrag(event: DragEvent): Boolean = false
                                        override fun onKey(event: KeyEvent): Boolean = false
                                    })

                                    scope.launch {
                                        fragment.currentLocator.collect { loc ->
                                            val p = (loc.locations.progression ?: 0.0).toFloat()
                                            currentEpubProgress = p
                                            val cfi = loc.toJSON().toString()
                                            viewModel.updateProgress(p, cfi, loc.locations.position)
                                        }
                                    }
                                }

                                if (fragment is DecorableNavigator) {
                                    fragment.addDecorationListener("highlights", object : DecorableNavigator.Listener {
                                        override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                                            val hl = highlights.find { it.id == event.decoration.id }
                                            if (hl != null) {
                                                activeHighlightForEdit = hl
                                            }
                                            return true
                                        }
                                    })
                                }
                                com.anonymous.csreader.ui.epub.EpubPageTransformers.applyTransition(fragment?.view, pageTransition)
                            }
                        }
                    },
                    update = { view ->
                        val fragment = activity.supportFragmentManager.findFragmentById(view.id)
                        com.anonymous.csreader.ui.epub.EpubPageTransformers.applyTransition(fragment?.view, pageTransition)
                    }
                )
            }
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
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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

                // TTS Sesli Okuma Butonu
                IconButton(onClick = {
                    if (ttsManager.isSpeaking) {
                        ttsManager.stop()
                    } else {
                        val fragment = activity.supportFragmentManager.findFragmentById(viewId) as? SelectableNavigator
                        scope.launch {
                            val selection = fragment?.currentSelection()
                            val textToSpeak = selection?.locator?.text?.highlight
                            if (!textToSpeak.isNullOrBlank()) {
                                ttsManager.speak(textToSpeak)
                            } else {
                                Toast.makeText(context, "Seslendirmek için metin seçebilirsiniz veya vurgu ekleyebilirsiniz.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) {
                    Icon(
                        imageVector = if (ttsManager.isSpeaking) Icons.Default.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Seslendir",
                        tint = if (ttsManager.isSpeaking) Color(0xFFEF4444) else CsReaderTheme.colors.text
                    )
                }

                // Font & Tipografi Butonu
                IconButton(onClick = { showFontDialog = true }) {
                    Icon(Icons.Default.FormatSize, contentDescription = "Yazı Boyutu", tint = CsReaderTheme.colors.text)
                }

                // Sayfa Geçiş Efekti Menüsü
                Box {
                    IconButton(onClick = { showTransitionMenu = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "Geçiş Modu",
                            tint = CsReaderTheme.colors.primary
                        )
                    }

                    DropdownMenu(
                        expanded = showTransitionMenu,
                        onDismissRequest = { showTransitionMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("3D Kitap Kıvrılma (Curl)") },
                            leadingIcon = {
                                if (pageTransition == "curl") Icon(Icons.Default.Check, null)
                            },
                            onClick = {
                                viewModel.setPageTransition("curl")
                                showTransitionMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Yatay Sayfalama (Slide)") },
                            leadingIcon = {
                                if (pageTransition == "slide") Icon(Icons.Default.Check, null)
                            },
                            onClick = {
                                viewModel.setPageTransition("slide")
                                showTransitionMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Dikey Kesintisiz (Scroll)") },
                            leadingIcon = {
                                if (pageTransition == "scroll") Icon(Icons.Default.Check, null)
                            },
                            onClick = {
                                viewModel.setPageTransition("scroll")
                                showTransitionMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Yumuşak Geçiş (Fade)") },
                            leadingIcon = {
                                if (pageTransition == "fade") Icon(Icons.Default.Check, null)
                            },
                            onClick = {
                                viewModel.setPageTransition("fade")
                                showTransitionMenu = false
                            }
                        )
                    }
                }

                // Metin Vurgula Butonu
                IconButton(onClick = {
                    val fragment = activity.supportFragmentManager.findFragmentById(viewId)
                    if (fragment is SelectableNavigator) {
                        scope.launch {
                            val selection = fragment.currentSelection()
                            if (selection != null) {
                                highlightSelection = selection
                            } else {
                                Toast.makeText(context, "Önce parmağınızla bir metin seçin.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) {
                    Icon(Icons.Default.Edit, contentDescription = "Vurgula", tint = CsReaderTheme.colors.primary)
                }
            }
        }

        // Bottom Navigation Bar (EPUB İlerleme ve Sayfa Geçişi)
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CsReaderTheme.colors.cardBg)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Okuma İlerlemesi",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CsReaderTheme.colors.text
                    )
                    Text(
                        text = "%${(currentEpubProgress * 100).toInt()}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CsReaderTheme.colors.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val fragment = activity.supportFragmentManager.findFragmentById(viewId) as? EpubNavigatorFragment
                        fragment?.goBackward(true)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Önceki Sayfa")
                    }

                    Slider(
                        value = currentEpubProgress,
                        onValueChange = {
                            currentEpubProgress = it
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = CsReaderTheme.colors.primary,
                            activeTrackColor = CsReaderTheme.colors.primary
                        )
                    )

                    IconButton(onClick = {
                        val fragment = activity.supportFragmentManager.findFragmentById(viewId) as? EpubNavigatorFragment
                        fragment?.goForward(true)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Sonraki Sayfa")
                    }
                }
            }
        }

        // Apple Books / Kindle Tarzı Kayan Hızlı Vurgulama & Aksiyon Çubuğu
        AnimatedVisibility(
            visible = highlightSelection != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showControls) 90.dp else 24.dp, start = 16.dp, end = 16.dp)
        ) {
            highlightSelection?.let { selection ->
                val selectedText = selection.locator.text.highlight ?: ""
                val colors = listOf(
                    "#FFE082" to "Sarı",
                    "#A5D6A7" to "Yeşil",
                    "#90CAF9" to "Mavi",
                    "#F48FB1" to "Pembe",
                    "#CE93D8" to "Mor"
                )

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1-Tap Renkli Vurgulama Butonları
                        colors.forEach { (colorHex, _) ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(colorHex)))
                                    .border(1.dp, Color.White, CircleShape)
                                    .clickable {
                                        val locator = selection.locator
                                        val text = locator.text.highlight ?: "Alıntı"
                                        val cfi = locator.toJSON().toString()
                                        viewModel.addHighlight(text, cfi, locator.locations.position, colorHex, null) {
                                            val fragment = activity.supportFragmentManager.findFragmentById(viewId) as? SelectableNavigator
                                            fragment?.clearSelection()
                                            highlightSelection = null
                                            Toast.makeText(context, "Vurgulandı!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            )
                        }

                        VerticalDivider(modifier = Modifier.height(24.dp))

                        // Not Ekle Butonu
                        IconButton(onClick = { showHighlightDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.AddComment, contentDescription = "Not Ekle", tint = CsReaderTheme.colors.primary, modifier = Modifier.size(20.dp))
                        }

                        // Seslendir Butonu
                        IconButton(onClick = {
                            ttsManager.speak(selectedText)
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Seslendir", tint = CsReaderTheme.colors.text, modifier = Modifier.size(20.dp))
                        }

                        // Kopyala Butonu
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Alıntı", selectedText))
                            Toast.makeText(context, "Kopyalandı!", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Kopyala", tint = CsReaderTheme.colors.text, modifier = Modifier.size(18.dp))
                        }

                        // Kapat Butonu
                        IconButton(onClick = {
                            val fragment = activity.supportFragmentManager.findFragmentById(viewId) as? SelectableNavigator
                            fragment?.clearSelection()
                            highlightSelection = null
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = CsReaderTheme.colors.textMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // TTS Aktif Çalma Çubuğu
        if (ttsManager.isSpeaking) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.primary),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 60.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sesli Okunuyor...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Durdur",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { ttsManager.stop() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    // Font & Görünüm Ayar Penceresi
    if (showFontDialog) {
        Dialog(onDismissRequest = { showFontDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Okuma Ayarları", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Yazı Boyutu ($fontSize px)", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = CsReaderTheme.colors.text)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.setFontSize((fontSize - 2).coerceAtLeast(12)) },
                            modifier = Modifier.clip(CircleShape).background(CsReaderTheme.colors.bg).border(1.dp, CsReaderTheme.colors.border, CircleShape)
                        ) {
                            Text("A-", fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                        }

                        Text(
                            text = "$fontSize px",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = CsReaderTheme.colors.text,
                            modifier = Modifier.padding(horizontal = 28.dp)
                        )

                        IconButton(
                            onClick = { viewModel.setFontSize((fontSize + 2).coerceAtMost(32)) },
                            modifier = Modifier.clip(CircleShape).background(CsReaderTheme.colors.bg).border(1.dp, CsReaderTheme.colors.border, CircleShape)
                        ) {
                            Text("A+", fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text("Renk & Sayfa Teması", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = CsReaderTheme.colors.text)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "light" to ("Aydınlık" to Color.White),
                            "dark" to ("Karanlık" to Color(0xFF1E293B)),
                            "amoled" to ("AMOLED" to Color(0xFF000000)),
                            "sepia" to ("Sepya" to Color(0xFFFAF6EB))
                        ).forEach { (tName, pair) ->
                            val (label, bgCol) = pair
                            val isSelected = themeName.equals(tName, ignoreCase = true)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgCol)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) CsReaderTheme.colors.primary else CsReaderTheme.colors.border,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.setTheme(tName) }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (tName == "amoled" || tName == "dark") Color.White else Color(0xFF1F2937)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { showFontDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = CsReaderTheme.colors.primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Tamam", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Add Highlight & Note Dialog for EPUB (Detaylı Not Ekleme)
    if (showHighlightDialog && highlightSelection != null) {
        var note by remember { mutableStateOf("") }
        var selectedColor by remember { mutableStateOf("#FFE082") }
        val colors = listOf(
            "#FFE082" to "Sarı",
            "#A5D6A7" to "Yeşil",
            "#90CAF9" to "Mavi",
            "#F48FB1" to "Pembe",
            "#CE93D8" to "Mor"
        )

        val selectedText = highlightSelection!!.locator.text.highlight ?: ""

        Dialog(onDismissRequest = { showHighlightDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Vurgula ve Not Ekle", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (selectedText.isNotBlank()) {
                        Text(
                            text = "\"${selectedText.take(90)}...\"",
                            fontSize = 13.sp,
                            color = CsReaderTheme.colors.textMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Notunuz (İsteğe bağlı)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Renk:", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CsReaderTheme.colors.text)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        colors.forEach { (colorHex, _) ->
                            val isSelected = selectedColor.equals(colorHex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(colorHex)))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) CsReaderTheme.colors.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = colorHex }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showHighlightDialog = false }) {
                            Text("İptal")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val locator = highlightSelection!!.locator
                                val text = locator.text.highlight ?: "Alıntı"
                                val cfi = locator.toJSON().toString()
                                viewModel.addHighlight(text, cfi, locator.locations.position, selectedColor, note) {
                                    val fragment = activity.supportFragmentManager.findFragmentById(viewId) as? SelectableNavigator
                                    fragment?.clearSelection()
                                    showHighlightDialog = false
                                    highlightSelection = null
                                    Toast.makeText(context, "Vurgulandı ve kaydedildi!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CsReaderTheme.colors.primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Kaydet", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Edit/Delete Existing Highlight Dialog for EPUB
    activeHighlightForEdit?.let { hl ->
        PdfNoteDetailDialog(
            highlight = hl,
            onDismiss = { activeHighlightForEdit = null },
            onUpdate = { updatedHl ->
                viewModel.updateHighlight(updatedHl)
                activeHighlightForEdit = null
                Toast.makeText(context, "Not güncellendi!", Toast.LENGTH_SHORT).show()
            },
            onDelete = { id ->
                viewModel.deleteHighlight(id)
                activeHighlightForEdit = null
                Toast.makeText(context, "Vurgulama silindi.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
}

