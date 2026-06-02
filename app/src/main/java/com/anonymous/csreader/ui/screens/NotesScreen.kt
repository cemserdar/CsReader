package com.anonymous.csreader.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anonymous.csreader.data.*
import com.anonymous.csreader.ui.theme.CsReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(
    private val highlightDao: HighlightDao,
    private val bookDao: BookDao
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _activeColorFilter = MutableStateFlow("all")
    val activeColorFilter: StateFlow<String> = _activeColorFilter

    val highlightsState: StateFlow<List<HighlightEntity>> = highlightDao.getAllHighlights()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val booksState: StateFlow<List<BookEntity>> = bookDao.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredNotes: StateFlow<List<HighlightEntity>> = combine(
        highlightsState, booksState, _searchQuery, _activeColorFilter
    ) { highlights, books, query, filter ->
        highlights.filter { hl ->
            val book = books.find { it.id == hl.bookId }
            val bookTitle = book?.title ?: ""
            val bookAuthor = book?.author ?: ""

            val matchesSearch = hl.text.contains(query, ignoreCase = true) ||
                    (hl.note ?: "").contains(query, ignoreCase = true) ||
                    bookTitle.contains(query, ignoreCase = true) ||
                    bookAuthor.contains(query, ignoreCase = true)

            val matchesColor = filter == "all" || hl.color == filter

            matchesSearch && matchesColor
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setActiveColorFilter(filter: String) {
        _activeColorFilter.value = filter
    }

    fun deleteHighlight(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            highlightDao.deleteHighlightById(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    highlightDao: HighlightDao,
    bookDao: BookDao,
    onBack: () -> Unit,
    onNavigateToBookAtLocation: (BookEntity, String?, Int?) -> Unit
) {
    val context = LocalContext.current
    val viewModel: NotesViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return NotesViewModel(highlightDao, bookDao) as T
        }
    })

    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeColorFilter by viewModel.activeColorFilter.collectAsState()
    val books by viewModel.booksState.collectAsState()
    val filteredNotes by viewModel.filteredNotes.collectAsState()

    var showDeleteDialogForHl by remember { mutableStateOf<HighlightEntity?>(null) }

    Scaffold(
        containerColor = CsReaderTheme.colors.bg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Geri",
                        tint = CsReaderTheme.colors.text,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = "Tüm Notlarım",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CsReaderTheme.colors.text
                )

                Spacer(modifier = Modifier.width(48.dp)) // Equalizer spacer
            }

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Notlarında ara...", color = CsReaderTheme.colors.textMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CsReaderTheme.colors.textMuted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CsReaderTheme.colors.cardBg,
                    unfocusedContainerColor = CsReaderTheme.colors.cardBg,
                    focusedTextColor = CsReaderTheme.colors.text,
                    unfocusedTextColor = CsReaderTheme.colors.text,
                    focusedBorderColor = CsReaderTheme.colors.primary,
                    unfocusedBorderColor = CsReaderTheme.colors.border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            // Color Filters Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val active = activeColorFilter == "all"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) CsReaderTheme.colors.accent else Color.Transparent)
                            .border(
                                1.dp,
                                if (active) CsReaderTheme.colors.primary else Color(0xFFE5E7EB),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { viewModel.setActiveColorFilter("all") }
                            .padding(vertical = 6.dp, horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Hepsi",
                            fontSize = 12.sp,
                            color = if (active) CsReaderTheme.colors.primary else CsReaderTheme.colors.text,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                items(listOf("yellow", "green", "pink", "blue", "underline")) { colorName ->
                    val active = activeColorFilter == colorName
                    val bgVal = when (colorName) {
                        "yellow" -> Color(0xFFFEF08A)
                        "green" -> Color(0xFFBBF7D0)
                        "pink" -> Color(0xFFFBCFE8)
                        "blue" -> Color(0xFFBFDBFE)
                        else -> Color.Transparent
                    }
                    val label = when (colorName) {
                        "yellow" -> "Sarı"
                        "green" -> "Yeşil"
                        "pink" -> "Pembe"
                        "blue" -> "Mavi"
                        else -> "Altı Çizili"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) CsReaderTheme.colors.accent else Color.Transparent)
                            .border(
                                1.dp,
                                if (active) CsReaderTheme.colors.primary else Color(0xFFE5E7EB),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { viewModel.setActiveColorFilter(colorName) }
                            .padding(vertical = 6.dp, horizontal = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(bgVal)
                                    .border(
                                        width = if (colorName == "underline") 1.dp else 0.dp,
                                        color = Color.Red,
                                        shape = CircleShape
                                    )
                            ) {
                                if (colorName == "underline") {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .width(8.dp)
                                            .height(1.dp)
                                            .background(Color.Red)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = if (active) CsReaderTheme.colors.primary else CsReaderTheme.colors.text,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Notes List
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = CsReaderTheme.colors.border,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Not Bulunmadı",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = CsReaderTheme.colors.text
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Aramanıza uyan not veya vurgulama bulunamadı." else "Henüz not almadınız. Okuyucu ekranından bir metni seçerek başlayabilirsiniz.",
                            fontSize = 13.sp,
                            color = CsReaderTheme.colors.textMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredNotes, key = { it.id }) { item ->
                        val associatedBook = books.find { it.id == item.bookId }
                        NoteCard(
                            highlight = item,
                            book = associatedBook,
                            onGoToBook = {
                                if (associatedBook != null) {
                                    onNavigateToBookAtLocation(associatedBook, item.cfiRange, item.page)
                                } else {
                                    Toast.makeText(context, "Bu nota ait kitap bulunamadı.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onShare = {
                                val bookTitle = associatedBook?.title ?: "Bilinmeyen Kitap"
                                var message = "\"${item.text}\"\n"
                                if (!item.note.isNullOrEmpty()) {
                                    message += "Notum: ${item.note}\n"
                                }
                                message += "\n- \"$bookTitle\" kitabından CsReader ile paylaşıldı."

                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, message)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Paylaş"))
                            },
                            onDelete = { showDeleteDialogForHl = item }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteDialogForHl?.let { hl ->
        AlertDialog(
            onDismissRequest = { showDeleteDialogForHl = null },
            title = { Text("Notu Sil", fontWeight = FontWeight.Bold) },
            text = { Text("Bu vurgulamayı ve notu kalıcı olarak silmek istediğinize emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteHighlight(hl.id)
                        showDeleteDialogForHl = null
                    }
                ) {
                    Text("Sil", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogForHl = null }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun NoteCard(
    highlight: HighlightEntity,
    book: BookEntity?,
    onGoToBook: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val bookTitle = book?.title ?: "Bilinmeyen Kitap"
    val bookAuthor = book?.author ?: ""
    val hlColor = when (highlight.color) {
        "yellow" -> Color(0xFFFEF08A)
        "green" -> Color(0xFFBBF7D0)
        "pink" -> Color(0xFFFBCFE8)
        "blue" -> Color(0xFFBFDBFE)
        else -> Color.Transparent
    }
    val dateStr = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(highlight.date))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Book Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGoToBook() }
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    tint = CsReaderTheme.colors.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$bookTitle ${if (bookAuthor.isNotEmpty()) "• $bookAuthor" else ""}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CsReaderTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Highlight Metadata Row
            Row(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(hlColor)
                        .border(
                            width = if (highlight.color == "underline") 1.dp else 0.dp,
                            color = Color.Red,
                            shape = RoundedCornerShape(4.dp)
                        )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "$dateStr ${if (highlight.page != null) "(Sayfa ${highlight.page})" else ""}",
                    fontSize = 11.sp,
                    color = CsReaderTheme.colors.textMuted
                )
            }

            // Highlight Text
            Text(
                text = "\"${highlight.text}\"",
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                color = CsReaderTheme.colors.text,
                lineHeight = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGoToBook() }
            )

            // Note Comment
            if (!highlight.note.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CsReaderTheme.colors.bg)
                        .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = CsReaderTheme.colors.primary,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = highlight.note,
                        fontSize = 13.sp,
                        color = CsReaderTheme.colors.text,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = CsReaderTheme.colors.border)

            // Actions Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kitapta Git",
                    color = CsReaderTheme.colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onGoToBook() }
                        .padding(vertical = 4.dp)
                )

                Row {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Paylaş",
                            tint = CsReaderTheme.colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Sil",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
