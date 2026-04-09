package com.owlsoda.pageportal.features.book

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.owlsoda.pageportal.core.database.entity.BookEntity
import com.owlsoda.pageportal.core.extensions.parseAuthors
import com.owlsoda.pageportal.core.extensions.parseTags
import com.owlsoda.pageportal.ui.components.EmptyState
import com.owlsoda.pageportal.ui.theme.PagePortalPurple
import com.owlsoda.pageportal.ui.util.rememberCoverColors
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.owlsoda.pageportal.ui.util.animatedCoverColor
import com.owlsoda.pageportal.ui.components.SkeletonBookDetail

@Composable
fun BookEntity.serverName(): String {
    // A quick hack for the UI: check if service is storyteller
    val ctx = LocalContext.current
    return "Storyteller" // Storyteller has read aloud mostly
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    onBack: () -> Unit,
    onPlayAudiobook: (String) -> Unit,
    onReadEbook: (String) -> Unit,

    onPlayReadAloud: (String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onOpenWebReader: (String) -> Unit,
    onEditClick: (Long) -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    
    val sharedScope = com.owlsoda.pageportal.ui.theme.LocalSharedTransitionScope.current
    val animatedScope = com.owlsoda.pageportal.ui.theme.LocalNavAnimatedVisibilityScope.current
    
    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }
    
    // Dynamic cover theming — extract colors from cover art
    val coverUrl = state.book?.audiobookCoverUrl ?: state.book?.coverUrl
    val coverColors = rememberCoverColors(coverUrl)
    val animatedOverlayColor = animatedCoverColor(
        targetColor = coverColors?.darkMuted?.copy(alpha = 0.65f),
        fallback = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    )
    val animatedPrimary = animatedCoverColor(
        targetColor = coverColors?.vibrant,
        fallback = PagePortalPurple
    )
    val animatedPrimaryContainer = animatedCoverColor(
        targetColor = coverColors?.vibrant?.copy(alpha = 0.15f),
        fallback = MaterialTheme.colorScheme.primaryContainer
    )
    
    val topBarState = com.owlsoda.pageportal.navigation.LocalTopBarState.current
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.book) {
        topBarState.title = ""
        topBarState.actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, "More")
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit Metadata") },
                    onClick = {
                        state.book?.id?.let { onEditClick(it) }
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                )
                DropdownMenuItem(
                    text = { Text("Unlink Book") },
                    onClick = {
                        viewModel.unlinkBook()
                        showMenu = false
                        onBack()
                    },
                    leadingIcon = { Icon(Icons.Default.LinkOff, null) }
                )
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
            // Background Artwork (Fixed Full-Screen Blur)
            state.book?.coverUrl?.let { coverUrl ->
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(60.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                    contentScale = ContentScale.Crop,
                    alpha = 0.25f
                )
                // Dynamic overlay — uses the cover's dominant dark color for a cohesive feel
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(animatedOverlayColor)
                )
            }
            
            // Show skeleton while book is loading
            if (state.book == null) {
                SkeletonBookDetail()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(64.dp)) // Standard TopBar height approximate
                
                state.book?.let { book ->
                    // Hero Content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Cover Image
                        val coverUrl = state.book?.audiobookCoverUrl ?: state.book?.coverUrl
                        val aspectRatio = if (state.book?.audiobookCoverUrl != null) 1f else 2f / 3f
                        
                        var surfaceModifier: Modifier = Modifier
                            .width(if (aspectRatio == 1f) 260.dp else 220.dp)
                            .aspectRatio(aspectRatio)

                        if (sharedScope != null && animatedScope != null) {
                            with(sharedScope) {
                                surfaceModifier = surfaceModifier.sharedElement(
                                    state = rememberSharedContentState(key = "cover_${book.id}"),
                                    animatedVisibilityScope = animatedScope
                                )
                            }
                        }
                        
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            shadowElevation = 32.dp,
                            modifier = surfaceModifier
                        ) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = state.book?.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Title & Author
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (book.title.length > 30) 24.sp else 28.sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            lineHeight = 32.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Authors (Multiple supported)
                        val authorList = remember(book.authors) {
                            book.authors.parseAuthors()
                        }
                        
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            authorList.forEach { author ->
                                Text(
                                    text = author,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PagePortalPurple,
                                    modifier = Modifier
                                        .clickable { onAuthorClick(author) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        
                        if (!book.series.isNullOrBlank()) {
                            val seriesLabel = book.seriesIndex?.let { index ->
                                val floatIndex = index.toFloatOrNull()
                                if (floatIndex != null) {
                                    val s = if (floatIndex % 1 == 0f) floatIndex.toInt().toString() else floatIndex.toString()
                                    " #$s"
                                } else " #$index"
                            } ?: ""
                            
                            Surface(
                                onClick = { onSeriesClick(book.series) },
                                color = Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${book.series}$seriesLabel",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Progress Line
                        if (state.progressPercent > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = { state.progressPercent / 100f },
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                            
                            // Last Sync Info
                            val isSyncingMap by viewModel.isSyncing.collectAsState()
                            val isSyncing = isSyncingMap[book.id] == true
                            
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Syncing...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Icon(Icons.Default.Sync, null, modifier = Modifier.size(12.dp).clickable { viewModel.syncNow() }, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = state.lastSyncAt?.let { "Synced ${formatLastSync(it)}" } ?: "Not synced",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Primary Actions (FlowRow for responsiveness)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Listen Button
                            if (book.hasAudiobook) {
                                MainActionButton(
                                    text = "Listen",
                                    icon = Icons.Default.Headphones,
                                    onClick = { onPlayAudiobook(book.id.toString()) },
                                    modifier = Modifier.weight(1f, fill = false).widthIn(min = 100.dp),
                                    containerColor = animatedPrimary,
                                    contentColor = Color.White
                                )
                            }

                            // Read Button
                            if (book.hasEbook) {
                                MainActionButton(
                                    text = "Read",
                                    icon = Icons.AutoMirrored.Filled.MenuBook,
                                    onClick = { onReadEbook(book.id.toString()) },
                                    enabled = book.isEbookDownloaded,
                                    modifier = Modifier.weight(1f, fill = false).widthIn(min = 100.dp),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            
                            // Sync Button (ReadAloud specific)
                            if (book.hasReadAloud) {
                                MainActionButton(
                                    text = "ReadAloud",
                                    icon = Icons.Default.AutoFixHigh,
                                    onClick = { onPlayReadAloud(book.id.toString()) },
                                    enabled = book.isReadAloudDownloaded,
                                    modifier = Modifier.weight(1f, fill = false).widthIn(min = 100.dp),
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }

                        if (state.webReaderUrl != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            MainActionButton(
                                text = "Use Web Reader (Beta)",
                                icon = Icons.Default.OpenInBrowser,
                                onClick = { onOpenWebReader(state.webReaderUrl!!) },
                                modifier = Modifier.fillMaxWidth(),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Secondary Actions & Status
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.isDownloading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (state.downloadProgress < 0) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 3.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Downloading...", style = MaterialTheme.typography.labelMedium)
                                    } else {
                                        CircularProgressIndicator(
                                            progress = { state.downloadProgress },
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 3.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Downloading... ${(state.downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                                    }
                                    TextButton(onClick = { viewModel.cancelDownload() }) {
                                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            
                            // Download Error Display
                            val downloadError = state.downloadError
                            if (downloadError != null) {
                                androidx.compose.material3.Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = "Error",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            downloadError,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            
                            if (!state.isDownloading) {
                                // Audio Download Status
                                if (book.hasAudiobook) {
                                    if (!book.isAudiobookDownloaded) {
                                        OutlinedButton(
                                            onClick = { viewModel.startDownload("audio") },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Download Audio")
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                    }
                                }
                                
                                // Alignment Processing Status
                                if (state.alignmentStatus != null && state.alignmentStatus != "COMPLETED" && state.alignmentStatus != "READY" && state.alignmentStatus != "ALIGNED") {
                                    val progressVal = state.alignmentProgress ?: 0f
                                    Row(
                                        modifier = Modifier.padding(top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { if (progressVal > 0) progressVal else 0.0f },
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Aligning: ${state.alignmentStage ?: state.alignmentStatus} ${if (progressVal > 0) "${(progressVal * 100).toInt()}%" else ""}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(onClick = { viewModel.cancelAlignment() }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Cancel, "Cancel", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                } else if (!book.hasReadAloud && book.serverName() == "Storyteller") {
                                     // Alignment Trigger Button
                                    TextButton(onClick = { viewModel.triggerReadAloudAlignment() }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Generate Read-Aloud (Turbo Supported)")
                                    }
                                }
                                
                                // Ebook Download Status
                                if (book.hasEbook) {
                                    if (!book.isEbookDownloaded) {
                                        OutlinedButton(
                                            onClick = { viewModel.startDownload("ebook") },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Book, null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Download Ebook")
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Ebook Ready", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            IconButton(onClick = { viewModel.deleteDownload("ebook") }) {
                                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }

                                 // ReadAloud / Sync Download Status
                                if (book.hasReadAloud) {
                                    if (!book.isReadAloudDownloaded) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { viewModel.startDownload("readaloud") },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                                            ) {
                                                Icon(Icons.Default.Sync, null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Download ReadAloud")
                                            }
                                            
                                            TextButton(
                                                onClick = { viewModel.triggerReadAloudCreation(restart = true) },
                                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                                            ) {
                                                Text("Force Re-sync", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                            }
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("ReadAloud Ready", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                                            IconButton(onClick = { viewModel.deleteDownload("readaloud") }) {
                                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                } else if (book.hasEbook && book.hasAudiobook) {
                                    // Option to create ReadAloud
                                    val isSyncing = book.processingStatus?.lowercase() in listOf("processing", "queued", "transcribing", "aligning")
                                    if (isSyncing) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            val stage = (book.processingStage ?: book.processingStatus ?: "Syncing").replaceFirstChar { it.uppercase() }
                                            val progress = book.processingProgress ?: 0f
                                            
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text("$stage...", style = MaterialTheme.typography.labelMedium)
                                            }
                                            
                                            if (progress > 0f) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    modifier = Modifier.fillMaxWidth(0.8f).height(4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                                                    color = PagePortalPurple,
                                                    trackColor = PagePortalPurple.copy(alpha = 0.1f)
                                                )
                                                Text(
                                                    "${(progress * 100).toInt()}%", 
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(top = 4.dp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { viewModel.triggerReadAloudCreation() },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PagePortalPurple)
                                        ) {
                                            Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Create ReadAloud Sync")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Metadata Row
                    BookMetadataRow(book)

                    // Description Section
                    if (!book.description.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Text(
                                "About this book",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = book.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = TextUnit(24f, TextUnitType.Sp)
                            )
                        }
                    }
                    
                    // Tags Section
                    val tagList = remember(book.tags) {
                        book.tags.parseTags()
                    }
                    
                    if (tagList.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Tags",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                tagList.forEach { tag ->
                                    TagChip(tag = tag, onClick = { onTagClick(tag) })
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        }
    }
}

@Composable
fun MainActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color,
    contentColor: Color
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text, 
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp // Slightly smaller for better fit
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BookMetadataRow(book: BookEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        MetadataItem(
            label = "PUBLISHED",
            value = book.publishedYear?.toString() ?: "—"
        )
        VerticalDivider(modifier = Modifier.height(24.dp).align(Alignment.CenterVertically), alpha = 0.2f)
        MetadataItem(
            label = "DURATION",
            value = book.duration?.let { formatDuration(it) } ?: "—"
        )
        if (book.hasReadAloud) {
            VerticalDivider(modifier = Modifier.height(24.dp).align(Alignment.CenterVertically), alpha = 0.2f)
            MetadataItem(
                label = "TYPE",
                value = "ReadAloud"
            )
        }
    }
}

@Composable
fun MetadataItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun VerticalDivider(modifier: Modifier, alpha: Float) {
    Box(modifier = modifier.width(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)))
}

@Composable
fun TagChip(tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "Unknown"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatLastSync(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60000 -> "just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> "${diff / 86400000}d ago"
    }
}
