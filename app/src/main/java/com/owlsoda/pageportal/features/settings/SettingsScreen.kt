package com.owlsoda.pageportal.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onServersClick: () -> Unit,
    onMatchReviewClick: () -> Unit,
    onStorageClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SettingsSectionHeader("Library")
                SettingsItem(
                    icon = Icons.Default.Dns,
                    title = "Manage Servers",
                    subtitle = "Connect specific services",
                    onClick = onServersClick
                )
                SettingsItem(
                    icon = Icons.Default.Merge,
                    title = "Match Review",
                    subtitle = "Fix incorrect book matches",
                    onClick = onMatchReviewClick
                )
            }
            
            item {
                SettingsSectionHeader("Offline")
                SwitchSettingsItem(
                    icon = Icons.Default.WifiOff,
                    title = "Offline Mode",
                    subtitle = "Disable network access",
                    checked = state.isOfflineMode,
                    onCheckedChange = { viewModel.toggleOfflineMode(it) }
                )
                SettingsItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "Clear Cache",
                    subtitle = "${state.cacheSize} used",
                    showChevron = false,
                    onClick = { viewModel.clearCache() }
                )
                SettingsItem(
                    icon = Icons.Default.SdStorage,
                    title = "Storage Management",
                    subtitle = "Manage downloads",
                    onClick = onStorageClick
                )
            }
            
            item {
                SettingsSectionHeader("Playback")
                
                // Playback Speed
                var showSpeedDialog by remember { mutableStateOf(false) }
                SettingsItem(
                    icon = Icons.Default.Speed,
                    title = "Playback Speed",
                    subtitle = String.format("%.2fx", state.playbackSpeed),
                    onClick = { showSpeedDialog = true }
                )
                
                if (showSpeedDialog) {
                    AlertDialog(
                        onDismissRequest = { showSpeedDialog = false },
                        title = { Text("Playback Speed") },
                        text = {
                            Column {
                                Text(
                                    text = String.format("%.2fx", state.playbackSpeed), 
                                    style = MaterialTheme.typography.headlineMedium, 
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                                Slider(
                                    value = state.playbackSpeed,
                                    onValueChange = { viewModel.setPlaybackSpeed(it) },
                                    valueRange = 0.5f..3.0f,
                                    steps = 49 // (3.0 - 0.5) / 0.05 - 1 = 49 steps
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showSpeedDialog = false }) {
                                Text("Done")
                            }
                        }
                    )
                }

                // Grid Size
                var showGridDialog by remember { mutableStateOf(false) }
                SettingsItem(
                    icon = Icons.Default.GridView,
                    title = "Grid Item Size",
                    subtitle = when {
                        state.gridMinWidth < 110 -> "Small"
                        state.gridMinWidth < 140 -> "Normal"
                        state.gridMinWidth < 180 -> "Large"
                        else -> "Extra Large"
                    },
                    onClick = { showGridDialog = true }
                )

                if (showGridDialog) {
                    AlertDialog(
                        onDismissRequest = { showGridDialog = false },
                        title = { Text("Grid Item Size") },
                        text = {
                            Column {
                                Text(
                                    text = "${state.gridMinWidth}dp",
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                                Text(
                                    text = "Larger items = fewer columns",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
                                )
                                Slider(
                                    value = state.gridMinWidth.toFloat(),
                                    onValueChange = { viewModel.setGridMinWidth(it.toInt()) },
                                    valueRange = 100f..300f,
                                    steps = 19 
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showGridDialog = false }) {
                                Text("Done")
                            }
                        }
                    )
                }
                
                // Sleep Timer
                var showTimerDialog by remember { mutableStateOf(false) }
                val timerOptions = listOf(0, 15, 30, 45, 60, 90, 120)
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = "Default Sleep Timer",
                    subtitle = if (state.sleepTimerMinutes == 0) "Disabled" else "${state.sleepTimerMinutes} minutes",
                    onClick = { showTimerDialog = true }
                )
                
                if (showTimerDialog) {
                    AlertDialog(
                        onDismissRequest = { showTimerDialog = false },
                        title = { Text("Default Sleep Timer") },
                        text = {
                            Column {
                                timerOptions.forEach { minutes ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.setSleepTimerMinutes(minutes)
                                                showTimerDialog = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(if (minutes == 0) "Disabled" else "$minutes minutes")
                                        RadioButton(
                                            selected = state.sleepTimerMinutes == minutes,
                                            onClick = {
                                                viewModel.setSleepTimerMinutes(minutes)
                                                showTimerDialog = false
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showTimerDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }

            item {
                SettingsSectionHeader("Appearance")
                
                var showThemeDialog by remember { mutableStateOf(false) }
                
                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = "Theme",
                    subtitle = when (state.themeMode) {
                        "LIGHT" -> "Light"
                        "DARK" -> "Dark"
                        else -> "System Default"
                    },
                    onClick = { showThemeDialog = true }
                )
                
                if (showThemeDialog) {
                    AlertDialog(
                        onDismissRequest = { showThemeDialog = false },
                        title = { Text("Choose Theme") },
                        text = {
                            Column {
                                ThemeOption(
                                    title = "System Default",
                                    selected = state.themeMode == "SYSTEM",
                                    onClick = {
                                        viewModel.updateTheme("SYSTEM")
                                        showThemeDialog = false
                                    }
                                )
                                ThemeOption(
                                    title = "Light",
                                    selected = state.themeMode == "LIGHT",
                                    onClick = {
                                        viewModel.updateTheme("LIGHT")
                                        showThemeDialog = false
                                    }
                                )
                                ThemeOption(
                                    title = "Dark",
                                    selected = state.themeMode == "DARK",
                                    onClick = {
                                        viewModel.updateTheme("DARK")
                                        showThemeDialog = false
                                    }
                                )
                                ThemeOption(
                                    title = "AMOLED Black",
                                    selected = state.themeMode == "AMOLED",
                                    onClick = {
                                        viewModel.updateTheme("AMOLED")
                                        showThemeDialog = false
                                    }
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showThemeDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }

            item {
                SettingsSectionHeader("About")
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0.0 (Alpha)",
                    showChevron = false,
                    onClick = { }
                )
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { 
            Icon(
                imageVector = icon, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
        trailingContent = if (showChevron) {
            { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) }
        } else null,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SwitchSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { 
            Icon(
                imageVector = icon, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable(onClick = { onCheckedChange(!checked) })
    )
}

@Composable
fun ThemeOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title)
        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}
