package com.example.booklabs.ui.reader

import android.app.Application
import android.content.Context
import android.net.Uri
import android.text.Html
import android.text.Spanned
import android.text.SpannableString
import androidx.core.text.HtmlCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.booklabs.data.ContentLoader
import com.example.booklabs.data.repository.FavoritesRepository
import com.example.booklabs.data.repository.ReaderThemeRepository
import com.example.booklabs.data.repository.ReadingProgressRepository
import com.example.booklabs.data.repository.TextHighlight
import com.example.booklabs.data.repository.TextHighlightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReaderUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val bookChapters: List<String> = emptyList(), // Raw HTML
    val cachedSpannedChapters: Map<Int, Spanned> = emptyMap(), // Processed Spanned for display
    val imagePages: List<String> = emptyList(),
    val textContent: String? = null,
    val toc: List<ContentLoader.ChapterInfo> = emptyList(),
    
    // User Preferences
    val fontSize: Float = 18f,
    val isDarkTheme: Boolean = true,
    
    // Reader State
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val isFavorite: Boolean = false,
    
    // Highlights
    val highlights: List<TextHighlight> = emptyList(),
    
    // UI Controls
    val showControls: Boolean = true
)

class ReaderViewModel(
    application: Application,
    private val comicUri: String
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()
    
    // Context is needed for SharedPreferences (init repositories)
    private val context: Context get() = getApplication<Application>().applicationContext
    
    private var cleanPath: String = ""
    private var loadingJob: Job? = null

    init {
        initializeReader()
    }
    
    private fun initializeReader() {
        cleanPath = if (comicUri.startsWith("file://")) File(java.net.URI(comicUri)).absolutePath else comicUri
        
        // Initialize Repositories
        FavoritesRepository.init(context)
        ReaderThemeRepository.init(context)
        ReadingProgressRepository.init(context)
        TextHighlightRepository.init(context)
        
        // Load initial state
        val isFav = FavoritesRepository.isFavorite(cleanPath)
        val progress = ReadingProgressRepository.getProgress(cleanPath)
        val themeDark = ReaderThemeRepository.isDarkTheme()
        val savedHighlights = TextHighlightRepository.getHighlightsForBook(cleanPath)
        
        _uiState.update { 
            it.copy(
                isFavorite = isFav,
                currentPage = progress,
                isDarkTheme = themeDark,
                highlights = savedHighlights
            )
        }
        
        loadContent()
    }
    
    private fun loadContent() {
        if (loadingJob?.isActive == true) return
        
        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = if (comicUri.startsWith("file://")) File(java.net.URI(comicUri)) else File(comicUri)
                val safeFile = if (file.exists()) file else File(comicUri)
                
                when (val result = ContentLoader.loadContent(context, safeFile)) {
                    is ContentLoader.LoaderResult.Book -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                bookChapters = result.chapters,
                                toc = result.toc,
                                totalPages = result.chapters.size
                            ) 
                        }
                        // Pre-calculate spans for current and nearby chapters to avoid main thread jank
                        // Start with current, previous, next
                        val initialChaptersToLoad = listOfNotNull(
                            _uiState.value.currentPage - 1,
                            _uiState.value.currentPage,
                            _uiState.value.currentPage + 1
                        ).filter { it in result.chapters.indices }
                        
                        prepareSpansForChapters(result.chapters, initialChaptersToLoad)
                    }
                    is ContentLoader.LoaderResult.Images -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                imagePages = result.pagePaths,
                                totalPages = result.pagePaths.size
                            ) 
                        }
                    }
                    is ContentLoader.LoaderResult.Error -> {
                        _uiState.update { it.copy(isLoading = false, error = result.message) }
                    }
                    else -> {
                         _uiState.update { it.copy(isLoading = false, error = "Formato não suportado") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }
    
    /**
     * Process HTML to Spanned on background thread.
     * This is crucial for scrolling performance.
     */
    fun prepareSpansForChapters(chapters: List<String>, indices: List<Int>) {
        viewModelScope.launch(Dispatchers.Default) {
            val newMap = _uiState.value.cachedSpannedChapters.toMutableMap()
            var changed = false
            
            indices.forEach { index ->
                if (!newMap.containsKey(index) && index in chapters.indices) {
                    val rawHtml = chapters[index]
                    // HtmlCompat.fromHtml is slow, doing it here saves the UI thread
                    val spanned = HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
                    newMap[index] = SpannableString(spanned)
                    changed = true
                }
            }
            
            if (changed) {
                _uiState.update { it.copy(cachedSpannedChapters = newMap) }
            }
        }
    }
    
    // Actions
    
    fun onPageChanged(page: Int) {
        if (page != _uiState.value.currentPage) {
             _uiState.update { it.copy(currentPage = page) }
             ReadingProgressRepository.saveProgress(cleanPath, page)
             
             // Pre-load nearby chapters if needed
             if (_uiState.value.bookChapters.isNotEmpty()) {
                 val nearby = listOfNotNull(page - 1, page + 1, page + 2).filter { it in _uiState.value.bookChapters.indices }
                 prepareSpansForChapters(_uiState.value.bookChapters, nearby)
             }
        }
    }
    
    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }
    
    fun hideControls() {
        _uiState.update { it.copy(showControls = false) }
    }
    
    fun toggleTheme() {
        val newTheme = !_uiState.value.isDarkTheme
        _uiState.update { it.copy(isDarkTheme = newTheme) }
        ReaderThemeRepository.saveTheme(newTheme)
    }
    
    fun increaseFontSize() {
        if (_uiState.value.fontSize < 36f) {
            _uiState.update { it.copy(fontSize = it.fontSize + 2) }
        }
    }
    
    fun decreaseFontSize() {
        if (_uiState.value.fontSize > 12f) {
            _uiState.update { it.copy(fontSize = it.fontSize - 2) }
        }
    }
    
    fun toggleFavorite() {
        FavoritesRepository.toggleFavorite(context, cleanPath)
        val isFav = !_uiState.value.isFavorite
        _uiState.update { it.copy(isFavorite = isFav) }
    }
    
    fun addHighlight(highlight: TextHighlight) {
        TextHighlightRepository.saveHighlight(highlight)
        refreshHighlights()
    }
    
    fun removeHighlight(highlightId: String) {
        TextHighlightRepository.removeHighlight(highlightId)
        refreshHighlights()
    }
    
    private fun refreshHighlights() {
        val newHighlights = TextHighlightRepository.getHighlightsForBook(cleanPath)
        _uiState.update { it.copy(highlights = newHighlights) }
        
        // Force update of cached spans to re-render highlights? 
        // Actually, spans are raw text. Highlights are applied on top in the UI. 
        // But if we baked highlights into spans, we would need to invalidate cache.
        // Current implementation applies background spans dynamically in UI, which is fine if text is pre-parsed.
    }
    
    class Factory(private val application: Application, private val comicUri: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReaderViewModel(application, comicUri) as T
        }
    }
}
