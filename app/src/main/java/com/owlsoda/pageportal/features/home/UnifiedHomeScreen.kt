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
import com.owlsoda.pageportal.features.library.LibraryViewModel
import com.owlsoda.pageportal.features.library.UnifiedBookDisplay
import com.owlsoda.pageportal.features.library.BookCard
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedHomeScreen(
    onBookClick: (String) -> Unit,
    onNavigateToService: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBook(it) }
    }
    
    // Calculate continues reading or similar logic if available
    // For now we use recentBooks from VM
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unified Home") },
                actions = {
                    IconButton(onClick = { 
                        importLauncher.launch(arrayOf("application/epub+zip", "audio/*", "application/zip", "application/octet-stream"))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Import Local File")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
            
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
