package com.owlsoda.pageportal.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadAloudPlayerScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: ReadAloudPlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val isSyncingMap by viewModel.isSyncing.collectAsState()
    val isSyncing = isSyncingMap[bookId.toLongOrNull() ?: 0L] == true
    
    var showSleepTimer by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    
    LaunchedEffect(bookId) {
        viewModel.initializePlayer(context)
        viewModel.loadBook(bookId)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopAppBar(
                title = { Text("ReadAloud", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.CloudDone,
                            "Synced",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                )
            )
            
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                val localContext = LocalContext.current
                
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Error",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            state.error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(24.dp))
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
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Copy Error")
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        FilledTonalButton(
                            onClick = { viewModel.deleteReadAloud() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Delete Download")
                        }
                    }
                }
            } else {
                // Main content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Cover art
                    AsyncImage(
                        model = state.coverUrl,
                        contentDescription = state.title,
                        modifier = Modifier
                            .size(280.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Title and author
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.author,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Progress slider
                    Column {
                        Slider(
                            value = state.currentPosition.toFloat(),
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTimeReadAloud(state.currentPosition),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "-${formatTimeReadAloud(state.duration - state.currentPosition)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Playback controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Speed button
                        TextButton(onClick = { showSpeedPicker = true }) {
                            Text("${state.playbackSpeed}x")
                        }
                        
                        // Rewind
                        IconButton(
                            onClick = { viewModel.rewind() },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.Replay10,
                                "Rewind 10s",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        // Play/Pause
                        FilledIconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (state.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        
                        // Forward
                        IconButton(
                            onClick = { viewModel.forward() },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.Forward30,
                                "Forward 30s",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        // Sleep timer
                        IconButton(onClick = { showSleepTimer = true }) {
                            Icon(
                                Icons.Default.Bedtime,
                                "Sleep Timer",
                                tint = if (state.sleepTimerRemaining > 0) 
                                    MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    // Sleep timer indicator
                    if (state.sleepTimerRemaining > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Sleep in ${formatTimeReadAloud(state.sleepTimerRemaining)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
        
        // Sleep timer dialog
        if (showSleepTimer) {
            AlertDialog(
                onDismissRequest = { showSleepTimer = false },
                title = { Text("Sleep Timer") },
                text = {
                    Column {
                        listOf(5, 10, 15, 30, 45, 60).forEach { minutes ->
                            TextButton(
                                onClick = {
                                    viewModel.setSleepTimer(minutes)
                                    showSleepTimer = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("$minutes minutes")
                            }
                        }
                        if (state.sleepTimerRemaining > 0) {
                            HorizontalDivider()
                            TextButton(
                                onClick = {
                                    viewModel.cancelSleepTimer()
                                    showSleepTimer = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel Timer", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                },
                confirmButton = { }
            )
        }
        
        // Speed picker dialog
        if (showSpeedPicker) {
            AlertDialog(
                onDismissRequest = { showSpeedPicker = false },
                title = { Text("Playback Speed") },
                text = {
                    Column {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f).forEach { speed ->
                            val isSelected = state.playbackSpeed == speed
                            TextButton(
                                onClick = {
                                    viewModel.setPlaybackSpeed(speed)
                                    showSpeedPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${speed}x",
                                    color = if (isSelected) 
                                        MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                },
                confirmButton = { }
            )
        }
    }
}

private fun formatTimeReadAloud(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
