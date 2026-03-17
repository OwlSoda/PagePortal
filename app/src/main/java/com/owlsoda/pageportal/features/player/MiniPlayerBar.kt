package com.owlsoda.pageportal.features.player

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.owlsoda.pageportal.ui.theme.BookCampPurple
import com.owlsoda.pageportal.ui.theme.BookCampTextSecondary

/**
 * MiniPlayerBar - A compact persistent player bar shown at the bottom of screens
 * when audio is playing. Allows quick access to play/pause/stop without navigating
 * to the full player screen.
 */
@Composable
fun MiniPlayerBar(
    isVisible: Boolean,
    bookTitle: String,
    bookAuthor: String,
    chapterTitle: String?,
    coverUrl: String?,
    isPlaying: Boolean,
    progress: Float,
    onPlayPauseClick: () -> Unit,
    onStopClick: () -> Unit,
    onBarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showStopConfirmation by remember { mutableStateOf(false) }
    
    // Stop confirmation dialog
    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text("Stop Playback") },
            text = { Text("Are you sure you want to stop playback?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onStopClick()
                        showStopConfirmation = false
                    }
                ) {
                    Text("Stop")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .height(72.dp)
                .clickable(onClick = onBarClick),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 12.dp
        ) {
            Column {
                // Progress indicator
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = BookCampPurple,
                    trackColor = BookCampPurple.copy(alpha = 0.1f)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cover image
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Title and subtitle
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bookTitle,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = chapterTitle ?: bookAuthor,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (chapterTitle != null) 
                                BookCampPurple 
                            else 
                                BookCampTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // Play/Pause button
                    IconButton(onClick = onPlayPauseClick) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = BookCampPurple
                        )
                    }
                    
                    // Stop button
                    IconButton(onClick = { showStopConfirmation = true }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop and Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
