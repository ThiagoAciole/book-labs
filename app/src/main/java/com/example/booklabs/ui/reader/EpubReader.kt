package com.example.booklabs.ui.reader

import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.booklabs.data.repository.TextHighlight
import com.example.booklabs.data.repository.TextHighlightRepository

@Composable
@Composable
fun EpubReaderContainer(
    bookId: String,
    chapters: List<String>,
    cachedSpannedChapters: Map<Int, android.text.Spanned>, // New param
    fontSize: Float,
    initialPage: Int,
    textColor: Color,
    bookPath: String,
    highlights: List<TextHighlight>, // Novo parâmetro reativo
    onPageChange: (Int, Int) -> Unit,
    onTap: () -> Unit,
    onHighlightCreated: (TextHighlight) -> Unit = {},
    onHighlightDeleted: (String) -> Unit = {} // Novo callback para deleção
) {
    // Implementação simplificada: um scroll vertical infinito usando LazyColumn
    SimpleTextReader(
        chapters = chapters,
        cachedSpannedChapters = cachedSpannedChapters,
        fontSize = fontSize,
        textColor = textColor,
        initialPage = initialPage,
        bookPath = bookPath,
        highlights = highlights,
        onPageChange = onPageChange,
        onTap = onTap,
        onHighlightCreated = onHighlightCreated,
        onHighlightDeleted = onHighlightDeleted
    )
}

@Composable
fun SimpleTextReader(
    chapters: List<String>,
    cachedSpannedChapters: Map<Int, android.text.Spanned>,
    fontSize: Float,
    textColor: Color,
    initialPage: Int,
    bookPath: String,
    highlights: List<TextHighlight>,
    onPageChange: (Int, Int) -> Unit,
    onTap: () -> Unit,
    onHighlightCreated: (TextHighlight) -> Unit = {},
    onHighlightDeleted: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    // Jump to chapter when requested from TOC
    LaunchedEffect(initialPage) {
        if (initialPage < chapters.size && listState.firstVisibleItemIndex != initialPage) {
            listState.scrollToItem(initialPage)
        }
    }
    
    // Update progress
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onPageChange(listState.firstVisibleItemIndex, chapters.size)
    }

    var horizontalSwipeOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onTap() }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { horizontalSwipeOffset = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(horizontalSwipeOffset) > 100f) {
                            onTap()
                        }
                    },
                    onHorizontalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                        horizontalSwipeOffset += dragAmount
                    }
                )
            }
            .padding(horizontal = 20.dp)
    ) {
        items(
            count = chapters.size,
            key = { index -> index }, // Use index as key if chapters are static, or use a stable ID if available
            contentType = { "chapter" }
        ) { index ->
            val chapter = chapters[index]
            
            // Filtra os destaques para este capítulo a partir da lista reativa
            // Ensure this doesn't recalculate unnecessarily
            val chapterHighlights = remember(highlights, index) {
                highlights.filter { it.chapterIndex == index }
            }
            
            // Optimization: Use cached spanned text if available, otherwise parse on main thread (fallback)
            // Caching this in VM prevents Jank
            val baseSpannable = remember(chapter, cachedSpannedChapters[index]) {
                val spanned = cachedSpannedChapters[index] ?: Html.fromHtml(chapter, Html.FROM_HTML_MODE_COMPACT)
                SpannableString(spanned)
            }
            
            // Optimization: Apply highlights only when needed
            val displayedText = remember(baseSpannable, chapterHighlights) {
                val spannable = SpannableString(baseSpannable)
                chapterHighlights.forEach { highlight ->
                    try {
                        val color = android.graphics.Color.parseColor(highlight.color)
                        // Verify bounds to prevent crashes
                        val start = highlight.startOffset.coerceIn(0, spannable.length)
                        val end = highlight.endOffset.coerceIn(0, spannable.length)
                        if (start < end) {
                            spannable.setSpan(
                                BackgroundColorSpan(color),
                                start,
                                end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    } catch (e: Exception) {}
                }
                spannable
            }
            
            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        setTextColor(textColor.toArgb())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
                        setLineSpacing(0f, 1.2f)
                        setTextIsSelectable(true)
                        
                        customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
                            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                                menu?.clear()
                                
                                val start = selectionStart
                                val end = selectionEnd
                                
                                // Verifica se a seleção já está marcada para mostrar "Desmarcar" em vez de "Marcar"
                                val existingHighlight = chapterHighlights.find { 
                                    (start >= it.startOffset && start < it.endOffset) || 
                                    (end > it.startOffset && end <= it.endOffset)
                                }
                                
                                if (existingHighlight != null) {
                                    menu?.add(0, 2, 0, "Desmarcar")
                                } else {
                                    menu?.add(0, 1, 0, "Marcar")
                                }
                                return true
                            }
                            
                            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
                            
                            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean {
                                return when (item?.itemId) {
                                    1 -> { // Marcar
                                        val start = selectionStart
                                        val end = selectionEnd
                                        // Avoid empty selection
                                        if (start >= end) return true
                                        
                                        val selectedTxt = text.subSequence(start, end).toString()
                                        val highlight = TextHighlight(
                                            bookPath = bookPath,
                                            chapterIndex = index,
                                            selectedText = selectedTxt,
                                            startOffset = start,
                                            endOffset = end
                                        )
                                        // TextHighlightRepository.saveHighlight(highlight) -- Moved to ViewModel via callback
                                        onHighlightCreated(highlight)
                                        mode?.finish()
                                        true
                                    }
                                    2 -> { // Desmarcar
                                        val start = selectionStart
                                        val existingHighlight = chapterHighlights.find { 
                                            start >= it.startOffset && start < it.endOffset
                                        }
                                        existingHighlight?.let {
                                            // TextHighlightRepository.removeHighlight(it.id) -- Moved to ViewModel via callback
                                            onHighlightDeleted(it.id)
                                        }
                                        mode?.finish()
                                        true
                                    }
                                    else -> false
                                }
                            }
                            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
                        }
                    }
                },
                update = { textView ->
                    // Only update text if it changed significantly
                    if (textView.text != displayedText) {
                        textView.text = displayedText
                    }
                    textView.setTextColor(textColor.toArgb())
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
        
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
