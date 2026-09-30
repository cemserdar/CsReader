package com.anonymous.csreader.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anonymous.csreader.data.BookDao
import com.anonymous.csreader.data.BookEntity
import com.anonymous.csreader.data.HighlightDao
import com.anonymous.csreader.ui.components.*
import com.anonymous.csreader.ui.theme.CsReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

class LibraryViewModel(
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao? = null
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _activeTab = MutableStateFlow("all") // "all", "reading", "favorite", "epub", "pdf"
    val activeTab: StateFlow<String> = _activeTab

    val booksState: StateFlow<List<BookEntity>> = bookDao.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val notesCountState: StateFlow<Int> = (highlightDao?.getAllHighlights()?.map { it.size } ?: flowOf(0))
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val filteredBooks: StateFlow<List<BookEntity>> = combine(
        booksState, _searchQuery, _activeTab
    ) { books, query, tab ->
        books.filter { book ->
            val matchesSearch = book.title.contains(query, ignoreCase = true) ||
                    book.author.contains(query, ignoreCase = true)
            val matchesTab = when (tab) {
                "reading" -> book.progress > 0f && book.progress < 1f
                "favorite" -> book.favorite
                "epub" -> book.type.equals("epub", ignoreCase = true)
                "pdf" -> book.type.equals("pdf", ignoreCase = true)
                else -> true
            }
            matchesSearch && matchesTab
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setActiveTab(tab: String) {
        _activeTab.value = tab
    }

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.updateBook(book.copy(favorite = !book.favorite))
        }
    }

    fun resetProgress(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            bookDao.updateBook(
                book.copy(
                    progress = 0f,
                    lastCfi = null,
                    lastPage = null
                )
            )
        }
    }

    fun deleteBook(context: Context, book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(book.uri)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            bookDao.deleteBookById(book.id)
        }
    }

    fun scanLibrary(context: Context, onComplete: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val books = bookDao.getAllBooks().first()
            val existingUris = books.map { it.uri }.toSet()
            val filesDir = context.filesDir
            val files = filesDir.listFiles() ?: emptyArray()
            var addedCount = 0

            for (file in files) {
                val lower = file.name.lowercase()
                if (!lower.endsWith(".epub") && !lower.endsWith(".pdf")) continue
                val uri = file.absolutePath
                if (existingUris.contains(uri)) continue

                val fileBaseName = file.nameWithoutExtension
                var title = fileBaseName
                var author = "Bilinmeyen Yazar"

                val dashIndex = fileBaseName.indexOf('-')
                if (dashIndex > 0) {
                    title = fileBaseName.substring(0, dashIndex).trim()
                    author = fileBaseName.substring(dashIndex + 1).trim()
                }

                val bookType = if (lower.endsWith(".epub")) "epub" else "pdf"
                val newBook = BookEntity(
                    id = "book_${System.currentTimeMillis()}_${(1000..9999).random()}",
                    title = title,
                    author = author,
                    uri = uri,
                    type = bookType,
                    progress = 0f,
                    lastCfi = null,
                    lastPage = null,
                    lastRead = System.currentTimeMillis(),
                    addedDate = System.currentTimeMillis(),
                    favorite = false
                )
                bookDao.insertBook(newBook)
                addedCount++
            }
            withContext(Dispatchers.Main) {
                onComplete(addedCount)
            }
        }
    }

    fun importFolder(context: Context, treeUri: Uri, onProgress: (String) -> Unit, onComplete: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var importedCount = 0
            try {
                val contentResolver = context.contentResolver
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val queue = ArrayDeque<String>()
                queue.add(rootDocId)

                val books = bookDao.getAllBooks().first()
                val existingTitles = books.map { it.title.lowercase().trim() }.toSet()

                while (queue.isNotEmpty()) {
                    val docId = queue.removeFirst()
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

                    contentResolver.query(
                        childrenUri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE
                        ),
                        null, null, null
                    )?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                        while (cursor.moveToNext()) {
                            val childId = cursor.getString(idIdx)
                            val childName = cursor.getString(nameIdx) ?: ""
                            val childMime = cursor.getString(mimeIdx) ?: ""

                            if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                queue.add(childId)
                            } else {
                                val lowerName = childName.lowercase()
                                val isEpub = lowerName.endsWith(".epub") || childMime == "application/epub+zip"
                                val isPdf = lowerName.endsWith(".pdf") || childMime == "application/pdf"

                                if (isEpub || isPdf) {
                                    val fileBaseName = childName.substringBeforeLast(".")
                                    val title = fileBaseName

                                    if (existingTitles.contains(title.lowercase().trim())) {
                                        continue
                                    }

                                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)

                                    withContext(Dispatchers.Main) {
                                        onProgress("Kopyalanıyor:\n$childName")
                                    }

                                    val cleanName = childName.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
                                    val uniqueFilename = "${System.currentTimeMillis()}_$cleanName"
                                    val targetFile = File(context.filesDir, uniqueFilename)

                                    try {
                                        contentResolver.openInputStream(childUri)?.use { input ->
                                            targetFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }

                                        var bookTitle = fileBaseName
                                        var author = "Bilinmeyen Yazar"
                                        val dashIndex = fileBaseName.indexOf('-')
                                        if (dashIndex > 0) {
                                            bookTitle = fileBaseName.substring(0, dashIndex).trim()
                                            author = fileBaseName.substring(dashIndex + 1).trim()
                                        }

                                        val newBook = BookEntity(
                                            id = "book_${System.currentTimeMillis()}_${(1000..9999).random()}",
                                            title = bookTitle,
                                            author = author,
                                            uri = targetFile.absolutePath,
                                            type = if (isEpub) "epub" else "pdf",
                                            progress = 0f,
                                            lastCfi = null,
                                            lastPage = null,
                                            lastRead = System.currentTimeMillis(),
                                            addedDate = System.currentTimeMillis(),
                                            favorite = false
                                        )
                                        bookDao.insertBook(newBook)
                                        importedCount++
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                onComplete(importedCount)
            }
        }
    }

    fun importBook(context: Context, uri: Uri, onComplete: (BookEntity?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                var filename = "imported_book_${System.currentTimeMillis()}"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        filename = cursor.getString(nameIndex)
                    }
                }

                val cleanName = filename.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
                val uniqueFilename = "${System.currentTimeMillis()}_$cleanName"
                val targetFile = File(context.filesDir, uniqueFilename)

                contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val fileBaseName = filename.substringBeforeLast(".")
                var title = fileBaseName
                var author = "Bilinmeyen Yazar"

                val dashIndex = fileBaseName.indexOf('-')
                if (dashIndex > 0) {
                    title = fileBaseName.substring(0, dashIndex).trim()
                    author = fileBaseName.substring(dashIndex + 1).trim()
                }

                val bookType = if (filename.lowercase().endsWith(".epub")) "epub" else "pdf"
                val newBook = BookEntity(
                    id = "book_${System.currentTimeMillis()}",
                    title = title,
                    author = author,
                    uri = targetFile.absolutePath,
                    type = bookType,
                    progress = 0f,
                    lastCfi = null,
                    lastPage = null,
                    lastRead = System.currentTimeMillis(),
                    addedDate = System.currentTimeMillis(),
                    favorite = false
                )

                bookDao.insertBook(newBook)
                withContext(Dispatchers.Main) {
                    onComplete(newBook)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(null)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    bookDao: BookDao,
    highlightDao: HighlightDao? = null,
    onSelectBook: (BookEntity) -> Unit,
    onNavigateToNotes: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(bookDao, highlightDao) as T
        }
    })

    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val books by viewModel.booksState.collectAsState()
    val filteredBooks by viewModel.filteredBooks.collectAsState()
    val notesCount by viewModel.notesCountState.collectAsState()

    var isGridView by remember { mutableStateOf(true) }
    var selectedBookForOptions by remember { mutableStateOf<BookEntity?>(null) }
    var showDeleteDialogForBook by remember { mutableStateOf<BookEntity?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var scanProgressText by remember { mutableStateOf("") }

    // En son okunan öne çıkan eser (Hero Showcase)
    val heroBook = remember(books) {
        books.filter { it.progress > 0f }.maxByOrNull { it.lastRead }
            ?: books.maxByOrNull { it.lastRead }
            ?: books.firstOrNull()
    }

    // Zamana göre dinamik selamlama
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour) {
        when (currentHour) {
            in 5..11 -> "Günaydın"
            in 12..17 -> "İyi günler"
            in 18..22 -> "İyi akşamlar"
            else -> "Huzurlu geceler"
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            importing = true
            viewModel.importBook(context, uri) { importedBook ->
                importing = false
                if (importedBook != null) {
                    Toast.makeText(context, "Kitap kütüphaneye eklendi", Toast.LENGTH_SHORT).show()
                    onSelectBook(importedBook)
                } else {
                    Toast.makeText(context, "Kitap eklenirken hata oluştu", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            scanning = true
            scanProgressText = "Tarama başlatılıyor..."
            viewModel.importFolder(context, uri, { progress ->
                scanProgressText = progress
            }) { count ->
                scanning = false
                scanProgressText = ""
                Toast.makeText(context, "Tarama tamamlandı. $count yeni eser kütüphaneye dahil edildi.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CsReaderTheme.colors.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Editoryal Üst Başlık & Selamlama Barı
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CsReader",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = CsReaderTheme.colors.text
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CsReaderTheme.colors.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "KİTAPLIK",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CsReaderTheme.colors.primary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "$greeting • ${books.size} Eser",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = CsReaderTheme.colors.textMuted
                    )
                }

                // Sağ Üst Kontroller: Görünüm Değiştirici (3D Raf Grid vs. Liste)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CsReaderTheme.colors.cardBg)
                            .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(12.dp))
                            .size(42.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                            contentDescription = if (isGridView) "Liste Görünümü" else "3D Raf Görünümü",
                            tint = CsReaderTheme.colors.text,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 2. Arama Girişi
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Kitap, yazar veya konu ara...", color = CsReaderTheme.colors.textMuted, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = CsReaderTheme.colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Temizle",
                                tint = CsReaderTheme.colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CsReaderTheme.colors.cardBg,
                    unfocusedContainerColor = CsReaderTheme.colors.cardBg,
                    focusedBorderColor = CsReaderTheme.colors.primary,
                    unfocusedBorderColor = CsReaderTheme.colors.border,
                    focusedTextColor = CsReaderTheme.colors.text,
                    unfocusedTextColor = CsReaderTheme.colors.text
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            // 3. Editoryal Filtre Kapsülleri (Filter Pills)
            val readingCount = remember(books) { books.count { it.progress > 0f && it.progress < 1f } }
            val favCount = remember(books) { books.count { it.favorite } }
            val epubCount = remember(books) { books.count { it.type.equals("epub", ignoreCase = true) } }
            val pdfCount = remember(books) { books.count { it.type.equals("pdf", ignoreCase = true) } }

            val filterList = listOf(
                "all" to "Tümü (${books.size})",
                "reading" to "Okunanlar ($readingCount)",
                "favorite" to "Favoriler ($favCount)",
                "epub" to "EPUB ($epubCount)",
                "pdf" to "PDF ($pdfCount)"
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterList) { (tabId, label) ->
                    val selected = activeTab == tabId
                    val isPdf = tabId == "pdf"
                    val activeBg = if (isPdf) Color(0x1AEF4444) else CsReaderTheme.colors.primary.copy(alpha = 0.12f)
                    val activeColor = if (isPdf) Color(0xFFEF4444) else CsReaderTheme.colors.primary

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) activeBg else CsReaderTheme.colors.cardBg)
                            .border(
                                width = 1.dp,
                                color = if (selected) activeColor else CsReaderTheme.colors.border,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { viewModel.setActiveTab(tabId) }
                            .padding(vertical = 7.dp, horizontal = 14.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) activeColor else CsReaderTheme.colors.textMuted
                        )
                    }
                }
            }

            // 4. Kitap Listesi / Raflar
            if (filteredBooks.isEmpty()) {
                // Boş Durum (Empty State)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(CsReaderTheme.colors.cardBg)
                                .border(1.dp, CsReaderTheme.colors.border, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = CsReaderTheme.colors.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = if (searchQuery.isNotEmpty()) "Eser Bulunamadı" else "Kütüphaneniz Boş",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = CsReaderTheme.colors.text
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (searchQuery.isNotEmpty())
                                "\"$searchQuery\" kriterine uyan hiçbir eser bulunamadı."
                            else
                                "Cihazınızdaki EPUB ve PDF kitapları ekleyerek okumaya hemen başlayabilirsiniz.",
                            fontSize = 13.sp,
                            color = CsReaderTheme.colors.textMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )

                        if (searchQuery.isEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { filePickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = CsReaderTheme.colors.primary),
                                shape = RoundedCornerShape(22.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kitap Seç ve Ekle", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CsReaderTheme.colors.text),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CsReaderTheme.colors.border),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Klasör Tara (Toplu)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            } else {
                // Kitaplar Dolu
                val showHero = heroBook != null && searchQuery.isEmpty() && activeTab == "all"

                if (isGridView) {
                    // 3D Raf Grid Görünümü (2 Kolon)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 125.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (showHero) {
                            item(span = { GridItemSpan(2) }, key = "hero_spotlight") {
                                heroBook?.let { hero ->
                                    HeroContinueReadingCard(
                                        book = hero,
                                        onContinueReading = { onSelectBook(hero) },
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                            }
                        }

                        items(filteredBooks, key = { it.id }) { book ->
                            BookShelfCard(
                                book = book,
                                onSelectBook = onSelectBook,
                                onToggleFavorite = { viewModel.toggleFavorite(book) },
                                onShowOptions = { selectedBookForOptions = book }
                            )
                        }
                    }
                } else {
                    // Editoryal Liste Görünümü
                    LazyColumn(
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 125.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (showHero) {
                            item(key = "hero_spotlight") {
                                heroBook?.let { hero ->
                                    HeroContinueReadingCard(
                                        book = hero,
                                        onContinueReading = { onSelectBook(hero) },
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                            }
                        }

                        items(filteredBooks, key = { it.id }) { book ->
                            BookListRowCard(
                                book = book,
                                onSelectBook = onSelectBook,
                                onToggleFavorite = { viewModel.toggleFavorite(book) },
                                onShowOptions = { selectedBookForOptions = book }
                            )
                        }
                    }
                }
            }
        }

        // 5. Yüzen Ada Navigasyon İskeleti (Floating Navigation Island)
        FloatingNavigationIsland(
            currentTab = NavigationTab.LIBRARY,
            notesCount = notesCount,
            onTabSelected = { tab ->
                when (tab) {
                    NavigationTab.LIBRARY -> { /* Zaten kütüphanedeyiz */ }
                    NavigationTab.NOTES -> onNavigateToNotes()
                    NavigationTab.SETTINGS -> onNavigateToSettings()
                }
            },
            onAddNewBook = { filePickerLauncher.launch("*/*") },
            onScanFolder = { folderPickerLauncher.launch(null) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }

    // 6. Kitap Detay & Seçenekler Modalı (Artisanal Bottom Sheet)
    selectedBookForOptions?.let { book ->
        val progressPercent = (book.progress * 100).toInt().coerceIn(0, 100)
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { selectedBookForOptions = null },
            sheetState = sheetState,
            containerColor = CsReaderTheme.colors.cardBg,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(CsReaderTheme.colors.border)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                // Kitap Bilgi Başlığı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ArtisanalBookCover(
                        book = book,
                        size = BookCoverSize.COMPACT
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = CsReaderTheme.colors.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = book.author,
                            fontSize = 13.sp,
                            color = CsReaderTheme.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CsReaderTheme.colors.primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = book.type.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CsReaderTheme.colors.primary
                                )
                            }

                            Text(
                                text = "• %$progressPercent okundu",
                                fontSize = 11.sp,
                                color = CsReaderTheme.colors.textMuted
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = CsReaderTheme.colors.border
                )

                // Aksiyon Listesi
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 1. Okumaya Başla / Kaldığın Yerden Oku
                    ListItem(
                        headlineContent = {
                            Text(
                                text = if (book.progress > 0f) "Kaldığın Yerden Oku" else "Okumaya Başla",
                                fontWeight = FontWeight.SemiBold,
                                color = CsReaderTheme.colors.text
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = CsReaderTheme.colors.primary
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedBookForOptions = null
                                onSelectBook(book)
                            }
                    )

                    // 2. Favorilere Ekle / Çıkar
                    ListItem(
                        headlineContent = {
                            Text(
                                text = if (book.favorite) "Favorilerden Kaldır" else "Favorilere Ekle",
                                fontWeight = FontWeight.SemiBold,
                                color = CsReaderTheme.colors.text
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = if (book.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (book.favorite) Color(0xFFEF4444) else CsReaderTheme.colors.textMuted
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.toggleFavorite(book)
                                selectedBookForOptions = null
                            }
                    )

                    // 3. Okuma İlerlemesini Sıfırla
                    if (book.progress > 0f) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "İlerlemeyi Sıfırla",
                                    fontWeight = FontWeight.SemiBold,
                                    color = CsReaderTheme.colors.text
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.RestartAlt,
                                    contentDescription = null,
                                    tint = CsReaderTheme.colors.textMuted
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.resetProgress(book)
                                    selectedBookForOptions = null
                                    Toast.makeText(context, "Okuma ilerlemesi sıfırlandı", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }

                    // 4. Kitabı Sil
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "Kitaplıktan ve Cihazdan Sil",
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFEF4444)
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF4444)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val target = book
                                selectedBookForOptions = null
                                showDeleteDialogForBook = target
                            }
                    )
                }
            }
        }
    }

    // 7. Silme Onay Penceresi (Delete Confirmation Dialog)
    showDeleteDialogForBook?.let { book ->
        AlertDialog(
            onDismissRequest = { showDeleteDialogForBook = null },
            title = {
                Text(
                    text = "Kitabı Sil",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
            },
            text = {
                Text(
                    text = "\"${book.title}\" eseri kütüphanenizden ve cihazınızdan silinecektir. Bu işlem geri alınamaz."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(context, book)
                        showDeleteDialogForBook = null
                        Toast.makeText(context, "Kitap silindi", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Sil", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogForBook = null }) {
                    Text("Vazgeç")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = CsReaderTheme.colors.cardBg
        )
    }

    // 8. Klasör Tarama İlerleme Penceresi
    if (scanning && scanProgressText.isNotEmpty()) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                modifier = Modifier.padding(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CsReaderTheme.colors.border)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = CsReaderTheme.colors.primary, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Kütüphane Taranıyor",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = CsReaderTheme.colors.text,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = scanProgressText,
                        color = CsReaderTheme.colors.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
