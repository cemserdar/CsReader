package com.anonymous.csreader.ui.screens

import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(
    private val settingsManager: SettingsManager,
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao,
    private val db: AppDatabase
) : ViewModel() {
    val themeState = MutableStateFlow(settingsManager.theme)
    val fontSizeState = MutableStateFlow(settingsManager.fontSize)
    val pageTransitionState = MutableStateFlow(settingsManager.pageTransition)

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

    fun clearAllDatabase(context: Context, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filesDir = context.filesDir
                val files = filesDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            db.clearAllTables()

            settingsManager.theme = "light"
            settingsManager.fontSize = 16
            settingsManager.pageTransition = "slide"

            themeState.value = "light"
            fontSizeState.value = 16
            pageTransitionState.value = "slide"

            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    bookDao: BookDao,
    highlightDao: HighlightDao,
    db: AppDatabase,
    onBack: () -> Unit,
    onDatabaseCleared: () -> Unit,
    onThemeChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsManager, bookDao, highlightDao, db) as T
        }
    })

    val themeName by viewModel.themeState.collectAsState()
    val fontSize by viewModel.fontSizeState.collectAsState()
    val pageTransition by viewModel.pageTransitionState.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }

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
                    text = "Ayarlar",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = CsReaderTheme.colors.text
                )

                Spacer(modifier = Modifier.width(48.dp)) // Equalizer spacer
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Theme settings Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = CsReaderTheme.colors.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Görünüm Teması", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Uygulamanın genel renk şemasını değiştirin.",
                            fontSize = 12.sp,
                            color = CsReaderTheme.colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Themes grid
                        listOf(
                            "light" to "Aydınlık",
                            "dark" to "Karanlık",
                            "sepia" to "Sepya",
                            "forest" to "Yeşil"
                        ).chunked(2).forEach { chunk ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                chunk.forEach { (tName, tLabel) ->
                                    val active = themeName == tName
                                    val bgCol = when (tName) {
                                        "light" -> Color.White
                                        "dark" -> Color(0xFF0F172A)
                                        "sepia" -> Color(0xFFFAF6EB)
                                        else -> Color(0xFFF3F7F2)
                                    }
                                    val indicatorCol = when (tName) {
                                        "light" -> Color(0xFFF3F4F6)
                                        "dark" -> Color(0xFF0F172A)
                                        "sepia" -> Color(0xFFF4ECD8)
                                        else -> Color(0xFFE8EFE9)
                                    }

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(vertical = 6.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(bgCol)
                                            .border(
                                                2.dp,
                                                if (active) CsReaderTheme.colors.primary else CsReaderTheme.colors.border,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                viewModel.setTheme(tName)
                                                onThemeChanged(tName)
                                            }
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(indicatorCol)
                                                .border(1.dp, Color("rgba(0,0,0,0.1)".toIntOrNull() ?: 0x1A000000), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = tLabel,
                                            fontSize = 13.sp,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                            color = if (tName == "dark") Color.White else Color(0xFF1F2937)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Font Adjuster Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TextFields, contentDescription = null, tint = CsReaderTheme.colors.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Varsayılan Yazı Boyutu", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "EPUB okuyucu için varsayılan metin boyutu.",
                            fontSize = 12.sp,
                            color = CsReaderTheme.colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val newSize = (fontSize - 2).coerceAtLeast(12)
                                    viewModel.setFontSize(newSize)
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
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )

                            IconButton(
                                onClick = {
                                    val newSize = (fontSize + 2).coerceAtMost(28)
                                    viewModel.setFontSize(newSize)
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
                }

                // Page Transition Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = CsReaderTheme.colors.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sayfa Geçişi", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Okuma sırasında tercih ettiğiniz sayfa geçiş efektini seçin.",
                            fontSize = 12.sp,
                            color = CsReaderTheme.colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "scroll" to "Aşağı Kaydır",
                                "none" to "Yok",
                                "slide" to "Sağa Kaydır",
                                "fade" to "Solma",
                                "page" to "Sayfa"
                            ).forEach { (pType, pLabel) ->
                                val active = pageTransition == pType
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (active) CsReaderTheme.colors.primary else CsReaderTheme.colors.bg)
                                        .border(
                                            1.dp,
                                            if (active) CsReaderTheme.colors.primary else CsReaderTheme.colors.border,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable { viewModel.setPageTransition(pType) }
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (active) Color.White else CsReaderTheme.colors.text
                                    )
                                }
                            }
                        }
                    }
                }

                // Danger zone Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tehlikeli Bölge", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tüm uygulama verilerini ve e-kitapları cihazınızdan temizleyin.",
                            fontSize = 12.sp,
                            color = CsReaderTheme.colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { showResetDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kütüphaneyi Sıfırla", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // About application Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(16.dp))
                        .padding(bottom = 40.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = CsReaderTheme.colors.textMuted)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uygulama Hakkında", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CsReaderTheme.colors.text)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "CsReader v1.0.0\nKotlin + Jetpack Compose\n\nFarklı renklerde vurgulama, not alma, aydınlık/karanlık/sepya/yeşil temaları ve sayfa geçişleri desteğine sahip e-kitap okuyucu uygulaması.",
                            fontSize = 13.sp,
                            color = CsReaderTheme.colors.text,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Uygulama Verilerini Sıfırla", fontWeight = FontWeight.Bold) },
            text = { Text("Tüm e-kitaplar, PDF dosyaları, aldığınız notlar ve vurgulamalar kalıcı olarak silinecektir. Bu işlem geri alınamaz. Sıfırlamak istediğinize emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllDatabase(context) {
                            Toast.makeText(context, "Uygulama sıfırlandı", Toast.LENGTH_SHORT).show()
                            onDatabaseCleared()
                            onBack()
                        }
                        showResetDialog = false
                    }
                ) {
                    Text("Sıfırla", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}
