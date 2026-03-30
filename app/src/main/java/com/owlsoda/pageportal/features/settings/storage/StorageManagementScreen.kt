package com.owlsoda.pageportal.features.settings.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.owlsoda.pageportal.ui.theme.PagePortalPurple
import com.owlsoda.pageportal.ui.theme.PagePortalTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: StorageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Storage Manager",
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Storage Summary
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = PagePortalPurple.copy(alpha = 0.08f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Used Storage",
                                style = MaterialTheme.typography.labelSmall,
                                color = PagePortalPurple
                            )
                            Text(
                                text = uiState.formattedTotalSize,
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.SdStorage,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = PagePortalPurple
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Breakdown Progress Bar
                    val total = uiState.totalSizeBytes.toFloat().coerceAtLeast(1f)
                    val audioRatio = uiState.audioSizeBytes / total
                    val ebookRatio = uiState.ebookSizeBytes / total
                    val cacheRatio = uiState.cacheSizeBytes / total
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        if (audioRatio > 0) {
                            Box(modifier = Modifier.weight(audioRatio).fillMaxHeight().background(PagePortalPurple))
                        }
                        if (ebookRatio > 0) {
                            Box(modifier = Modifier.weight(ebookRatio).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
                        }
                        if (cacheRatio > 0) {
                            Box(modifier = Modifier.weight(cacheRatio).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
                        }
                        val remaining = 1f - audioRatio - ebookRatio - cacheRatio
                        if (remaining > 0f) {
                            Box(modifier = Modifier.weight(remaining).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendItem("Audio", uiState.formattedAudioSize, PagePortalPurple)
                        LegendItem("Books", uiState.formattedEbookSize, MaterialTheme.colorScheme.secondary)
                        LegendItem("Cache", uiState.formattedCacheSize, MaterialTheme.colorScheme.tertiary)
                    }
                    
                    if (uiState.cacheSizeBytes > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.clearReadAloudCache() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text("Clear Unread Caches")
                        }
                    }
                }
            }

            Text(
                text = "Downloaded Items",
                style = MaterialTheme.typography.titleSmall,
                color = PagePortalPurple,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (uiState.isLoading && uiState.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No downloads found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(uiState.items, key = { it.bookId }) { item ->
                        StorageItemRow(
                            item = item,
                            onDelete = { viewModel.deleteItem(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StorageItemRow(
    item: StorageItem,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = "${item.author} • ${item.formattedSize}",
                style = MaterialTheme.typography.bodySmall,
                color = PagePortalTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            if (item.coverUrl != null) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                )
            }
        }
    )
    HorizontalDivider()
}

@Composable
fun LegendItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        }
    }
}
