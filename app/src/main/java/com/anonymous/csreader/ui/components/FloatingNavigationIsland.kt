package com.anonymous.csreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonymous.csreader.ui.theme.CsReaderTheme

enum class NavigationTab {
    LIBRARY,
    NOTES,
    SETTINGS
}

/**
 * FloatingNavigationIsland:
 * Ekranın altında havada duran, buzlu cam (frosted glass) efektli,
 * modern ve özgün navigasyon adası.
 */
@Composable
fun FloatingNavigationIsland(
    currentTab: NavigationTab,
    notesCount: Int = 0,
    onTabSelected: (NavigationTab) -> Unit,
    onAddNewBook: () -> Unit,
    onScanFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Island Container
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = CsReaderTheme.colors.cardBg.copy(alpha = 0.95f),
            shadowElevation = 14.dp,
            tonalElevation = 6.dp,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = CsReaderTheme.colors.border.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(32.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Kitaplık Tab
                IslandTabItem(
                    selected = currentTab == NavigationTab.LIBRARY,
                    icon = Icons.Default.Book,
                    label = "Kitaplık",
                    onClick = { onTabSelected(NavigationTab.LIBRARY) }
                )

                // 2. Alıntılar & Notlar Tab
                IslandTabItem(
                    selected = currentTab == NavigationTab.NOTES,
                    icon = Icons.Default.AutoAwesome,
                    label = "Alıntılar",
                    badgeCount = notesCount,
                    onClick = { onTabSelected(NavigationTab.NOTES) }
                )

                // 3. Ayarlar Tab
                IslandTabItem(
                    selected = currentTab == NavigationTab.SETTINGS,
                    icon = Icons.Default.Tune,
                    label = "Ayarlar",
                    onClick = { onTabSelected(NavigationTab.SETTINGS) }
                )

                // İnce Dikey Ayraç
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(CsReaderTheme.colors.border)
                        .padding(horizontal = 2.dp)
                )

                // 4. Hızlı Kitap Ekle Butonu (+ Ekle)
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        CsReaderTheme.colors.primary,
                                        CsReaderTheme.colors.primary.copy(alpha = 0.82f)
                                    )
                                )
                            )
                            .clickable { showAddMenu = true }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Kitap Ekle",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Ekle",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Hızlı Ekleme Açılır Menüsü
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false },
                        modifier = Modifier
                            .background(CsReaderTheme.colors.cardBg)
                            .border(1.dp, CsReaderTheme.colors.border, RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tek Kitap Seç (EPUB/PDF)") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = CsReaderTheme.colors.primary) },
                            onClick = {
                                showAddMenu = false
                                onAddNewBook()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Klasör Tara (Toplu Ekle)") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = CsReaderTheme.colors.primary) },
                            onClick = {
                                showAddMenu = false
                                onScanFolder()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandTabItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val animatedBgColor by animateColorAsState(
        targetValue = if (selected) CsReaderTheme.colors.primary.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(240),
        label = "tabBg"
    )
    val animatedContentColor by animateColorAsState(
        targetValue = if (selected) CsReaderTheme.colors.primary else CsReaderTheme.colors.textMuted,
        animationSpec = tween(240),
        label = "tabContent"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(animatedBgColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = animatedContentColor,
                modifier = Modifier.size(19.dp)
            )

            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(CsReaderTheme.colors.primary)
                )
            }
        }

        AnimatedVisibility(visible = selected) {
            Text(
                text = label,
                color = animatedContentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

