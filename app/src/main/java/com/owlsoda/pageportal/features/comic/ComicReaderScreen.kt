package com.owlsoda.pageportal.features.comic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.Image
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicReaderScreen(
    bookId: String,
    filePath: String,
    onBack: () -> Unit,
    viewModel: ComicReaderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showPageSlider by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    
    LaunchedEffect(filePath, bookId) {
        viewModel.loadComic(filePath, bookId)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main content - comic page
        // Main content - comic page
        // Main content - comic page
        if (state.readingMode == ReadingMode.SCROLL_VERTICAL) {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentPage)
            
            // Sync visible item to ViewModel
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .collect { index ->
                        // Don't call goToPage here to avoid loops, just update state subtly?
                        // Or just trust the list position for this mode.
                        // Ideally we update the VM so "resume" works.
                        // viewModel.updateCurrentPage(index) 
                    }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val boxWidth = constraints.maxWidth.toFloat()
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val tapZone = boxWidth / 3
                                    if (offset.x > tapZone && offset.x < boxWidth - tapZone) {
                                        viewModel.toggleControls()
                                    }
                                }
                            )
                        }
                ) {
                    items(state.pageCount) { index ->
                        var itemBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                        
                        LaunchedEffect(index) {
                            itemBitmap = viewModel.getPage(index)
                        }
                        
                        if (itemBitmap != null) {
                            Image(
                                bitmap = itemBitmap!!.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        } else {
                            // Loading placeholder
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(400.dp) // Estimate height
                                    .background(Color.DarkGray),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    }
                }
            }
        } else if (state.currentBitmap != null) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val boxWidth = constraints.maxWidth.toFloat()
                
                ZoomableImage(
                    bitmap = state.currentBitmap!!.asImageBitmap(),
                    contentDescription = "Page ${state.currentPage + 1}",
                    onTap = { offset ->
                        val tapZone = boxWidth / 3
                        
                        when {
                            offset.x < tapZone -> viewModel.previousPage()
                            offset.x > boxWidth - tapZone -> viewModel.nextPage()
                            else -> viewModel.toggleControls()
                        }
                    }
                )
            }
        }
        
        // Loading indicator
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        
        // Error message
        if (state.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        state.error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                }
            }
        }
        
        // Controls overlay
        if (state.showControls) {
            // Top bar
            TopAppBar(
                title = { 
                    Text(
                        state.title,
                        maxLines = 1,
                        color = Color.White
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            Icons.Default.Settings,
                            "Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                )
            )
            
            // Bottom controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp)
            ) {
                // Page indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.previousPage() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    TextButton(
                        onClick = { showPageSlider = !showPageSlider }
                    ) {
                        Text(
                            "${state.currentPage + 1} / ${state.pageCount}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                    
                    IconButton(onClick = { viewModel.nextPage() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            "Next",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                // Page slider
                if (showPageSlider && state.pageCount > 1) {
                    Slider(
                        value = state.currentPage.toFloat(),
                        onValueChange = { viewModel.goToPage(it.toInt()) },
                        valueRange = 0f..(state.pageCount - 1).toFloat(),
                        steps = state.pageCount - 2,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.Gray
                        )
                    )
                }
            }
        }
        
        // Settings bottom sheet
        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Reading Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Reading mode
                    Text(
                        "Reading Mode",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReadingMode.values().forEach { mode ->
                            FilterChip(
                                selected = state.readingMode == mode,
                                onClick = { viewModel.setReadingMode(mode) },
                                label = { 
                                    Text(
                                        when (mode) {
                                            ReadingMode.SINGLE_PAGE -> "Single"
                                            ReadingMode.SCROLL_VERTICAL -> "Scroll V"
                                            ReadingMode.SCROLL_HORIZONTAL -> "Scroll H"
                                            ReadingMode.DOUBLE_PAGE -> "Double"
                                        }
                                    )
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // RTL toggle (for manga)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Right to Left",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "For manga and RTL comics",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.isRightToLeft,
                            onCheckedChange = { viewModel.toggleRightToLeft() }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Jump to page
                    Text(
                        "Jump to Page",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var jumpPage by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = jumpPage,
                            onValueChange = { 
                                jumpPage = it.filter { c -> c.isDigit() }
                            },
                            label = { Text("Page") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                jumpPage.toIntOrNull()?.let { page ->
                                    if (page in 1..state.pageCount) {
                                        viewModel.goToPage(page - 1)
                                        showSettings = false
                                    }
                                }
                            }
                        ) {
                            Text("Go")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
