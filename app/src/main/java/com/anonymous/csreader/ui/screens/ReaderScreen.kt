package com.anonymous.csreader.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anonymous.csreader.data.*
import com.anonymous.csreader.ui.theme.CsReaderTheme
import com.anonymous.csreader.utils.AssetUtils
import java.io.File
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun setTheme(theme: String) {
        settingsManager.theme = theme
        themeState.value = theme
    }

    fun setFontSize(size: Int) {
        settingsManager.fontSize = size
        fontSizeState.value = size
    }

    fun setPageTransition(transition: String) {
        settingsManager.pageTransition = transition
        pageTransitionState.value = transition
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
            withContext(Dispatchers.Main) {
                onComplete(newHl)
            }
        }
    }

    fun updateHighlightNote(hlId: String, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            highlightsState.value.find { it.id == hlId }?.let { hl ->
                highlightDao.updateHighlight(hl.copy(note = note))
            }
        }
    }

    fun deleteHighlight(hlId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            highlightDao.deleteHighlightById(hlId)
        }
    }
}

// Javascript bridge shim
class AndroidBridge(
    private val onMessageReceived: (String) -> Unit
) {
    @JavascriptInterface
    fun postMessage(data: String) {
        onMessageReceived(data)
    }
}

// WebView messages models
data class SelectionMessage(val text: String, val cfiRange: String?, val page: Int?)
data class TocChapter(val label: String, val href: String)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(
    bookDao: BookDao,
    highlightDao: HighlightDao,
    settingsManager: SettingsManager,
    book: BookEntity,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ReaderViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ReaderViewModel(bookDao, highlightDao, settingsManager, book) as T
        }
    })

    val themeName by viewModel.themeState.collectAsState()
    val fontSize by viewModel.fontSizeState.collectAsState()
    val pageTransition by viewModel.pageTransitionState.collectAsState()
    val highlights by viewModel.highlightsState.collectAsState()

    var loading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Navigation and book progress state
    var bookProgress by remember { mutableStateOf(book.progress) }
    var currentCfi by remember { mutableStateOf(book.lastCfi ?: "") }
    var currentPage by remember { mutableStateOf(book.lastPage ?: 1) }
    var totalPagesEstimate by remember { mutableStateOf(0) }
    var bookRealTitle by remember { mutableStateOf(book.title) }

    // Navigation debounce - must be longer than animation duration
    var lastNavTime by remember { mutableLongStateOf(0L) }
    val navDebounce = 800L
    var bookLoaded by remember { mutableStateOf(false) }

    // TOC
    var tocList by remember { mutableStateOf<List<TocChapter>>(emptyList()) }

    // Dialog / Modal Visibility States
    var showStyleDialog by remember { mutableStateOf(false) }
    var showTocDialog by remember { mutableStateOf(false) }
    var showHighlightDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    // Active Selection State
    var selectedText by remember { mutableStateOf("") }
    var selectedCfiRange by remember { mutableStateOf<String?>(null) }
    var selectedPage by remember { mutableStateOf<Int?>(null) }
    var activeHighlight by remember { mutableStateOf<HighlightEntity?>(null) }
    var noteText by remember { mutableStateOf("") }

    // Helper functions to send actions to JS
    // Calls handleCommand() directly — more reliable than window.postMessage from evaluateJavascript
    val triggerAction = { action: String, payload: Map<String, Any> ->
        webViewRef?.let { webView ->
            val payloadMap = mutableMapOf<String, Any>("action" to action)
            payloadMap.putAll(payload)
            val jsonString = Gson().toJson(payloadMap)
            val jsStringLiteral = Gson().toJson(jsonString)
            val js = "typeof handleCommand === 'function' && handleCommand($jsStringLiteral);"
            webView.evaluateJavascript(js, null)
        }
    }

    val navigateNext = {
        val now = System.currentTimeMillis()
        if (now - lastNavTime > navDebounce) {
            lastNavTime = now  // set BEFORE sending command to block rapid re-entry
            if (book.type == "pdf") {
                if (currentPage < totalPagesEstimate) {
                    triggerAction("goToPage", mapOf("page" to currentPage + 1))
                }
            } else {
                triggerAction("next", emptyMap())
            }
        }
    }

    val navigatePrev = {
        val now = System.currentTimeMillis()
        if (now - lastNavTime > navDebounce) {
            lastNavTime = now  // set BEFORE sending command to block rapid re-entry
            if (book.type == "pdf") {
                if (currentPage > 1) {
                    triggerAction("goToPage", mapOf("page" to currentPage - 1))
                }
            } else {
                triggerAction("prev", emptyMap())
            }
        }
    }

    // Safety fallback: if book never fires relocated/pageChange, hide loader after 10s
    LaunchedEffect(bookLoaded) {
        if (!bookLoaded) {
            delay(10_000L)
            loading = false
        }
    }

    // Load WebView function
    val loadBookInWebView = {
        // Use virtual URL - directly accessible via custom WebViewClient interception
        val bookFileName = File(book.uri).name
        val bookUrl = "https://appassets.androidplatform.net/files/$bookFileName"
        val payload = mapOf(
            "bookPath" to bookUrl,
            "initialCfi" to (book.lastCfi ?: ""),
            "initialPage" to (book.lastPage ?: 1),
            "theme" to themeName,
            "fontSize" to fontSize,
            "pageTransition" to pageTransition,
            "highlights" to highlights.map { mapOf("cfiRange" to it.cfiRange, "color" to it.color) }
        )
        triggerAction("load", payload)
        // loading overlay will be hidden after the first 'relocated'/'pageChange' event fires
    }

    // Handles messages from JS
    val handleMessage = { json: String ->
        try {
            val map = Gson().fromJson(json, Map::class.java)
            val type = map["type"] as? String ?: ""
            when (type) {
                "relocated" -> {
                    val cfi = map["cfi"] as? String ?: ""
                    val progress = (map["progress"] as? Double)?.toFloat() ?: 0f
                    bookProgress = progress
                    currentCfi = cfi
                    if (!bookLoaded) { bookLoaded = true; loading = false }
                    viewModel.updateProgress(progress, cfi, null)
                }
                "pageChange" -> {
                    val page = (map["page"] as? Double)?.toInt() ?: 1
                    currentPage = page
                    val pdfProgress = if (totalPagesEstimate > 0) (page - 1).toFloat() / totalPagesEstimate else 0f
                    bookProgress = pdfProgress
                    if (!bookLoaded) { bookLoaded = true; loading = false }
                    viewModel.updateProgress(pdfProgress, null, page)
                }
                "progressReady" -> {
                    val progress = (map["progress"] as? Double)?.toFloat() ?: 0f
                    bookProgress = progress
                }
                "toc" -> {
                    val chapters = (map["chapters"] as? List<*>)?.map {
                        val chapMap = it as Map<*, *>
                        TocChapter(chapMap["label"] as String, chapMap["href"] as String)
                    } ?: emptyList()
                    tocList = chapters
                }
                "metadata" -> {
                    val title = map["title"] as? String
                    if (!title.isNullOrEmpty()) {
                        bookRealTitle = title
                    }
                    val totalPages = (map["totalPages"] as? Double)?.toInt()
                    if (totalPages != null) {
                        totalPagesEstimate = totalPages
                    }
                }
                "click" -> {
                    showControls = !showControls
                }
                "selected" -> {
                    selectedText = map["text"] as? String ?: ""
                    selectedCfiRange = map["cfiRange"] as? String
                    selectedPage = (map["page"] as? Double)?.toInt()
                    showHighlightDialog = true
                }
                "highlightClicked" -> {
                    val clickedCfi = map["cfiRange"] as? String
                    val tappedHl = highlights.find { it.cfiRange == clickedCfi }
                    if (tappedHl != null) {
                        activeHighlight = tappedHl
                        noteText = tappedHl.note ?: ""
                        showNoteDialog = true
                    }
                }
                "error" -> {
                    val errMsg = map["message"] as? String ?: "Bilinmeyen hata"
                    Toast.makeText(context, "Okuyucu Hatası: $errMsg", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CsReaderTheme.colors.bg)
    ) {
        // WebView Integration
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    // Allow file access so epub.js can fetch the epub binary from internal storage
                    @Suppress("DEPRECATION")
                    settings.allowFileAccess = true
                    @Suppress("DEPRECATION")
                    settings.allowContentAccess = true
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true

                    addJavascriptInterface(AndroidBridge { handleMessage(it) }, "AndroidBridge")

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            consoleMessage?.let {
                                Log.d("ReaderWebView", "${it.message()} -- line ${it.lineNumber()} of ${it.sourceId()}")
                            }
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Inject AndroidBridge shim for sendToAndroid() fallback
                            val shimScript = """
                                window.ReactNativeWebView = {
                                    postMessage: function(data) { AndroidBridge.postMessage(data); }
                                };
                            """.trimIndent()
                            view?.evaluateJavascript(shimScript) {
                                loadBookInWebView()
                            }
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url ?: return null
                            if (url.scheme == "https" && url.host == "appassets.androidplatform.net") {
                                val path = url.path ?: ""
                                if (path.startsWith("/assets/")) {
                                    val assetName = path.substringAfter("/assets/")
                                    val mimeType = when {
                                        assetName.endsWith(".html") -> "text/html"
                                        assetName.endsWith(".js") -> "text/javascript"
                                        assetName.endsWith(".css") -> "text/css"
                                        else -> "application/octet-stream"
                                    }
                                    return try {
                                        val stream = ctx.assets.open(assetName)
                                        WebResourceResponse(mimeType, "UTF-8", stream)
                                    } catch (e: Exception) {
                                        Log.e("ReaderWebView", "Error loading asset: $assetName", e)
                                        null
                                    }
                                } else if (path.startsWith("/files/")) {
                                    val fileName = path.substringAfter("/files/")
                                    val file = File(ctx.filesDir, fileName)
                                    if (file.exists()) {
                                        val mimeType = if (fileName.endsWith(".epub")) {
                                            "application/epub+zip"
                                        } else {
                                            "application/pdf"
                                        }
                                        return try {
                                            val stream = file.inputStream()
                                            WebResourceResponse(mimeType, null, stream)
                                        } catch (e: Exception) {
                                            Log.e("ReaderWebView", "Error loading book file: $fileName", e)
                                            null
                                        }
                                    } else {
                                        Log.e("ReaderWebView", "Book file not found: ${file.absolutePath}")
                                    }
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }

                    val assetFile = if (book.type == "epub") "reader.html" else "pdf_viewer.html"
                    loadUrl("https://appassets.androidplatform.net/assets/$assetFile")
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                // Keep theme updated
            }
        )

        // Left tap zone for prev page (only visible as a touch target when controls are hidden)
        if (!showControls && !loading) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .align(Alignment.CenterStart)
                    .clickable { navigatePrev() }
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .align(Alignment.CenterEnd)
                    .clickable { navigateNext() }
            )
        }

        // Loading Overlay
        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CsReaderTheme.colors.bg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CsReaderTheme.colors.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Kitap yükleniyor...", color = CsReaderTheme.colors.textMuted)
                }
            }
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
                    .border(1.dp, CsReaderTheme.colors.border)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Geri", tint = CsReaderTheme.colors.text, modifier = Modifier.size(26.dp))
                }

                Text(
                    text = bookRealTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CsReaderTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { showStyleDialog = true }) {
                    Icon(Icons.Default.Palette, contentDescription = "Görünüm Ayarları", tint = CsReaderTheme.colors.text)
                }

                IconButton(onClick = { showTocDialog = true }) {
                    Icon(Icons.Default.Book, contentDescription = "İçindekiler", tint = CsReaderTheme.colors.text)
                }
            }
        }

        // Footer Control Bar
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CsReaderTheme.colors.cardBg)
                    .navigationBarsPadding()
                    .border(1.dp, CsReaderTheme.colors.border)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = navigatePrev,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CsReaderTheme.colors.bg)
                        .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(10.dp))
                        .size(44.dp)
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Geri", tint = CsReaderTheme.colors.text, modifier = Modifier.size(26.dp))
                }

                Text(
                    text = if (book.type == "epub") "İlerleme: %${(bookProgress * 100).toInt()}" else "Sayfa: $currentPage / ${if (totalPagesEstimate > 0) totalPagesEstimate else "?"}",
                    fontSize = 12.sp,
                    color = CsReaderTheme.colors.textMuted
                )

                IconButton(
                    onClick = navigateNext,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CsReaderTheme.colors.bg)
                        .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(10.dp))
                        .size(44.dp)
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "İleri", tint = CsReaderTheme.colors.text, modifier = Modifier.size(26.dp))
                }
            }
        }
    }

    // --- DIALOGS / DRAWERS ---

    // Style settings Dialog
    if (showStyleDialog) {
        Dialog(onDismissRequest = { showStyleDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Görünüm Ayarları", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                        IconButton(onClick = { showStyleDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = CsReaderTheme.colors.text)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("TEMA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.textMuted)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Theme selector grid
                    listOf(
                        "light" to "Aydınlık",
                        "dark" to "Karanlık",
                        "sepia" to "Sepya",
                        "forest" to "Yeşil"
                    ).chunked(2).forEach { rowThemes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowThemes.forEach { (tName, tLabel) ->
                                val active = themeName == tName
                                val bgCol = when (tName) {
                                    "light" -> Color.White
                                    "dark" -> Color(0xFF121212)
                                    "sepia" -> Color(0xFFF4ECD8)
                                    else -> Color(0xFFE8EFE9)
                                }
                                val textCol = if (tName == "dark") Color.White else Color(0xFF333333)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bgCol)
                                        .border(
                                            2.dp,
                                            if (active) CsReaderTheme.colors.primary else CsReaderTheme.colors.border,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            viewModel.setTheme(tName)
                                            triggerAction("setTheme", mapOf("theme" to tName))
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tLabel,
                                        color = textCol,
                                        fontSize = 13.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    if (book.type == "epub") {
                        Spacer(modifier = Modifier.height(20.dp))

                        Text("YAZI BOYUTU", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.textMuted)

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val newSize = (fontSize - 2).coerceAtLeast(12)
                                    viewModel.setFontSize(newSize)
                                    triggerAction("setFontSize", mapOf("fontSize" to newSize))
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(CsReaderTheme.colors.bg)
                                    .border(1.dp, CsReaderTheme.colors.border, CircleShape)
                            ) {
                                Text("A-", fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                            }

                            Text(
                                text = "$fontSize px",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = CsReaderTheme.colors.text,
                                modifier = Modifier.padding(horizontal = 30.dp)
                            )

                            IconButton(
                                onClick = {
                                    val newSize = (fontSize + 2).coerceAtMost(28)
                                    viewModel.setFontSize(newSize)
                                    triggerAction("setFontSize", mapOf("fontSize" to newSize))
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(CsReaderTheme.colors.bg)
                                    .border(1.dp, CsReaderTheme.colors.border, CircleShape)
                            ) {
                                Text("A+", fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("SAYFA EFEKTİ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.textMuted)

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "none" to "Yok",
                            "slide" to "Kaydır",
                            "fade" to "Solma"
                        ).forEach { (transId, transLabel) ->
                            val active = pageTransition == transId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) CsReaderTheme.colors.accent else CsReaderTheme.colors.bg)
                                    .border(
                                        2.dp,
                                        if (active) CsReaderTheme.colors.primary else CsReaderTheme.colors.border,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        viewModel.setPageTransition(transId)
                                        triggerAction("setTransition", mapOf("pageTransition" to transId))
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = transLabel,
                                    color = if (active) CsReaderTheme.colors.primary else CsReaderTheme.colors.textMuted,
                                    fontSize = 13.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Highlight color picker Dialog
    if (showHighlightDialog) {
        Dialog(onDismissRequest = { showHighlightDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "\"$selectedText\"",
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                        color = CsReaderTheme.colors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            "yellow" to Color(0xFFFEF08A),
                            "green" to Color(0xFFBBF7D0),
                            "pink" to Color(0xFFFBCFE8),
                            "blue" to Color(0xFFBFDBFE),
                            "underline" to Color.Transparent
                        ).forEach { (colorName, colorVal) ->
                            val isUnderline = colorName == "underline"
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colorVal)
                                    .border(
                                        width = if (isUnderline) 2.dp else 1.dp,
                                        color = if (isUnderline) Color(0xFFEF4444) else Color(0xFFE5E7EB),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        viewModel.addHighlight(
                                            selectedText,
                                            selectedCfiRange,
                                            selectedPage,
                                            colorName
                                        ) { newHl ->
                                            triggerAction(
                                                "addHighlight",
                                                mapOf(
                                                    "cfiRange" to (selectedCfiRange ?: ""),
                                                    "page" to (selectedPage ?: 0),
                                                    "color" to colorName
                                                )
                                            )
                                            activeHighlight = newHl
                                            noteText = ""
                                            showHighlightDialog = false
                                            showNoteDialog = true
                                        }
                                    }
                            ) {
                                if (isUnderline) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .width(20.dp)
                                            .height(2.dp)
                                            .background(Color.Red)
                                    )
                                }
                            }
                        }

                        VerticalDivider(modifier = Modifier.height(30.dp))

                        // Share
                        IconButton(onClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "\"$selectedText\"\n\n- CsReader ile \"$bookRealTitle\" kitabından paylaşıldı.")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Paylaş"))
                            showHighlightDialog = false
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Paylaş", tint = CsReaderTheme.colors.text)
                        }

                        IconButton(onClick = { showHighlightDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = CsReaderTheme.colors.text)
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Note Dialog
    if (showNoteDialog) {
        Dialog(onDismissRequest = { showNoteDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Not Ekle / Düzenle", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                        IconButton(onClick = { showNoteDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = CsReaderTheme.colors.text)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = activeHighlight?.text ?: "",
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                        color = CsReaderTheme.colors.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CsReaderTheme.colors.bg)
                            .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Düşüncelerinizi yazın...", color = CsReaderTheme.colors.textMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CsReaderTheme.colors.bg,
                            unfocusedContainerColor = CsReaderTheme.colors.bg,
                            focusedTextColor = CsReaderTheme.colors.text,
                            unfocusedTextColor = CsReaderTheme.colors.text,
                            focusedBorderColor = CsReaderTheme.colors.primary,
                            unfocusedBorderColor = CsReaderTheme.colors.border
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        activeHighlight?.let { hl ->
                            Button(
                                onClick = {
                                    triggerAction("removeHighlight", mapOf("cfiRange" to (hl.cfiRange ?: "")))
                                    viewModel.deleteHighlight(hl.id)
                                    showNoteDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sil", color = Color.White)
                            }
                        }

                        Button(
                            onClick = {
                                activeHighlight?.let { hl ->
                                    viewModel.updateHighlightNote(hl.id, noteText)
                                }
                                showNoteDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CsReaderTheme.colors.primary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text("Kaydet", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // TOC Drawer / Modal dialog
    if (showTocDialog) {
        Dialog(onDismissRequest = { showTocDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("İçerik & Notlar", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                        IconButton(onClick = { showTocDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = CsReaderTheme.colors.text)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (tocList.isNotEmpty()) {
                            Text(
                                "BÖLÜMLER",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CsReaderTheme.colors.textMuted,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            HorizontalDivider(color = CsReaderTheme.colors.border)

                            tocList.forEach { chapter ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            triggerAction("goToCfi", mapOf("cfi" to chapter.href))
                                            showTocDialog = false
                                        }
                                        .padding(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = chapter.label,
                                        color = CsReaderTheme.colors.text,
                                        fontSize = 14.sp
                                    )
                                }
                                HorizontalDivider(color = CsReaderTheme.colors.border)
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        Text(
                            text = "BU KİTAPTAKİ NOTLARIM (${highlights.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CsReaderTheme.colors.textMuted,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        HorizontalDivider(color = CsReaderTheme.colors.border)

                        if (highlights.isEmpty()) {
                            Text(
                                text = "Kitapta henüz vurgulama veya not bulunmuyor. Metinlerin üzerine basılı tutarak not ekleyebilirsiniz.",
                                fontSize = 13.sp,
                                color = CsReaderTheme.colors.textMuted,
                                modifier = Modifier.padding(vertical = 16.dp),
                                lineHeight = 18.sp
                            )
                        } else {
                            highlights.forEach { hl ->
                                val hlColor = when (hl.color) {
                                    "yellow" -> Color(0xFFFEF08A)
                                    "green" -> Color(0xFFBBF7D0)
                                    "pink" -> Color(0xFFFBCFE8)
                                    "blue" -> Color(0xFFBFDBFE)
                                    else -> Color.Transparent
                                }
                                val dateStr = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(hl.date))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CsReaderTheme.colors.bg)
                                        .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(32.dp)
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(hlColor)
                                                .border(
                                                    width = if (hl.color == "underline") 1.dp else 0.dp,
                                                    color = Color.Red,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                        )

                                        Text(
                                            text = "$dateStr ${if (hl.page != null) "(Sayfa ${hl.page})" else ""}",
                                            fontSize = 11.sp,
                                            color = CsReaderTheme.colors.textMuted
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "\"${hl.text}\"",
                                        fontSize = 13.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = CsReaderTheme.colors.text,
                                        modifier = Modifier.clickable {
                                            if (book.type == "epub" && hl.cfiRange != null) {
                                                triggerAction("goToCfi", mapOf("cfi" to hl.cfiRange))
                                            } else if (book.type == "pdf" && hl.page != null) {
                                                triggerAction("goToPage", mapOf("page" to hl.page))
                                            }
                                            showTocDialog = false
                                        }
                                    )

                                    if (!hl.note.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CsReaderTheme.colors.cardBg)
                                                .padding(6.dp)
                                                .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(6.dp))
                                                .padding(6.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, tint = CsReaderTheme.colors.primary, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(hl.note, fontSize = 12.sp, color = CsReaderTheme.colors.text)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(onClick = {
                                            activeHighlight = hl
                                            noteText = hl.note ?: ""
                                            showTocDialog = false
                                            showNoteDialog = true
                                        }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Edit, contentDescription = "Not Düzenle", tint = CsReaderTheme.colors.textMuted, modifier = Modifier.size(16.dp))
                                        }

                                        IconButton(onClick = {
                                            val shareIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "\"${hl.text}\"\n\n- CsReader ile \"$bookRealTitle\" kitabından paylaşıldı.")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Paylaş"))
                                        }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Share, contentDescription = "Paylaş", tint = CsReaderTheme.colors.textMuted, modifier = Modifier.size(16.dp))
                                        }

                                        IconButton(onClick = {
                                            triggerAction("removeHighlight", mapOf("cfiRange" to (hl.cfiRange ?: "")))
                                            viewModel.deleteHighlight(hl.id)
                                        }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
