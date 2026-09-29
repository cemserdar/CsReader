package com.anonymous.csreader.ui.pdf

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
import androidx.compose.ui.window.Dialog
import com.anonymous.csreader.data.BookEntity
import com.anonymous.csreader.data.HighlightEntity
import com.anonymous.csreader.ui.screens.ReaderViewModel
import com.anonymous.csreader.ui.theme.CsReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderContainer(
    book: BookEntity,
    viewModel: ReaderViewModel,
    pageTransition: String,
    onBack: () -> Unit,
    onPageTransitionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val highlights by viewModel.highlightsState.collectAsState()
    val themeName by viewModel.themeState.collectAsState()

    var renderer by remember { mutableStateOf<PdfPageRenderer?>(null) }
    var isInitialized by remember { mutableStateOf(false) }
    var initError by remember { mutableStateOf<String?>(null) }

    var currentPage by remember { mutableIntStateOf(book.lastPage ?: 0) }
    var showControls by remember { mutableStateOf(false) }

    // Dialogs
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteTargetLocation by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var activeHighlightForDialog by remember { mutableStateOf<HighlightEntity?>(null) }
    var showTransitionMenu by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

    // Initialize renderer
    LaunchedEffect(book.uri) {
        val r = PdfPageRenderer(context, book.uri)
        renderer = r
        val ok = r.init()
        if (ok) {
            isInitialized = true
            if (currentPage >= r.pageCount) currentPage = 0
        } else {
            initError = "PDF dosyası açılamadı."
        }
    }

    DisposableEffect(book.uri) {
        onDispose {
            renderer?.close()
        }
    }

    // Save progress whenever page changes
    fun handlePageChange(newPage: Int) {
        val r = renderer ?: return
        if (newPage in 0 until r.pageCount) {
            currentPage = newPage
            val prog = (newPage + 1).toFloat() / r.pageCount.toFloat()
            viewModel.updateProgress(progress = prog, lastCfi = null, lastPage = newPage)
        }
    }

    CsReaderTheme(themeName = themeName) {
        Box(modifier = modifier.fillMaxSize().background(CsReaderTheme.colors.bg)) {
            if (!isInitialized) {
                if (initError != null) {
                    Text(
                        text = initError ?: "",
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = CsReaderTheme.colors.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("PDF Hazırlanıyor...", color = CsReaderTheme.colors.text, fontSize = 14.sp)
                    }
                }
            } else {
                val r = renderer!!

                // Render according to selected transition mode
                when (pageTransition) {
                    "curl" -> {
                        PdfCurlView(
                            renderer = r,
                            currentPage = currentPage,
                            highlights = highlights,
                            themeName = themeName,
                            onPageChange = { handlePageChange(it) },
                            onToggleControls = { showControls = !showControls },
                            onAddNoteAt = { pIndex, relX, relY ->
                                currentPage = pIndex
                                noteTargetLocation = Pair(relX, relY)
                                showAddNoteDialog = true
                            },
                            onHighlightClick = { hl -> activeHighlightForDialog = hl }
                        )
                    }
                    "scroll" -> {
                        PdfScrollView(
                            renderer = r,
                            currentPage = currentPage,
                            highlights = highlights,
                            themeName = themeName,
                            onPageChange = { handlePageChange(it) },
                            onToggleControls = { showControls = !showControls },
                            onAddNoteAt = { pIndex, relX, relY ->
                                currentPage = pIndex
                                noteTargetLocation = Pair(relX, relY)
                                showAddNoteDialog = true
                            },
                            onHighlightClick = { hl -> activeHighlightForDialog = hl }
                        )
                    }
                    "fade" -> {
                        PdfFadeView(
                            renderer = r,
                            currentPage = currentPage,
                            highlights = highlights,
                            themeName = themeName,
                            onPageChange = { handlePageChange(it) },
                            onToggleControls = { showControls = !showControls },
                            onAddNoteAt = { pIndex, relX, relY ->
                                currentPage = pIndex
                                noteTargetLocation = Pair(relX, relY)
                                showAddNoteDialog = true
                            },
                            onHighlightClick = { hl -> activeHighlightForDialog = hl }
                        )
                    }
                    else -> { // "slide" is default
                        PdfSlideView(
                            renderer = r,
                            currentPage = currentPage,
                            highlights = highlights,
                            themeName = themeName,
                            onPageChange = { handlePageChange(it) },
                            onToggleControls = { showControls = !showControls },
                            onAddNoteAt = { pIndex, relX, relY ->
                                currentPage = pIndex
                                noteTargetLocation = Pair(relX, relY)
                                showAddNoteDialog = true
                            },
                            onHighlightClick = { hl -> activeHighlightForDialog = hl }
                        )
                    }
                }

                // Top Header Bar
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
                            .padding(horizontal = 12.dp, vertical = 6.dp),
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

                        // Theme Switcher Icon (Amoled, Dark, Sepia, Light)
                        Box {
                            IconButton(onClick = { showThemeMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = "Renk Teması",
                                    tint = CsReaderTheme.colors.primary
                                )
                            }

                            DropdownMenu(
                                expanded = showThemeMenu,
                                onDismissRequest = { showThemeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Aydınlık (Varsayılan)") },
                                    leadingIcon = {
                                        if (themeName == "light") Icon(Icons.Default.Check, null)
                                    },
                                    onClick = {
                                        viewModel.setTheme("light")
                                        showThemeMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Karanlık (Soft Dark)") },
                                    leadingIcon = {
                                        if (themeName == "dark") Icon(Icons.Default.Check, null)
                                    },
                                    onClick = {
                                        viewModel.setTheme("dark")
                                        showThemeMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("AMOLED Siyah (Sayfalar Siyah)") },
                                    leadingIcon = {
                                        if (themeName == "amoled") Icon(Icons.Default.Check, null)
                                    },
                                    onClick = {
                                        viewModel.setTheme("amoled")
                                        showThemeMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sıcak Sepya") },
                                    leadingIcon = {
                                        if (themeName == "sepia") Icon(Icons.Default.Check, null)
                                    },
                                    onClick = {
                                        viewModel.setTheme("sepia")
                                        showThemeMenu = false
                                    }
                                )
                            }
                        }

                        // Page Transition Switcher Icon
                        Box {
                            IconButton(onClick = { showTransitionMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = "Geçiş Efekti",
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
                                        onPageTransitionChange("curl")
                                        showTransitionMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Yatay Sayfalama (Slide)") },
                                    leadingIcon = {
                                        if (pageTransition == "slide") Icon(Icons.Default.Check, null)
                                    },
                                    onClick = {
                                        onPageTransitionChange("slide")
                                        showTransitionMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Dikey Kesintisiz (Scroll)") },
                                    leadingIcon = {
                                        if (pageTransition == "scroll") Icon(Icons.Default.Check, null)
                                    },
                                    onClick = {
                                        onPageTransitionChange("scroll")
                                        showTransitionMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Yumuşak Geçiş (Fade)") },
                                    leadingIcon = {
                                        if (pageTransition == "fade") Icon(Icons.Default.Check, null)
                                    },
                                    onClick = {
                                        onPageTransitionChange("fade")
                                        showTransitionMenu = false
                                    }
                                )
                            }
                        }

                        // Add Note Button
                        IconButton(onClick = {
                            noteTargetLocation = null
                            showAddNoteDialog = true
                        }) {
                            Icon(Icons.Default.AddComment, contentDescription = "Not Ekle", tint = CsReaderTheme.colors.text)
                        }
                    }
                }

                // Bottom Navigation Bar
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
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Page progress text
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sayfa ${currentPage + 1} / ${r.pageCount}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CsReaderTheme.colors.text
                            )
                            val percent = if (r.pageCount > 0) ((currentPage + 1) * 100 / r.pageCount) else 0
                            Text(
                                text = "%$percent",
                                fontSize = 13.sp,
                                color = CsReaderTheme.colors.textMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Slider and jump buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { handlePageChange(currentPage - 1) },
                                enabled = currentPage > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Önceki Sayfa")
                            }

                            if (r.pageCount > 1) {
                                Slider(
                                    value = currentPage.toFloat(),
                                    onValueChange = { handlePageChange(it.toInt()) },
                                    valueRange = 0f..(r.pageCount - 1).toFloat(),
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = CsReaderTheme.colors.primary,
                                        activeTrackColor = CsReaderTheme.colors.primary
                                    )
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }

                            IconButton(
                                onClick = { handlePageChange(currentPage + 1) },
                                enabled = currentPage + 1 < r.pageCount
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Sonraki Sayfa")
                            }
                        }
                    }
                }
            }
        }

        // Add Note Dialog
        if (showAddNoteDialog) {
            var noteText by remember { mutableStateOf("") }
            var selectedColor by remember { mutableStateOf("#FFE082") }

            val colors = listOf(
                "#FFE082" to "Sarı",
                "#A5D6A7" to "Yeşil",
                "#90CAF9" to "Mavi",
                "#F48FB1" to "Pembe",
                "#CE93D8" to "Mor"
            )

            Dialog(onDismissRequest = { showAddNoteDialog = false }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Sayfa ${currentPage + 1}'e Not Ekle",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = CsReaderTheme.colors.text
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            label = { Text("Notunuz") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
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
                            TextButton(onClick = { showAddNoteDialog = false }) {
                                Text("İptal")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (noteText.isNotBlank()) {
                                        val locJson = noteTargetLocation?.let {
                                            formatPdfPinCoordinates(it.first, it.second)
                                        }
                                        viewModel.addHighlight(
                                            text = "PDF Sayfa ${currentPage + 1} Notu",
                                            cfiRange = locJson,
                                            page = currentPage,
                                            color = selectedColor,
                                            note = noteText
                                        ) {
                                            showAddNoteDialog = false
                                            Toast.makeText(context, "Not kaydedildi!", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Lütfen bir not yazın.", Toast.LENGTH_SHORT).show()
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

        // Detail / Edit existing note dialog
        activeHighlightForDialog?.let { hl ->
            PdfNoteDetailDialog(
                highlight = hl,
                onDismiss = { activeHighlightForDialog = null },
                onUpdate = { updatedHl ->
                    viewModel.updateHighlight(updatedHl)
                    activeHighlightForDialog = null
                    Toast.makeText(context, "Not güncellendi!", Toast.LENGTH_SHORT).show()
                },
                onDelete = { id ->
                    viewModel.deleteHighlight(id)
                    activeHighlightForDialog = null
                    Toast.makeText(context, "Not silindi.", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
