package com.owlsoda.pageportal.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.owlsoda.pageportal.core.database.entity.ServerEntity
import com.owlsoda.pageportal.services.ServiceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerManagementScreen(
    onBack: () -> Unit,
    onAddServer: () -> Unit,
    onTestServer: (Long) -> Unit,
    viewModel: ServerManagementViewModel = hiltViewModel()
) {
    val servers by viewModel.servers.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Servers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddServer) {
                Icon(Icons.Default.Add, contentDescription = "Add Server")
            }
        }
    ) { padding ->
        if (servers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No servers connected",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(servers) { server ->
                    ServerItem(
                        server = server,
                        onDelete = { viewModel.deleteServer(server) },
                        onTest = { onTestServer(server.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ServerItem(
    server: ServerEntity,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Remove Server?") },
            text = { Text("This will remove all downloaded content associated with this server.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ListItem(
        headlineContent = { Text(server.displayName.ifBlank { "Unknown Server" }) },
        supportingContent = { 
            Column {
                Text(server.serviceType)
                Text(server.serverUrl, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onTest) {
                    Icon(
                        Icons.Default.Info, // Or Build/Settings
                        contentDescription = "Run Diagnostics",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { showDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
    Divider()
}
