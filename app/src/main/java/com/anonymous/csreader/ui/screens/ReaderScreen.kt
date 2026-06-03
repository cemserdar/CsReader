package com.anonymous.csreader.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
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

    var showControls by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(CsReaderTheme.colors.bg)) {
        
        Box(modifier = Modifier.fillMaxSize().clickable { showControls = !showControls }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(64.dp), tint = CsReaderTheme.colors.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Readium Native Viewer", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                Text("Kitap yükleniyor: ${book.title}", fontSize = 14.sp, color = CsReaderTheme.colors.textMuted)
                Text("(Metin seçme ve not özellikleri Readium entegrasyonu tamamlandıktan sonra aktif olacaktır)", fontSize = 12.sp, color = CsReaderTheme.colors.textMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(16.dp))
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
