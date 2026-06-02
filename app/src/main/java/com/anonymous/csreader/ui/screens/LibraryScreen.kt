package com.anonymous.csreader.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.anonymous.csreader.ui.theme.CsReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LibraryViewModel(private val bookDao: BookDao) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _activeTab = MutableStateFlow("all") // "all", "epub", "pdf", "favorite"
    val activeTab: StateFlow<String> = _activeTab

    val booksState: StateFlow<List<BookEntity>> = bookDao.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredBooks: StateFlow<List<BookEntity>> = combine(
        booksState, _searchQuery, _activeTab
    ) { books, query, tab ->
        books.filter { book ->
            val matchesSearch = book.title.contains(query, ignoreCase = true) ||
                    book.author.contains(query, ignoreCase = true)
            val matchesTab = when (tab) {
                "epub" -> book.type == "epub"
                "pdf" -> book.type == "pdf"
                "favorite" -> book.favorite
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

    fun deleteBook(context: Context, book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete file from internal storage
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
    onSelectBook: (BookEntity) -> Unit,
    onNavigateToNotes: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(bookDao) as T
        }
    })

    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val books by viewModel.booksState.collectAsState()
    val filteredBooks by viewModel.filteredBooks.collectAsState()

    var showDeleteDialogForBook by remember { mutableStateOf<BookEntity?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var scanProgressText by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            importing = true
            viewModel.importBook(context, uri) { importedBook ->
                importing = false
                if (importedBook != null) {
                    Toast.makeText(context, "Kitap başarıyla eklendi", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Tarama tamamlandı. $count yeni kitap eklendi.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (books.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    containerColor = CsReaderTheme.colors.primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
                ) {
                    if (importing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Add, contentDescription = "Kitap Ekle")
                    }
                }
            }
        },
        containerColor = CsReaderTheme.colors.bg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CsReader",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = CsReaderTheme.colors.text
                    )
                    Text(
                        text = "E-Kitap ve PDF Kütüphanesi",
                        fontSize = 12.sp,
                        color = CsReaderTheme.colors.textMuted
                    )
                }

                Row {
                    IconButton(
                        onClick = {
                            folderPickerLauncher.launch(null)
                        },
                        enabled = !scanning,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CsReaderTheme.colors.cardBg)
                            .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(12.dp))
                            .size(44.dp)
                    ) {
                        if (scanning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CsReaderTheme.colors.text)
                        } else {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Klasör Tara", tint = CsReaderTheme.colors.text)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onNavigateToNotes,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CsReaderTheme.colors.cardBg)
                            .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(12.dp))
                            .size(44.dp)
                    ) {
                        Icon(Icons.Default.Assignment, contentDescription = "Tüm Notlarım", tint = CsReaderTheme.colors.text)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CsReaderTheme.colors.cardBg)
                            .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(12.dp))
                            .size(44.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar", tint = CsReaderTheme.colors.text)
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Kitap veya yazar ara...", color = CsReaderTheme.colors.textMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CsReaderTheme.colors.textMuted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
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

            // Tabs Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                listOf(
                    "all" to "Tümü",
                    "epub" to "EPUB",
                    "pdf" to "PDF",
                    "favorite" to "Favoriler"
                ).forEach { (tabId, label) ->
                    val selected = activeTab == tabId
                    val isPdf = tabId == "pdf"
                    val activeBg = if (isPdf) Color(0x1AEF4444) else CsReaderTheme.colors.accent
                    val activeText = if (isPdf) Color(0xFFEF4444) else CsReaderTheme.colors.primary
                    val activeBorder = if (isPdf) Color(0xFFEF4444) else CsReaderTheme.colors.primary

                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) activeBg else Color.Transparent)
                            .border(
                                1.5.dp,
                                if (selected) activeBorder else Color.Transparent,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { viewModel.setActiveTab(tabId) }
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) activeText else CsReaderTheme.colors.textMuted
                        )
                    }
                }
            }

            // Books List
            if (filteredBooks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = CsReaderTheme.colors.border,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Kitap Bulunamadı",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = CsReaderTheme.colors.text
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Aramanızla eşleşen kitap bulunmamaktadır." else "Kütüphanenizde henüz kitap bulunmamaktadır.",
                            fontSize = 14.sp,
                            color = CsReaderTheme.colors.textMuted,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            lineHeight = 20.sp
                        )
                        if (searchQuery.isEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { filePickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = CsReaderTheme.colors.primary),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
                            ) {
                                Text("Kitap Ekle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    folderPickerLauncher.launch(null)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CsReaderTheme.colors.border),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = if (scanning) "Taranıyor..." else "Klasör Seç ve Tara",
                                    color = CsReaderTheme.colors.text,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp, top = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredBooks, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            onSelectBook = onSelectBook,
                            onToggleFavorite = { viewModel.toggleFavorite(book) },
                            onDeleteBook = { showDeleteDialogForBook = book }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteDialogForBook?.let { book ->
        AlertDialog(
            onDismissRequest = { showDeleteDialogForBook = null },
            title = { Text("Kitabı Sil", fontWeight = FontWeight.Bold) },
            text = { Text("\"${book.title}\" kitabını ve tüm notlarını silmek istediğinize emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(context, book)
                        showDeleteDialogForBook = null
                    }
                ) {
                    Text("Sil", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogForBook = null }) {
                    Text("İptal")
                }
            }
        )
    }

    if (scanning && scanProgressText.isNotEmpty()) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = CsReaderTheme.colors.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Klasör Taranıyor",
                        fontWeight = FontWeight.Bold,
                        color = CsReaderTheme.colors.text,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = scanProgressText,
                        color = CsReaderTheme.colors.textMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun BookCard(
    book: BookEntity,
    onSelectBook: (BookEntity) -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteBook: () -> Unit
) {
    val progressPercent = (book.progress * 100).toInt().coerceIn(0, 100)
    val isEpub = book.type == "epub"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(16.dp))
            .clickable { onSelectBook(book) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            // Book cover / icon area
            val iconBgColor = if (isEpub) CsReaderTheme.colors.accent else Color(0x1AEF4444)
            val iconColor = if (isEpub) CsReaderTheme.colors.primary else Color(0xFFEF4444)

            Box(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isEpub) Icons.Default.Book else Icons.Default.Description,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Text(
                    text = book.type.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = CsReaderTheme.colors.textMuted,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }

            // Book details
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = book.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CsReaderTheme.colors.text,
                        maxLines = 1,
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
                }

                // Progress Bar
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    LinearProgressIndicator(
                        progress = book.progress.coerceIn(0f, 1f),
                        color = iconColor,
                        trackColor = CsReaderTheme.colors.border,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = "%$progressPercent tamamlandı",
                        fontSize = 11.sp,
                        color = CsReaderTheme.colors.textMuted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favori",
                            tint = if (book.favorite) Color(0xFFF59E0B) else CsReaderTheme.colors.textMuted
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = onDeleteBook,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Sil",
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }
    }
}
