package com.example.booklabs.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableFloatStateOf
import coil.compose.AsyncImage
import com.example.booklabs.model.File as ComicFile // Alias to avoid confusion with java.io.File
import com.example.booklabs.ui.theme.Purple
import com.example.booklabs.ui.theme.PurpleLight
import com.example.booklabs.ui.theme.Surface
import com.example.booklabs.ui.theme.TextSecondary
import com.example.booklabs.data.ContentLoader
import com.example.booklabs.data.repository.FavoritesRepository
import com.example.booklabs.data.repository.ReadingProgressRepository
import com.example.booklabs.util.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onComicClick: (ComicFile) -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadClick: () -> Unit, // Reused as Favorites button for now or separate
    onFavoritesClick: () -> Unit = {} 
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Permission State
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasPermission by remember { 
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, 
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        ) 
    }

    // Refresh permission on resume
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    android.os.Environment.isExternalStorageManager()
                } else {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, 
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Init Favorites & Progress
    LaunchedEffect(Unit) {
        FavoritesRepository.init(context)
        com.example.booklabs.data.repository.ReadingProgressRepository.init(context)
    }

    // State
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Livros, 1: HQs, 2: Mangás
    val tabs = listOf("Livros", "HQs", "Mangás")
    
    var isFavoritesMode by remember { mutableStateOf(false) } // Favorites Mode State
    
    var files by remember { mutableStateOf<List<ComicFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }
    
    // Selection for options
    var selectedFile by remember { mutableStateOf<ComicFile?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) } // Error state for rename dialog

    // ... (rest of states)

    // ... Inside ModalBottomSheet ...
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Init Storage (Only if permission granted)
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            withContext(Dispatchers.IO) {
                StorageManager.initializeDirectories()
            }
            // Force refresh is effectively handled by the LaunchedEffect below observing hasPermission
            // but we can set loading here just in case if needed, though loadFiles handles it.
        }
    }

    // Updated Loader Function
    fun loadFiles() {
        if (!hasPermission) return
        
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val dirsToScan = if (isFavoritesMode) {
                listOf(
                    StorageManager.getBooksDirectory(), 
                    StorageManager.getComicsDirectory(), 
                    StorageManager.getMangasDirectory()
                )
            } else {
                listOf(when(selectedTab) {
                    0 -> StorageManager.getBooksDirectory()
                    1 -> StorageManager.getComicsDirectory()
                    else -> StorageManager.getMangasDirectory()
                })
            }
            
            val allFiles = mutableListOf<ComicFile>()
            
            for (targetDir in dirsToScan) {
                if (!targetDir.exists()) targetDir.mkdirs()
                
                // Identify type based on dir for now (simplified)
                val typeDetails = when {
                    targetDir.absolutePath.contains("Books") -> "book"
                    targetDir.absolutePath.contains("Comics") -> "comic"
                    else -> "manga"
                }

                val loadedFiles = targetDir.listFiles()?.filter { 
                    it.isFile && !it.name.startsWith(".") && 
                    (it.name.endsWith(".pdf", true) || 
                     it.name.endsWith(".epub", true) || 
                     it.name.endsWith(".cbz", true) || 
                     it.name.endsWith(".cbr", true))
                }?.map { file ->
                    // Attempt to extract cover if not exists (running in IO, so acceptable)
                    com.example.booklabs.util.CoverManager.extractAndSaveCover(context, file)
                    val coverFile = com.example.booklabs.util.CoverManager.getCoverFile(file)
                    val coverPath = if (coverFile.exists()) coverFile.absolutePath else null
                    
                    val isFav = FavoritesRepository.isFavorite(file.absolutePath)
                    
                    ComicFile(
                        id = file.absolutePath.hashCode().toString(),
                        title = file.nameWithoutExtension,
                        fileName = file.name,
                        coverUrl = coverPath,
                        path = file.absolutePath,
                        type = typeDetails,
                        favorite = isFav, 
                        markPage = 0
                    )
                } ?: emptyList()
                allFiles.addAll(loadedFiles)
            }
            
            val finalFiles = if (isFavoritesMode) {
                allFiles.filter { it.favorite }
            } else {
                allFiles
            }
            
            withContext(Dispatchers.Main) {
                files = finalFiles
                isLoading = false
            }
        }
    }

    // Refresh when tab changes, mode changes or permission granted
    LaunchedEffect(selectedTab, isFavoritesMode, hasPermission) {
        if (hasPermission) {
            loadFiles()
        }
    }

    // Cover Picker Launcher
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                if (selectedFile != null) {
                    val targetFile = File(selectedFile!!.path)
                    com.example.booklabs.util.CoverManager.saveCover(context, targetFile, it)
                    withContext(Dispatchers.Main) {
                        loadFiles() // Refresh to show new cover
                    }
                }
            }
        }
    }

    // Import Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                isLoading = true
                val type = when(selectedTab) { 0 -> "book" 1 -> "comic" else -> "manga" }
                StorageManager.importFile(context, it, type)
                withContext(Dispatchers.Main) {
                    loadFiles()
                }
            }
        }
    }
    
    // Legacy Permission Launcher
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    if (!hasPermission) {
        // ... (Permission UI code remains same, omitted for brevity in replace but kept in file via tool logic if I don't select it)
        // Note: I am replacing the whole block from 99 to 439, so I must include the permission UI or define the range carefully.
        // To be safe I'll include the Permission UI Check logic in the replacement if it falls in range, but actually
        // the range 99-439 covers almost everything.
        // Let's just output the Permission UI code block here to be safe.
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Settings, "Permissão", tint = Purple, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                "Permissão Necessária", 
                style = MaterialTheme.typography.titleLarge, 
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Para gerenciar sua biblioteca, o BookLabs precisa de acesso total aos arquivos.",
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.addCategory("android.intent.category.DEFAULT")
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    } else {
                        legacyPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Purple)
            ) {
                Text("Conceder Acesso")
            }
        }
        return
    }

    val filteredFiles = if (searchQuery.isEmpty()) files else files.filter { it.title.contains(searchQuery, true) }

    Scaffold(
        floatingActionButton = {
            if (!isFavoritesMode) {
                 FloatingActionButton(
                    onClick = { 
                        val mimeTypes = when(selectedTab) {
                            0 -> arrayOf("application/pdf", "application/epub+zip")
                            else -> arrayOf("application/pdf", "application/x-cbz", "application/x-cbr", "application/zip", "application/x-rar-compressed")
                        }
                        importLauncher.launch(mimeTypes)
                    },
                    containerColor = Purple,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                     Icon(Icons.Default.Add, "Adicionar")
                }
            }
        },
        topBar = {
            Column(modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding() // Adapts to safe area/cutout
            ) {
                // Custom Top Bar with Tabs
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Title
                        Text(
                            text = if (isFavoritesMode) "Favoritos" else "Biblioteca",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                                Icon(Icons.Default.Search, "Buscar", tint = if(isSearchVisible) PurpleLight else Color.White)
                            }
                            
                            // Favorites Toggle
                            IconButton(onClick = { isFavoritesMode = !isFavoritesMode }) {
                                Icon(
                                    imageVector = if (isFavoritesMode) Icons.Filled.Star else Icons.Outlined.Star,
                                    contentDescription = "Favoritos",
                                    tint = if (isFavoritesMode) Purple else Color.White
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = isSearchVisible) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar...", color = TextSecondary) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Surface,
                                unfocusedContainerColor = Surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Tabs - Hide in Favorites Mode!
                    AnimatedVisibility(visible = !isFavoritesMode) {
                        SecondaryTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = PurpleLight,
                            indicator = { 
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(selectedTab),
                                    color = Purple
                                )
                            },
                            divider = {}
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = { 
                                        Text(
                                            title, 
                                            fontWeight = if(selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                            color = if(selectedTab == index) Color.White else TextSecondary
                                        ) 
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        // Swipe Detection Variables
        var swipeOffsetX by remember { mutableFloatStateOf(0f) }
        val minSwipeDist = 50.dp // Minimum distance to register swipe
        val density = LocalDensity.current
        val minSwipePx = with(density) { minSwipeDist.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (!isFavoritesMode) {
                                if (swipeOffsetX > minSwipePx && selectedTab > 0) {
                                    selectedTab--
                                } else if (swipeOffsetX < -minSwipePx && selectedTab < tabs.size - 1) {
                                    selectedTab++
                                }
                            }
                            swipeOffsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            swipeOffsetX += dragAmount
                        }
                    )
                }
        ) {
            if (isLoading) {
                 Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                     CircularProgressIndicator(color = Purple)
                 }
            } else if (filteredFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = TextSecondary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (isFavoritesMode) "Nenhum favorito encontrado." else "Nenhum item encontrado.\nToque em + para adicionar.",
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp, 
                        end = 16.dp, 
                        // Adjust top padding based on mode (tabs visible or not)
                        top = 8.dp, 
                        bottom = 16.dp + 80.dp // Extra padding for FAB space
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredFiles) { file ->
                        FileCard(
                            file = file,
                            onClick = { onComicClick(file) },
                            onLongClick = {
                                selectedFile = file
                                showBottomSheet = true
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Bottom Sheet and Dialogs remain mostly the same...
    if (showBottomSheet && selectedFile != null) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = Surface,
            contentColor = Color.White
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(selectedFile?.title ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp)) // Increased initial spacer
                
                // Toggle Favorite Option
                Row(
                   Modifier
                       .fillMaxWidth()
                       .clickable {
                           FavoritesRepository.toggleFavorite(context, selectedFile!!.path)
                           showBottomSheet = false
                           loadFiles() // Refresh list
                       }
                       .padding(8.dp),
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    val isFav = FavoritesRepository.isFavorite(selectedFile!!.path)
                    Icon(
                        if (isFav) Icons.Filled.Star else Icons.Outlined.Star, 
                        null, 
                        tint = if (isFav) Purple else TextSecondary
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(if (isFav) "Remover dos Favoritos" else "Adicionar aos Favoritos", color = Color.White)
                }

                Spacer(Modifier.height(12.dp)) // Reduced spacing

                // Change Cover Option
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { 
                            showBottomSheet = false
                            coverPickerLauncher.launch("image/*")
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Image, null, tint = Color.White)
                    Spacer(Modifier.width(16.dp))
                    Text("Alterar Capa", color = Color.White)
                }

                Spacer(Modifier.height(12.dp)) // Reduced spacing

                // Rename Option
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { 
                            showBottomSheet = false
                            newFileName = selectedFile?.title ?: ""
                            renameError = null
                            showRenameDialog = true 
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, null, tint = Color.White)
                    Spacer(Modifier.width(16.dp))
                    Text("Renomear Arquivo", color = Color.White)
                }

                Spacer(Modifier.height(12.dp)) // Reduced spacing

                // Delete Option
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { 
                            showBottomSheet = false
                            showDeleteDialog = true 
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFFF8A80))
                    Spacer(Modifier.width(16.dp))
                    Text("Excluir", color = Color(0xFFFF8A80))
                }
                Spacer(Modifier.height(48.dp)) // Bottom padding
            }
        }
    }

    if (showRenameDialog && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renomear") },
            text = { 
                Column {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { 
                            newFileName = it 
                            renameError = null
                        },
                        label = { Text("Novo Nome") },
                        singleLine = true,
                        isError = renameError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Purple,
                            unfocusedBorderColor = TextSecondary,
                            cursorColor = Purple,
                            focusedLabelColor = Purple,
                            unfocusedLabelColor = TextSecondary
                        )
                    )
                    if (renameError != null) {
                        Text(
                            text = renameError!!, 
                            color = Color.Red, 
                            style = MaterialTheme.typography.bodySmall, 
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFileName.isBlank()) {
                        renameError = "O nome não pode ser vazio."
                        return@TextButton
                    }
                    
                    try {
                        val currentFile = File(selectedFile!!.path)
                        val extension = currentFile.extension
                        val newFile = File(currentFile.parent, "$newFileName.$extension")
                        
                        if (newFile.exists()) {
                            renameError = "Já existe um arquivo com esse nome."
                            return@TextButton
                        }

                        if (currentFile.renameTo(newFile)) {
                            // Rename Cover as well to keep association
                            val oldCover = com.example.booklabs.util.CoverManager.getCoverFile(currentFile)
                            if (oldCover.exists()) {
                                val newCover = com.example.booklabs.util.CoverManager.getCoverFile(newFile)
                                oldCover.renameTo(newCover)
                            }
                            
                            // Update Favorites if necessary
                            if (FavoritesRepository.isFavorite(currentFile.absolutePath)) {
                                FavoritesRepository.toggleFavorite(context, currentFile.absolutePath) // Remove old
                                FavoritesRepository.toggleFavorite(context, newFile.absolutePath)   // Add new
                            }
                            
                            // Update Reading Progress if necessary

                            val progress = com.example.booklabs.data.repository.ReadingProgressRepository.getProgress(currentFile.absolutePath)
                            if (progress > 0) {
                                com.example.booklabs.data.repository.ReadingProgressRepository.saveProgress(newFile.absolutePath, progress)
                            }
                            
                            loadFiles()
                            showRenameDialog = false
                            selectedFile = null
                        } else {
                            renameError = "Falha ao renomear arquivo."
                        }
                    } catch (e: Exception) {
                        renameError = "Erro: ${e.localizedMessage}"
                    }
                }) { Text("Renomear", color = Purple) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancelar") }
            },
            containerColor = Surface,
            titleContentColor = Color.White,
            textContentColor = TextSecondary
        )
    }

    if (showDeleteDialog && selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Excluir arquivo?") },
            text = { Text("Deseja excluir permanentemente '${selectedFile?.title}'?") },
            confirmButton = {
                TextButton(onClick = {
                    File(selectedFile!!.path).delete()
                    val cover = com.example.booklabs.util.CoverManager.getCoverFile(File(selectedFile!!.path))
                    if (cover.exists()) cover.delete()
                    
                    loadFiles()
                    showDeleteDialog = false
                    selectedFile = null
                }) { Text("Excluir", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            },
            containerColor = Surface,
            titleContentColor = Color.White,
            textContentColor = TextSecondary
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileCard(
    file: ComicFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(110.dp) // Fixed width for consistency
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(0.7f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)) // Slightly less rounded for sleek look
                .background(Surface),
            contentAlignment = Alignment.Center
        ) {
            if (file.coverUrl != null) {
                AsyncImage(
                    model = file.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Placeholder
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = TextSecondary.copy(0.3f), modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = file.title,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
