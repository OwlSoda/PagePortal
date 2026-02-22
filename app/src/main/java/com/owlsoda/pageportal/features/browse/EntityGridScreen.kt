package com.owlsoda.pageportal.features.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

import com.owlsoda.pageportal.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityGridScreen(
    currentType: String, // "AUTHORS" or "SERIES"
    onBack: () -> Unit,
    onEntityClick: (String, String) -> Unit, // type, id/name
    viewModel: EntityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Convert string type to enum if needed, or just rely on manual setup.
    // Ideally we pass filter type in route, but for now we set it once
    // A launched effect could set the type based on arguments, but ViewModel init default is Authors
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when(uiState.selectedType) {
                            EntityType.Authors -> "Authors"
                            EntityType.Series -> "Series"
                            EntityType.Collections -> "Collections"
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading && uiState.items.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.items.isEmpty()) {
                val (icon, title, message) = when(uiState.selectedType) {
                    EntityType.Authors -> Triple(Icons.Filled.Person, "No Authors Found", "Connect a service or refresh to find authors.")
                    EntityType.Series -> Triple(Icons.Filled.Folder, "No Series Found", "Connect a service or refresh to find series.")
                    EntityType.Collections -> Triple(Icons.Filled.CollectionsBookmark, "No Collections Found", "Create collections or sync with your server.")
                }
                
                EmptyState(
                    icon = icon,
                    title = title,
                    message = message
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        EntityGridItem(
                            item = item,
                            onClick = { 
                                // Pass the type and the ID (name) back
                                val typeStr = when(uiState.selectedType) {
                                    EntityType.Authors -> "AUTHOR"
                                    EntityType.Series -> "SERIES"
                                    EntityType.Collections -> "COLLECTION"
                                }
                                onEntityClick(typeStr, item.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EntityGridItem(
    item: EntityItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar / Cover
        if (item.coverUrl != null) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .size(100.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (item.count == 1) "1 book" else "${item.count} books",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
