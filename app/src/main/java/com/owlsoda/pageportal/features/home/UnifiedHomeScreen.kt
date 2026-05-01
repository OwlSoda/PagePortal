package com.owlsoda.pageportal.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.BlurredEdgeTreatment
import com.owlsoda.pageportal.navigation.LocalTopBarState
import com.owlsoda.pageportal.features.library.LibraryViewModel
import com.owlsoda.pageportal.features.library.UnifiedBookDisplay
import com.owlsoda.pageportal.features.library.AuthorDisplay
import com.owlsoda.pageportal.features.library.BookCard
import com.owlsoda.pageportal.ui.components.SkeletonHorizontalRow
import com.owlsoda.pageportal.ui.components.SkeletonLibraryGrid
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Person
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedHomeScreen(
    onBookClick: (String) -> Unit,
    onNavigateToService: (String) -> Unit,
    onGlobalSearchClick: () -> Unit,
    onQueueClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    queueViewModel: com.owlsoda.pageportal.features.queue.QueueDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBook(it) }
    }
    
    val topBarState = LocalTopBarState.current
    LaunchedEffect(Unit) {
        topBarState.title = "Dashboard"
    }

    val activeJobs by queueViewModel.activeJobs.collectAsState()
    
    LaunchedEffect(activeJobs) {
        topBarState.actions = {
            IconButton(onClick = onGlobalSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Global Search")
            }
            
            if (activeJobs.isNotEmpty()) {
                IconButton(onClick = onQueueClick) {
                    BadgedBox(badge = { Badge { Text(activeJobs.size.toString()) } }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Schedule, contentDescription = "Active Alignments")
                    }
                }
            }
            
            IconButton(onClick = { 
                importLauncher.launch(arrayOf("application/epub+zip", "audio/*", "application/zip", "application/octet-stream"))
            }) {
                Icon(Icons.Default.Add, contentDescription = "Import Local File")
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        
        // Dynamic Blurred Background
        val heroBook = uiState.continueReading.firstOrNull() ?: uiState.recentlyAdded.firstOrNull()
        val coverUrl = heroBook?.audiobookCoverUrl ?: heroBook?.coverUrl
        
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "Cover for ${heroBook?.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(100.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer { alpha = 0.4f }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }

        // Gradient overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.background
                        ),
                        startY = 0f,
                        endY = 1000f
                    )
                )
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp, top = 24.dp)
        ) {
            
            // Stats / Greeting
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = "Good to see you", 
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${uiState.books.size} Books across ${uiState.servers.size} Servers", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
            
            // Skeleton loading state
            if (uiState.isLoading && uiState.books.isEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        SkeletonHorizontalRow(itemCount = 3)
                        Spacer(Modifier.height(32.dp))
                        SkeletonHorizontalRow(itemCount = 4)
                        Spacer(Modifier.height(32.dp))
                        SkeletonLibraryGrid(columns = 3, rows = 2)
                    }
                }
            } else {
                
                // Continue Reading (Hero)
                if (uiState.continueReading.isNotEmpty()) {
                    item {
                        SectionHeader("Jump Back In")
                        HeroCarousel(
                            books = uiState.continueReading,
                            onBookClick = onBookClick
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }

                // Recently Added
                if (uiState.recentlyAdded.isNotEmpty()) {
                    item {
                        SectionHeader("Recently Added")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(uiState.recentlyAdded) { book ->
                                BookCard(
                                    book = book,
                                    onClick = { onBookClick("u_${book.id}") },
                                    modifier = Modifier.width(130.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
                
                // Offline Ready
                if (uiState.offlineReady.isNotEmpty()) {
                    item {
                        SectionHeader("Offline Ready")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(uiState.offlineReady) { book ->
                                BookCard(
                                    book = book,
                                    onClick = { onBookClick("u_${book.id}") },
                                    modifier = Modifier.width(130.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
                
                // Top Authors
                if (uiState.homeAuthors.isNotEmpty()) {
                    item {
                        SectionHeader("Top Authors")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            items(uiState.homeAuthors) { author ->
                                AuthorChip(author = author, onClick = { /* Navigate to filtered search, TODO when expanding home navigation */ })
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
        
        // Import Loading Overlay
        if (uiState.isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Adding to Library...", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title, 
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
fun HeroCarousel(books: List<UnifiedBookDisplay>, onBookClick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(books) { book ->
            HeroCard(book = book, onClick = { onBookClick("u_${book.id}") })
        }
    }
}

@Composable
fun HeroCard(book: UnifiedBookDisplay, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .width(320.dp)
            .height(180.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = 0.85f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Cover
            val coverUrl = book.audiobookCoverUrl ?: book.coverUrl
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Cover for ${book.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(modifier = Modifier.width(100.dp).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Info
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Text(
                    text = book.title, 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = book.authors, 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                
                // Progress
                if (book.listeningProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { book.listeningProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${(book.listeningProgress * 100).toInt()}% completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AuthorChip(author: AuthorDisplay, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        if (author.coverUrl != null) {
            AsyncImage(
                model = author.coverUrl,
                contentDescription = "Cover for ${author.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = "Default cover for ${author.name}", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = author.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.Center
        )
    }
}
