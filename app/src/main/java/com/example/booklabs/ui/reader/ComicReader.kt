package com.example.booklabs.ui.reader

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ComicReaderContainer(
    pages: List<String>,
    initialPage: Int,
    externalScale: Float = 1f,
    onPageChange: (Int) -> Unit,
    onTap: () -> Unit
) {
    ImageViewer(
        pages = pages,
        initialPage = initialPage,
        externalScale = externalScale,
        onPageChange = onPageChange,
        onTap = onTap
    )
}

@Composable
fun ImageViewer(
    pages: List<String>, 
    initialPage: Int = 0,
    externalScale: Float = 1f,
    onPageChange: (Int) -> Unit, 
    onTap: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
        pageCount = { pages.size }
    )
    var isZoomEnabled by remember { mutableStateOf(false) }
    
    LaunchedEffect(pagerState.currentPage) { onPageChange(pagerState.currentPage) }
    LaunchedEffect(pagerState.currentPage) { isZoomEnabled = false }

    HorizontalPager(
        state = pagerState, 
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !isZoomEnabled
    ) { page ->
        ZoomableBox(
            externalScale = externalScale,
            onZoomChange = { isZoomEnabled = it }, 
            onTap = onTap
        ) {
             AsyncImage(
                model = File(pages[page]),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 4f,
    externalScale: Float = 1f,
    onZoomChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val scale = remember { androidx.compose.animation.core.Animatable(externalScale) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    
    // Sincronizar com a escala externa vinda dos botões da barra inferior
    LaunchedEffect(externalScale) {
        scale.animateTo(externalScale.coerceIn(minScale, maxScale))
        if (externalScale <= minScale) {
            offset = Offset.Zero
        }
    }

    LaunchedEffect(scale.value) {
        onZoomChange(scale.value > minScale)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onTap() }
                )
            }
            .pointerInput(Unit) {
                detectSmartZoomGestures(
                    isZoomed = { scale.value > minScale }
                ) { pan, zoom ->
                    scope.launch {
                        val newScale = (scale.value * zoom).coerceIn(minScale, maxScale)
                        scale.snapTo(newScale)
                        
                        if (scale.value > minScale) {
                            offset = Offset(
                                x = (offset.x + pan.x * scale.value),
                                y = (offset.y + pan.y * scale.value)
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        content()
    }
}

suspend fun PointerInputScope.detectSmartZoomGestures(
    isZoomed: () -> Boolean,
    onGesture: (pan: Offset, zoom: Float) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val zoom = event.calculateZoom()
            val pan = event.calculatePan()

            if (zoom != 1f || (isZoomed() && pan != Offset.Zero)) {
                onGesture(pan, zoom)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
