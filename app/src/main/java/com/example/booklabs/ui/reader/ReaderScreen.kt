package com.example.booklabs.ui.reader

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.PointerInputScope
import coil.compose.AsyncImage
import com.example.booklabs.data.ContentLoader
import com.example.booklabs.ui.theme.Black
import com.example.booklabs.ui.theme.Purple
import com.example.booklabs.ui.theme.PurpleLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import com.example.booklabs.util.TextToSpeechManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    comicUri: String,
    comicFileName: String,
    comicTitle: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    
    // ViewModel Setup
    val viewModel: ReaderViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = ReaderViewModel.Factory(application, comicUri)
    )
    val uiState by viewModel.uiState.collectAsState()
    
    // Continuar com implementação para todos os formatos
    val window = (context as? android.app.Activity)?.window
    
    // Immersive Mode
    DisposableEffect(Unit) {
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    
    // Local UI states that are strictly viewing related (e.g. zoom scale)
    var comicScale by remember { mutableFloatStateOf(1f) }
    
    // TTS States (kept local as it binds to Android Service/Context heavily)
    val ttsManager = remember { TextToSpeechManager(context) }
    val isTtsPlaying by ttsManager.isPlaying.collectAsState()
    val speechRate by ttsManager.speechRate.collectAsState()
    var showTtsControls by remember { mutableStateOf(false) }
    
    // Highlights Dialog State
    var showHighlightsList by remember { mutableStateOf(false) }
    
    // Cleanup TTS on dispose
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }
    
    // Sync TTS with chapters when loaded
    LaunchedEffect(uiState.bookChapters) {
        if (uiState.bookChapters.isNotEmpty()) {
            ttsManager.setChapters(uiState.bookChapters)
        }
    }
    
    // Sync TTS navigation with ViewModel
    LaunchedEffect(Unit) {
        ttsManager.onChapterChange = { chapterIndex ->
             viewModel.onPageChanged(chapterIndex)
        }
    }

    // Theme colors
    val backgroundColor = if (uiState.isDarkTheme) Black else Color(0xFFF5F1E8)
    val textColor = if (uiState.isDarkTheme) Color.White else Color(0xFF2C2C2C)
    val surfaceColor = if (uiState.isDarkTheme) Black.copy(alpha = 0.9f) else Color(0xFFE8E4D9).copy(alpha = 0.95f)
    
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            ReaderTopBar(
                showControls = uiState.showControls,
                comicTitle = comicTitle,
                textColor = textColor,
                surfaceColor = surfaceColor,
                isFavorite = uiState.isFavorite,
                onBackClick = onBackClick,
                onToggleFavorite = { viewModel.toggleFavorite() },
                // TTS parameters
                isTextBased = uiState.textContent != null || uiState.bookChapters.isNotEmpty(),
                isTtsPlaying = isTtsPlaying,
                showTtsControls = showTtsControls,
                onToggleTtsControls = { showTtsControls = !showTtsControls }
            )
        },
        bottomBar = {
            ReaderBottomBar(
                showControls = uiState.showControls,
                markPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                fontSize = uiState.fontSize,
                isDarkTheme = uiState.isDarkTheme,
                textColor = textColor,
                surfaceColor = surfaceColor,
                isTextBased = uiState.textContent != null || uiState.bookChapters.isNotEmpty(),
                onFontSizeDecrease = { viewModel.decreaseFontSize() },
                onFontSizeIncrease = { viewModel.increaseFontSize() },
                onToggleTheme = { viewModel.toggleTheme() },
                onBookmarkPage = {
                    // Abrir lista de marcações de texto
                    showHighlightsList = true
                },
                // TTS parameters
                isTtsPlaying = isTtsPlaying,
                speechRate = speechRate,
                showTtsControls = showTtsControls,
                onToggleTtsControls = { showTtsControls = !showTtsControls },
                onTtsPlayPause = {
                    if (isTtsPlaying) {
                        ttsManager.pause()
                    } else {
                        // Sincronizar com a página atual antes de começar
                        ttsManager.goToChapter(uiState.currentPage)
                        ttsManager.play()
                    }
                },
                onTtsStop = {
                    ttsManager.stop()
                },
                onTtsPrevious = {
                    ttsManager.playPreviousChapter()
                },
                onTtsNext = {
                    ttsManager.playNextChapter()
                },
                onTtsSpeedChange = { newRate ->
                    ttsManager.setSpeechRate(newRate)
                },
                onZoomIn = {
                    if (comicScale < 4f) comicScale += 0.5f
                },
                onZoomOut = {
                    if (comicScale > 1f) comicScale -= 0.5f
                }
            )
        },
        containerColor = backgroundColor,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        // Content Box (First layer)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 70.dp) // Reduzido de 80dp para 24dp para aproveitar melhor a tela
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { viewModel.toggleControls() },
                        onDoubleTap = { viewModel.toggleControls() }
                    )
                }
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = PurpleLight)
            } else if (uiState.error != null) {
                Text(uiState.error ?: "Erro", color = Color.Red, modifier = Modifier.align(Alignment.Center))
            } else {
                when {
                    uiState.textContent != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(
                                text = uiState.textContent!!,
                                color = textColor,
                                fontSize = uiState.fontSize.sp,
                                lineHeight = (uiState.fontSize * 1.5).sp
                            )
                        }
                    }
                    uiState.bookChapters.isNotEmpty() -> {
                        val isPdf = comicFileName.lowercase().endsWith(".pdf")
                        if (isPdf) {
                            PdfReaderContainer(
                                bookId = comicUri.hashCode().toString(),
                                chapters = uiState.bookChapters,
                                fontSize = uiState.fontSize,
                                initialPage = uiState.currentPage,
                                textColor = textColor,
                                bookPath = File(comicUri).let { if(comicUri.startsWith("file://")) File(java.net.URI(comicUri)).absolutePath else comicUri },
                                highlights = uiState.highlights,
                                onPageChange = { page, _ -> 
                                     viewModel.onPageChanged(page)
                                },
                                onTap = { viewModel.toggleControls() },
                                onHighlightCreated = { highlight ->
                                    viewModel.addHighlight(highlight)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Texto marcado!",
                                            duration = androidx.compose.material3.SnackbarDuration.Short
                                        )
                                    }
                                },
                                onHighlightDeleted = { id ->
                                    viewModel.removeHighlight(id)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Marcação removida!",
                                            duration = androidx.compose.material3.SnackbarDuration.Short
                                        )
                                    }
                                }
                            )
                        } else {
                            EpubReaderContainer(
                                bookId = comicUri.hashCode().toString(),
                                chapters = uiState.bookChapters,
                                cachedSpannedChapters = uiState.cachedSpannedChapters, // Injeção de dependência crucial para performance
                                fontSize = uiState.fontSize,
                                initialPage = uiState.currentPage,
                                textColor = textColor,
                                bookPath = File(comicUri).let { if(comicUri.startsWith("file://")) File(java.net.URI(comicUri)).absolutePath else comicUri },
                                highlights = uiState.highlights, // Lista reativa do VM
                                onPageChange = { page, _ -> 
                                     viewModel.onPageChanged(page)
                                },
                                onTap = { viewModel.toggleControls() },
                                onHighlightCreated = { highlight ->
                                    viewModel.addHighlight(highlight)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Texto marcado!",
                                            duration = androidx.compose.material3.SnackbarDuration.Short
                                        )
                                    }
                                },
                                onHighlightDeleted = { id ->
                                    viewModel.removeHighlight(id)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Marcação removida!",
                                            duration = androidx.compose.material3.SnackbarDuration.Short
                                        )
                                    }
                                }
                            )
                        }
                    }
                    uiState.imagePages.isNotEmpty() -> {
                        ComicReaderContainer(
                            pages = uiState.imagePages,
                            initialPage = uiState.currentPage,
                            externalScale = comicScale,
                            onPageChange = { viewModel.onPageChanged(it) },
                            onTap = { viewModel.toggleControls() }
                        )
                    }
                }
            }
        }

        // Highlights List Dialog
        if (showHighlightsList) {
            HighlightsListDialog(
                highlights = uiState.highlights,
                textColor = textColor,
                surfaceColor = surfaceColor,
                onDismiss = { showHighlightsList = false },
                onHighlightClick = { highlight ->
                    // Navigate to the chapter with the highlight
                    viewModel.onPageChanged(highlight.chapterIndex)
                    showHighlightsList = false
                },
                onHighlightDelete = { highlight ->
                    viewModel.removeHighlight(highlight.id)
                }
            )
        }
    }
}
