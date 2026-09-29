package com.anonymous.csreader.ui.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.anonymous.csreader.data.HighlightEntity
import com.anonymous.csreader.ui.theme.CsReaderTheme
import org.json.JSONObject

// Parse normalized coordinates from cfiRange JSON (e.g. {"x": 0.45, "y": 0.32})
fun parsePdfPinCoordinates(cfiRange: String?): Pair<Float, Float>? {
    if (cfiRange.isNullOrBlank()) return null
    return try {
        val json = JSONObject(cfiRange)
        val x = json.optDouble("x", -1.0).toFloat()
        val y = json.optDouble("y", -1.0).toFloat()
        if (x in 0f..1f && y in 0f..1f) Pair(x, y) else null
    } catch (e: Exception) {
        null
    }
}

fun formatPdfPinCoordinates(relX: Float, relY: Float): String {
    val json = JSONObject()
    json.put("x", relX.toDouble())
    json.put("y", relY.toDouble())
    return json.toString()
}

@Composable
fun PdfPageAnnotations(
    pageIndex: Int,
    highlights: List<HighlightEntity>,
    onHighlightClick: (HighlightEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val pageHighlights = remember(highlights, pageIndex) {
        highlights.filter { it.page == pageIndex }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight

        pageHighlights.forEach { hl ->
            val coords = parsePdfPinCoordinates(hl.cfiRange)
            val pinColor = try {
                Color(android.graphics.Color.parseColor(hl.color))
            } catch (e: Exception) {
                Color(0xFFFFD54F) // Default warm yellow
            }

            if (coords != null) {
                // Pin with specific coordinates
                val pinX = boxWidth * coords.first
                val pinY = boxHeight * coords.second

                Box(
                    modifier = Modifier
                        .offset(x = pinX - 16.dp, y = pinY - 16.dp)
                        .size(32.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(pinColor)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable { onHighlightClick(hl) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Not Pini",
                        tint = Color(0xFF1F2937),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                // General page note (top-right corner badge)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(pinColor)
                        .border(1.5.dp, Color.White, RoundedCornerShape(12.dp))
                        .clickable { onHighlightClick(hl) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color(0xFF1F2937),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Not",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PdfNoteDetailDialog(
    highlight: HighlightEntity,
    onDismiss: () -> Unit,
    onUpdate: (HighlightEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    var noteText by remember { mutableStateOf(highlight.note ?: "") }
    var selectedColor by remember { mutableStateOf(highlight.color) }

    val colors = listOf(
        "#FFE082" to "Sarı",
        "#A5D6A7" to "Yeşil",
        "#90CAF9" to "Mavi",
        "#F48FB1" to "Pembe",
        "#CE93D8" to "Mor"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CsReaderTheme.colors.cardBg),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sayfa ${(highlight.page ?: 0) + 1} Notu",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CsReaderTheme.colors.text
                    )
                    IconButton(onClick = { onDelete(highlight.id); onDismiss() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color(0xFFEF4444))
                    }
                }

                if (highlight.text.isNotBlank() && highlight.text != "PDF Sayfa Notu") {
                    Text(
                        text = "\"${highlight.text}\"",
                        fontSize = 13.sp,
                        color = CsReaderTheme.colors.textMuted,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                    TextButton(onClick = onDismiss) {
                        Text("Kapat")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updated = highlight.copy(
                                note = noteText.takeIf { it.isNotBlank() },
                                color = selectedColor
                            )
                            onUpdate(updated)
                            onDismiss()
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
