package com.owlsoda.pageportal.features.queue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueDashboardScreen(
    onBack: () -> Unit,
    viewModel: QueueDashboardViewModel = hiltViewModel()
) {
    val activeJobs by viewModel.activeJobs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Alignments") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (activeJobs.isEmpty()) {
            EmptyState(
                title = "All Clear",
                message = "No active alignment jobs",
                icon = Icons.Default.Cancel,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(activeJobs, key = { it.id }) { book ->
                    JobItem(book = book, onCancel = { viewModel.cancelJob(book.id) })
                }
            }
        }
    }
}

@Composable
private fun JobItem(
    book: BookEntity,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover
            AsyncImage(
                model = book.audiobookCoverUrl ?: book.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp, 90.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val progressVal = book.processingProgress ?: 0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { if (progressVal > 0) progressVal else 0f },
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${book.processingStage ?: book.processingStatus} ${if (progressVal > 0) "${(progressVal * 100).toInt()}%" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            
            // Cancel Action
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Cancel, "Cancel Job", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
