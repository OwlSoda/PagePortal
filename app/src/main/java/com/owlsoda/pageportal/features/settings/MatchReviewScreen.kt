package com.owlsoda.pageportal.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.owlsoda.pageportal.core.extensions.parseAuthors
import com.owlsoda.pageportal.core.database.entity.BookEntity
import coil.compose.AsyncImage
import com.owlsoda.pageportal.ui.theme.PagePortalPurple
import com.owlsoda.pageportal.ui.theme.PagePortalTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchReviewScreen(
    onBack: () -> Unit,
    viewModel: MatchReviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Review Matches",
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.candidates.isNotEmpty() -> {
                    val candidate = state.candidates.first()
                    MatchCard(
                        candidate = candidate,
                        onConfirm = { viewModel.confirmMatch(candidate) },
                        onReject = { viewModel.rejectMatch(candidate) }
                    )
                }
                else -> {
                    Text(
                        "No more candidates to review!",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun MatchCard(
    candidate: MatchCandidate,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Unlinked Book (Top)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = candidate.book.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .width(100.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "New Book", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = PagePortalPurple
                        )
                        Text(
                            candidate.book.title, 
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            candidate.book.authors, 
                            style = MaterialTheme.typography.bodyMedium,
                            color = PagePortalTextSecondary
                        )
                    }
                }
            }
            
            Divider()
            
            // Potential Match (Bottom)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = candidate.potentialMatch.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .width(100.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Possible Match", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = PagePortalTextSecondary
                        )
                        Text(
                            text = candidate.potentialMatch.title, 
                            style = MaterialTheme.typography.titleMedium
                        )
                        val authors = remember(candidate.potentialMatch.authors) {
                            candidate.potentialMatch.authors.parseAuthors()
                        }
                        Text(
                            text = "Author: ${authors.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PagePortalTextSecondary
                        )
                        
                        Text(
                            text = "Similarity: ${(candidate.score * 100).toInt()}%", 
                            style = MaterialTheme.typography.labelSmall,
                            color = PagePortalPurple,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            
            // Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = onReject,
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                ) {
                    Icon(Icons.Default.Close, "Reject match", tint = MaterialTheme.colorScheme.onErrorContainer)
                }
                
                IconButton(
                    onClick = onConfirm,
                    modifier = Modifier
                        .size(56.dp)
                        .background(PagePortalPurple, CircleShape)
                ) {
                    Icon(Icons.Default.Check, "Confirm match", tint = Color.White)
                }
            }
        }
    }
}
