package com.owlsoda.pageportal.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import com.owlsoda.pageportal.navigation.LocalTopBarState
import com.owlsoda.pageportal.features.library.LibraryViewModel
import com.owlsoda.pageportal.features.library.UnifiedBookDisplay
import com.owlsoda.pageportal.features.library.BookCard
import com.owlsoda.pageportal.ui.components.SkeletonHorizontalRow
import com.owlsoda.pageportal.ui.components.SkeletonLibraryGrid
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color

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
        topBarState.title = "PagePortal"
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // Skeleton loading state — show shimmer placeholders while data loads
            if (uiState.isLoading && uiState.books.isEmpty()) {
                item {
                    SkeletonHorizontalRow(itemCount = 3)
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SkeletonHorizontalRow(itemCount = 4)
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SkeletonLibraryGrid(columns = 3, rows = 2)
                }
            }
            
            // Continue Reading (Mock logic for now: take first book with progress > 0)
            val continueReading = uiState.books.filter { it.downloadProgress > 0 || it.isDownloaded }.take(1) // Simplified
            if (continueReading.isNotEmpty()) {
                item {
                    Text("Continue Reading", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    BookCard(
                         book = continueReading.first(), 
                         onClick = { onBookClick("u_${continueReading.first().id}") },
                         modifier = Modifier.width(140.dp) // Or full width card
                    )
                }
            }

            // Recently Added
            if (uiState.recentBooks.isNotEmpty()) {
                item {
                    Text("Recently Added", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.recentBooks) { book ->
                            BookCard(
                                book = book,
                                onClick = { onBookClick("u_${book.id}") },
                                modifier = Modifier.width(120.dp)
                            )
                        }
                    }
                }
            }
            
            // Quick Access to Services
            /*
            item {
                Text("Your Services", style = MaterialTheme.typography.titleMedium)
                // ... Row of cards for Storyteller, ABS, etc if desired
            }
            */
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
}
