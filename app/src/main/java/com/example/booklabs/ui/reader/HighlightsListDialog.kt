package com.example.booklabs.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.booklabs.ui.theme.Purple
import com.example.booklabs.data.repository.TextHighlight
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog para exibir lista de marcações de texto
 */
@Composable
fun HighlightsListDialog(
    highlights: List<TextHighlight>,
    textColor: Color,
    surfaceColor: Color,
    onDismiss: () -> Unit,
    onHighlightClick: (TextHighlight) -> Unit,
    onHighlightDelete: (TextHighlight) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = surfaceColor,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Marcações de Texto",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = textColor
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Count
                Text(
                    text = "${highlights.size} marcação${if (highlights.size != 1) "ões" else ""} encontrada${if (highlights.size != 1) "s" else ""}",
                    fontSize = 14.sp,
                    color = textColor.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // List
                if (highlights.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📝",
                                fontSize = 48.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nenhuma marcação ainda",
                                fontSize = 16.sp,
                                color = textColor.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Selecione um texto e toque em 'Marcar'",
                                fontSize = 14.sp,
                                color = textColor.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(highlights.sortedByDescending { it.timestamp }) { highlight ->
                            HighlightItem(
                                highlight = highlight,
                                textColor = textColor,
                                onClick = { onHighlightClick(highlight) },
                                onDelete = { onHighlightDelete(highlight) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Item individual de marcação
 */
@Composable
fun HighlightItem(
    highlight: TextHighlight,
    textColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(android.graphics.Color.parseColor(highlight.color)).copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header with chapter and delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Capítulo ${highlight.chapterIndex + 1}",
                    fontSize = 12.sp,
                    color = Purple,
                    fontWeight = FontWeight.SemiBold
                )
                
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Deletar",
                        tint = textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Highlighted text
            Text(
                text = highlight.selectedText,
                fontSize = 14.sp,
                color = textColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Timestamp
            Text(
                text = formatTimestamp(highlight.timestamp),
                fontSize = 11.sp,
                color = textColor.copy(alpha = 0.5f)
            )
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remover Marcação") },
            text = { Text("Deseja realmente remover esta marcação?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Remover", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Formata timestamp para exibição
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Agora"
        diff < 3600_000 -> "${diff / 60_000} min atrás"
        diff < 86400_000 -> "${diff / 3600_000}h atrás"
        diff < 604800_000 -> "${diff / 86400_000}d atrás"
        else -> {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
