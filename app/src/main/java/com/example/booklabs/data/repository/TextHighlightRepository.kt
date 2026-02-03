package com.example.booklabs.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Modelo de dados para uma marcação de texto
 */
data class TextHighlight(
    val id: String = java.util.UUID.randomUUID().toString(),
    val bookPath: String,
    val chapterIndex: Int,
    val selectedText: String,
    val startOffset: Int,
    val endOffset: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val color: String = "#FFB74D" // Laranja claro padrão
)

/**
 * Repositório para gerenciar marcações de texto nos livros
 */
object TextHighlightRepository {
    
    private const val PREFS_NAME = "text_highlights"
    private const val KEY_HIGHLIGHTS = "highlights"
    
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Salva uma nova marcação de texto
     */
    fun saveHighlight(highlight: TextHighlight) {
        val highlights = getAllHighlights().toMutableList()
        highlights.add(highlight)
        saveAllHighlights(highlights)
    }
    
    /**
     * Remove uma marcação específica
     */
    fun removeHighlight(highlightId: String) {
        val highlights = getAllHighlights().toMutableList()
        highlights.removeAll { it.id == highlightId }
        saveAllHighlights(highlights)
    }
    
    /**
     * Obtém todas as marcações de um livro específico
     */
    fun getHighlightsForBook(bookPath: String): List<TextHighlight> {
        return getAllHighlights().filter { it.bookPath == bookPath }
    }
    
    /**
     * Obtém marcações de um capítulo específico
     */
    fun getHighlightsForChapter(bookPath: String, chapterIndex: Int): List<TextHighlight> {
        return getAllHighlights().filter { 
            it.bookPath == bookPath && it.chapterIndex == chapterIndex 
        }
    }
    
    /**
     * Obtém todas as marcações
     */
    private fun getAllHighlights(): List<TextHighlight> {
        val json = prefs.getString(KEY_HIGHLIGHTS, null) ?: return emptyList()
        val type = object : TypeToken<List<TextHighlight>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Salva todas as marcações
     */
    private fun saveAllHighlights(highlights: List<TextHighlight>) {
        val json = gson.toJson(highlights)
        prefs.edit().putString(KEY_HIGHLIGHTS, json).apply()
    }
    
    /**
     * Limpa todas as marcações de um livro
     */
    fun clearBookHighlights(bookPath: String) {
        val highlights = getAllHighlights().toMutableList()
        highlights.removeAll { it.bookPath == bookPath }
        saveAllHighlights(highlights)
    }
    
    /**
     * Verifica se existe uma marcação em uma posição específica
     */
    fun getHighlightAtPosition(
        bookPath: String, 
        chapterIndex: Int, 
        offset: Int
    ): TextHighlight? {
        return getHighlightsForChapter(bookPath, chapterIndex).find { 
            offset >= it.startOffset && offset <= it.endOffset 
        }
    }
}
