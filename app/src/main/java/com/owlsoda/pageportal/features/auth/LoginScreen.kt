package com.owlsoda.pageportal.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            try {
                onLoginSuccess()
            } catch (e: Exception) {
                // If navigation fails, log it
                e.printStackTrace()
                // Reset login state so user can try again
                viewModel.updateError("Navigation failed: ${e.message}")
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo and title
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = "PagePortal Logo",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "PagePortal",
            style = MaterialTheme.typography.headlineLarge
        )
        
        Text(
            text = "Your unified library",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Service selector
        val services = listOf("Storyteller", "Audiobookshelf", "Booklore")
        
        TabRow(selectedTabIndex = uiState.selectedService) {
            services.forEachIndexed { index, title ->
                Tab(
                    selected = uiState.selectedService == index,
                    onClick = { viewModel.selectService(index) },
                    text = { Text(text = title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Server URL
        OutlinedTextField(
            value = uiState.serverUrl,
            onValueChange = { viewModel.updateServerUrl(it) },
            label = { Text("Server URL") },
            placeholder = { 
                Text(
                    when (uiState.selectedService) {
                        0 -> "https://storyteller.example.com"
                        1 -> "https://abs.example.com"
                        else -> "https://booklore.example.com"
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Username
        OutlinedTextField(
            value = uiState.username,
            onValueChange = { viewModel.updateUsername(it) },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Password
        var passwordVisible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = uiState.password,
            onValueChange = { viewModel.updatePassword(it) },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )
        
        // Error message
        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Login button
        Button(
            onClick = { viewModel.login(uiState.selectedService) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading && 
                      uiState.serverUrl.isNotBlank() && 
                      uiState.username.isNotBlank() && 
                      uiState.password.isNotBlank()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Connect")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Manage servers link
        TextButton(onClick = { /* TODO: Navigate to server management */ }) {
            Text("Manage connected servers")
        }
    }
}
