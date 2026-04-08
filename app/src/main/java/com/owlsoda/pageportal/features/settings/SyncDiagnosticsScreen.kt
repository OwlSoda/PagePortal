package com.owlsoda.pageportal.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.core.database.dao.ProgressDao
import com.owlsoda.pageportal.core.database.entity.ProgressEntity
import com.owlsoda.pageportal.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─── ViewModel ────────────────────────────────────────────────────────────────

data class SyncDiagnosticsState(
    val unsyncedCount: Int = 0,
    val totalTracked: Int = 0,
    val conflictLog: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isForceSyncing: Boolean = false,
    val lastSyncTime: String = "Never",
    val message: String? = null
)

@HiltViewModel
class SyncDiagnosticsViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val progressDao: ProgressDao,
    private val bookDao: BookDao,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _state = MutableStateFlow(SyncDiagnosticsState())
    val state: StateFlow<SyncDiagnosticsState> = _state.asStateFlow()

    val isSyncing = syncRepository.isSyncing

    init {
        loadDiagnostics()
    }

    fun loadDiagnostics() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val allProgress = progressDao.getAllProgress()
                val unsynced = allProgress.filter { p ->
                    p.syncedAt == null || p.lastUpdated > p.syncedAt!!
                }

                val logFile = File(context.filesDir, "sync_conflicts.log")
                val conflictLines = if (logFile.exists()) {
                    logFile.readLines().takeLast(50).reversed()
                } else {
                    listOf("No conflicts recorded yet.")
                }

                val lastSync = allProgress.mapNotNull { it.syncedAt }.maxOrNull()
                val lastSyncFormatted = if (lastSync != null) {
                    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(lastSync))
                } else "Never"

                _state.update {
                    it.copy(
                        isLoading = false,
                        unsyncedCount = unsynced.size,
                        totalTracked = allProgress.size,
                        conflictLog = conflictLines,
                        lastSyncTime = lastSyncFormatted
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, message = "Error loading diagnostics: ${e.message}") }
            }
        }
    }

    fun forceSyncAll() {
        viewModelScope.launch {
            _state.update { it.copy(isForceSyncing = true, message = null) }
            val result = syncRepository.syncAll()
            _state.update {
                it.copy(
                    isForceSyncing = false,
                    message = if (result.isSuccess) "✅ Synced ${result.getOrNull()} books successfully"
                              else "❌ Sync failed: ${result.exceptionOrNull()?.message}"
                )
            }
            loadDiagnostics() // Refresh stats
        }
    }

    fun clearConflictLog() {
        viewModelScope.launch {
            try {
                File(context.filesDir, "sync_conflicts.log").delete()
                _state.update { it.copy(conflictLog = listOf("Log cleared."), message = "Conflict log cleared") }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Failed to clear log: ${e.message}") }
            }
        }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SyncDiagnosticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sync Diagnostics", fontWeight = FontWeight.Bold)
                        Text("Developer Tool", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadDiagnostics) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Status Banner ─────────────────────────────────────────────────
            state.message?.let { msg ->
                Surface(
                    color = if (msg.startsWith("✅"))
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        msg,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ── Summary Cards ─────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DiagCard(
                    label = "Unsynced",
                    value = if (state.isLoading) "…" else state.unsyncedCount.toString(),
                    icon = Icons.Default.CloudUpload,
                    tint = if (state.unsyncedCount > 0)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                DiagCard(
                    label = "Tracked Books",
                    value = if (state.isLoading) "…" else state.totalTracked.toString(),
                    icon = Icons.Default.MenuBook,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Last Sync ─────────────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Last successful sync", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.lastSyncTime, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.weight(1f))
                    // Live indicator
                    if (isSyncing.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Syncing ${isSyncing.size}…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // ── Force Sync Button ──────────────────────────────────────────────
            Button(
                onClick = viewModel::forceSyncAll,
                enabled = !state.isForceSyncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isForceSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Syncing…")
                } else {
                    Icon(Icons.Default.Sync, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Force Sync All Books Now")
                }
            }

            // ── Conflict Log ─────────────────────────────────────────────────
            HorizontalDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Conflict Log (Last 50)", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                TextButton(onClick = viewModel::clearConflictLog) {
                    Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
            Text(
                "Each line shows which device won (LOCAL = this device, REMOTE = another device)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        state.conflictLog.forEach { line ->
                            val isRemoteWin = line.contains("REMOTE won")
                            val isLocalWin = line.contains("LOCAL won")
                            Text(
                                line,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                ),
                                color = when {
                                    isRemoteWin -> MaterialTheme.colorScheme.tertiary
                                    isLocalWin -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DiagCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
