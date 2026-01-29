package com.owlsoda.pageportal.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
            }

            item {
                SettingsSectionHeader("Appearance")
                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = "Theme",
                    subtitle = "System Default",
                    onClick = { /* TODO */ }
                )
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
