package com.owlsoda.pageportal.features.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.owlsoda.pageportal.ui.components.ErrorState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    onBack: () -> Unit,
    onPlayAudiobook: (String) -> Unit,
    onReadEbook: (String) -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Unlink Book") },
                            onClick = {
                                viewModel.unlinkBook()
                                showMenu = false
                                onBack() // Navigate back after unlinking as the unified ID goes invalid
                            },
                            leadingIcon = { Icon(Icons.Default.LinkOff, null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                )
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                ErrorState(message = state.error!!)
            }
            state.book != null -> {
                val book = state.book!!
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header with cover
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = book.coverUrl,
                            contentDescription = book.title,
                            modifier = Modifier
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.FillHeight
                        )
                    }
                    
                    // Book info
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = book.authors,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        if (!book.series.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (book.seriesIndex != null) {
                                    "${book.series} #${book.seriesIndex}"
                                } else book.series,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        // Progress indicator
                        if (state.progressPercent > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Progress",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(
                                        "${state.progressPercent.toInt()}%",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = state.progressPercent / 100f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Format indicators and action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Audio Actions
                            if (book.hasAudiobook) {
                                if (state.isDownloaded) {
                                     Button(
                                        onClick = { onPlayAudiobook(book.id.toString()) },
                                        modifier = Modifier.weight(1f)
                                     ) {
                                         Icon(Icons.Default.Headphones, null)
                                         Spacer(modifier = Modifier.width(8.dp))
                                         Text("Listen")
                                     }
                                } else {
                                     OutlinedButton(
                                         onClick = { viewModel.startDownload(context) }, // Defaults to audio download
                                         modifier = Modifier.weight(1f),
                                         enabled = !state.isDownloading
                                     ) {
                                         Icon(Icons.Default.Download, null)
                                         Spacer(modifier = Modifier.width(8.dp))
                                         Text("Audio")
                                     }
                                }
                            }
                            
                            // Ebook Actions
                            if (book.hasEbook) {
                                Button(
                                    onClick = { onReadEbook(book.id.toString()) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(Icons.Default.MenuBook, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Read")
                                }
                            }
                        }
                        
                        // Download Status
                        if (state.isDownloading) {
                             Spacer(modifier = Modifier.height(16.dp))
                             Card(modifier = Modifier.fillMaxWidth()) {
                                 Column(modifier = Modifier.padding(16.dp)) {
                                     Row(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalArrangement = Arrangement.SpaceBetween,
                                         verticalAlignment = Alignment.CenterVertically
                                     ) {
                                         Text("Downloading...")
                                         TextButton(onClick = { viewModel.cancelDownload() }) {
                                             Text("Cancel")
                                         }
                                     }
                                     Spacer(modifier = Modifier.height(8.dp))
                                     LinearProgressIndicator(
                                         progress = state.downloadProgress,
                                         modifier = Modifier.fillMaxWidth()
                                     )
                                     Spacer(modifier = Modifier.height(4.dp))
                                     Text(
                                         "${(state.downloadProgress * 100).toInt()}%",
                                         style = MaterialTheme.typography.bodySmall
                                     )
                                 }
                             }
                        } else if (state.isDownloaded && book.hasAudiobook) {
                             Spacer(modifier = Modifier.height(16.dp))
                             Card(
                                 colors = CardDefaults.cardColors(
                                     containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.2f)
                                 ),
                                 modifier = Modifier.fillMaxWidth()
                             ) {
                                 Row(
                                     modifier = Modifier.padding(12.dp),
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                     Spacer(modifier = Modifier.width(8.dp))
                                     Text("Audiobook downloaded for offline playback", style = MaterialTheme.typography.bodySmall)
                                 }
                             }
                        }
                        
                        // Description
                        if (!book.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "Description",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = book.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
