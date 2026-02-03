package com.example.booklabs.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.booklabs.ui.theme.Purple
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    showControls: Boolean,
    comicTitle: String,
    textColor: Color,
    surfaceColor: Color,
    isFavorite: Boolean,
    onBackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    // TTS parameters
    isTextBased: Boolean = false,
    isTtsPlaying: Boolean = false,
    showTtsControls: Boolean = false,
    onToggleTtsControls: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = showControls,
        enter = slideInVertically(),
        exit = slideOutVertically()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = comicTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    maxLines = 1
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBackIosNew, "Voltar", tint = textColor)
                }
            },
            actions = {
                // TTS Button (only for text-based content)
                if (isTextBased) {
                    IconButton(onClick = onToggleTtsControls) {
                        Icon(
                            imageVector = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Leitura de Voz",
                            tint = if (showTtsControls || isTtsPlaying) Purple else textColor
                        )
                    }
                }
                
                // Favorite Button
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "Favoritar",
                        tint = if (isFavorite) Purple else textColor
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = surfaceColor
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderBottomBar(
    showControls: Boolean,
    markPage: Int,
    totalPages: Int,
    fontSize: Float,
    isDarkTheme: Boolean,
    textColor: Color,
    surfaceColor: Color,
    isTextBased: Boolean,
    onFontSizeDecrease: () -> Unit,
    onFontSizeIncrease: () -> Unit,
    onToggleTheme: () -> Unit,
    onBookmarkPage: () -> Unit,
    // TTS parameters
    isTtsPlaying: Boolean = false,
    speechRate: Float = 1.0f,
    onTtsPlayPause: () -> Unit = {},
    onTtsStop: () -> Unit = {},
    onTtsPrevious: () -> Unit = {},
    onTtsNext: () -> Unit = {},
    onTtsSpeedChange: (Float) -> Unit = {},
    showTtsControls: Boolean = false,
    onToggleTtsControls: () -> Unit = {},
    // Zoom parameters
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = showControls,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Column {
            if (totalPages > 0) {
                // Custom Progress Bar with rounded corners
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(textColor.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (markPage + 1).toFloat() / totalPages.toFloat().coerceAtLeast(1f))
                            .fillMaxHeight()
                            .background(Purple)
                    )
                }
            }
            
            // TTS Controls Panel (expandable)
            AnimatedVisibility(visible = showTtsControls && isTextBased) {
                Surface(
                    color = surfaceColor.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Controles de Leitura de Voz",
                            color = textColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Playback controls
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = onTtsPrevious) {
                                Icon(Icons.Default.SkipPrevious, "Capítulo Anterior", tint = textColor)
                            }
                            
                            IconButton(onClick = onTtsStop) {
                                Icon(Icons.Default.Stop, "Parar", tint = textColor)
                            }
                            
                            IconButton(
                                onClick = onTtsPlayPause,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isTtsPlaying) "Pausar" else "Reproduzir",
                                    tint = Purple,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            
                            IconButton(onClick = onTtsNext) {
                                Icon(Icons.Default.SkipNext, "Próximo Capítulo", tint = textColor)
                            }
                        }
                        
                        // Speed control
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Speed,
                                "Velocidade",
                                tint = textColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Slider(
                                value = speechRate,
                                onValueChange = onTtsSpeedChange,
                                valueRange = 0.5f..2.0f,
                                steps = 5,
                                modifier = Modifier.weight(1f).height(24.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Purple,
                                    activeTrackColor = Purple,
                                    inactiveTrackColor = textColor.copy(alpha = 0.2f)
                                ),
                                track = { sliderState ->
                                    SliderDefaults.Track(
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = Purple,
                                            inactiveTrackColor = textColor.copy(alpha = 0.2f)
                                        ),
                                        enabled = true,
                                        sliderState = sliderState,
                                        modifier = Modifier.height(4.dp)
                                    )
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = String.format("%.1fx", speechRate),
                                color = textColor,
                                fontSize = 12.sp,
                                modifier = Modifier.width(40.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
            
            BottomAppBar(
                containerColor = surfaceColor,
                contentColor = textColor
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Controls (Font size or Zoom)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isTextBased) {
                            IconButton(onClick = onFontSizeDecrease) {
                                Icon(Icons.Default.Remove, "Diminuir Fonte", tint = textColor)
                            }
                            IconButton(onClick = onFontSizeIncrease) {
                                Icon(Icons.Default.Add, "Aumentar Fonte", tint = textColor)
                            }
                        } else {
                            IconButton(onClick = onZoomOut) {
                                Icon(Icons.Default.Remove, "Diminuir Zoom", tint = textColor)
                            }
                            IconButton(onClick = onZoomIn) {
                                Icon(Icons.Default.Add, "Aumentar Zoom", tint = textColor)
                            }
                        }
                    }

                    // Center - Page Count
                    Text(
                        text = if (totalPages > 0) "${markPage + 1} de $totalPages" else "",
                        color = textColor,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )

                    // Right Controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Alternar Tema",
                                tint = textColor
                            )
                        }
                        IconButton(onClick = onBookmarkPage) {
                            Icon(Icons.Default.Bookmark, "Marcar Página", tint = textColor)
                        }
                    }
                }
            }
        }
    }
}
