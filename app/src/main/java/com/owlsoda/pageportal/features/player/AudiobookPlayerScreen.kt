package com.owlsoda.pageportal.features.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudiobookPlayerScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: AudiobookPlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    
    var showChapters by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    
    // Breathing animation for slider
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val sliderScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slider_scale"
    )

    LaunchedEffect(bookId) {
        viewModel.initializePlayer(context)
        viewModel.loadBook(bookId)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount > 50) { // Simple threshold
                        onBack()
                    }
                }
            }
    ) {
        // 1. Immersive Background Layer
        // ... (unchanged)
        if (state.coverUrl.isNotEmpty()) {
            AsyncImage(
                model = state.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 60.dp)
                    .scale(1.2f),
                contentScale = ContentScale.Crop
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            )
        }

        // 2. Content Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ... (TopAppBar unchanged)
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showChapters = true }) {
                        Icon(Icons.AutoMirrored.Filled.List, "Chapters", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
            
            if (state.isLoading) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (state.error != null) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    val localContext = LocalContext.current
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(state.error!!))
                                android.widget.Toast.makeText(localContext, "Error copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("Copy Error")
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Hero Cover Art
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .size(320.dp)
                        .aspectRatio(1f)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                if (dragAmount > 50) {
                                    viewModel.rewind() // Right slide (drag right) -> Seek Back
                                } else if (dragAmount < -50) {
                                    viewModel.forward() // Left slide (drag left) -> Seek Forward
                                }
                            }
                        }
                ) {
                    AsyncImage(
                        model = state.coverUrl,
                        contentDescription = state.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Metadata
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.author,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Scrubber / Progress
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Slider(
                        value = state.currentPosition.toFloat(),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(if (state.isPlaying) sliderScale else 1f)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatTime(state.currentPosition),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            "-${formatTime(state.duration - state.currentPosition)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed
                    TextButton(onClick = { showSpeedPicker = true }) {
                        Text(
                            "${state.playbackSpeed}x",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Rewind
                    IconButton(
                        onClick = { viewModel.rewind() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.Replay10,
                            "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // Play/Pause (Animated)
                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier
                            .size(72.dp)
                            .scale(if (state.isPlaying) 1.05f else 1f), // Subtle press effect logic could go here
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        AnimatedContent(
                            targetState = state.isPlaying,
                            transitionSpec = {
                                scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
                            },
                            label = "PlayPause"
                        ) { isPlaying ->
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    
                    // Forward
                    IconButton(
                        onClick = { viewModel.forward() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.Forward30,
                            "Forward 30s",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // Sleep Timer
                    IconButton(onClick = { showSleepTimer = true }) {
                        Icon(
                            Icons.Default.Bedtime,
                            "Sleep Timer",
                            tint = if (state.sleepTimerRemaining > 0) MaterialTheme.colorScheme.primaryContainer else Color.White
                        )
                    }
                }
            }
        }
        
        // Chapter bottom sheet (reused logic, updated style)
        if (showChapters) {
            ModalBottomSheet(
                onDismissRequest = { showChapters = false },
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
            ) {
                Text(
                    "Chapters",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 16.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                ) {
                    itemsIndexed(state.chapters) { index, chapter ->
                        val isCurrent = index == state.currentChapterIndex
                        ListItem(
                            headlineContent = { 
                                Text(
                                    chapter.title, 
                                    fontWeight = if(isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if(isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                ) 
                            },
                            leadingContent = {
                                if (isCurrent) Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.clickable {
                                viewModel.skipToChapter(index)
                                showChapters = false
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }

        // Sleep timer & Speed picker dialogs (simplified reuse)
        if (showSleepTimer) {
            // Same logic as before but maybe styled deeper if time permits
            // For now, standard dialog is fine
            BasicSleepTimerDialog(
                onDismiss = { showSleepTimer = false },
                onTimeSelected = { viewModel.setSleepTimer(it); showSleepTimer = false },
                onCancel = { viewModel.cancelSleepTimer(); showSleepTimer = false },
                isTimerActive = state.sleepTimerRemaining > 0
            )
        }
        
        if (showSpeedPicker) {
            BasicSpeedPickerDialog(
                currentSpeed = state.playbackSpeed,
                onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
                onDismiss = { showSpeedPicker = false }
            )
        }
    }
}

// Extracted for cleanliness
@Composable
fun BasicSleepTimerDialog(onDismiss: () -> Unit, onTimeSelected: (Int) -> Unit, onCancel: () -> Unit, isTimerActive: Boolean) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column {
                listOf(5, 10, 15, 30, 45, 60).forEach { m ->
                    TextButton(onClick = { onTimeSelected(m) }, modifier = Modifier.fillMaxWidth()) { Text("$m minutes") }
                }
                if (isTimerActive) {
                    HorizontalDivider()
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel Timer", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {}
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BasicSpeedPickerDialog(currentSpeed: Float, onSpeedSelected: (Float) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Playback Speed", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Text("${String.format("%.1f", currentSpeed)}x", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            Slider(value = currentSpeed, onValueChange = onSpeedSelected, valueRange = 0.5f..3.0f, steps = 24, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))
            FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                listOf(1.0f, 1.2f, 1.5f, 2.0f, 2.5f, 3.0f).forEach { speed ->
                    FilterChip(
                        selected = currentSpeed == speed,
                        onClick = { onSpeedSelected(speed) },
                        label = { Text("${speed}x") },
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}
