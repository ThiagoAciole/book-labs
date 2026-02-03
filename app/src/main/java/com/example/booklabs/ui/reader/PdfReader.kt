package com.example.booklabs.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.booklabs.util.TextHighlight

@Composable
fun PdfReaderContainer(
    bookId: String,
    chapters: List<String>,
    fontSize: Float,
    initialPage: Int,
    textColor: Color,
    bookPath: String,
    highlights: List<TextHighlight>,
    onPageChange: (Int, Int) -> Unit,
    onTap: () -> Unit,
    onHighlightCreated: (TextHighlight) -> Unit = {},
    onHighlightDeleted: (String) -> Unit = {}
) {
    // PDF extraído como texto também segue a lógica simples de scroll vertical
    SimpleTextReader(
        chapters = chapters,
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
