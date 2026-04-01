package com.owlsoda.pageportal.features.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import com.owlsoda.pageportal.ui.theme.PagePortalPurple
import com.owlsoda.pageportal.ui.theme.PagePortalTextSecondary

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onManageServers: () -> Unit = {},
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
    
    val context = LocalContext.current
    val authService = remember { AuthorizationService(context) }
    
    
    
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
            modifier = Modifier.size(80.dp),
            tint = PagePortalPurple
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "PagePortal",
            style = MaterialTheme.typography.displayLarge
        )
        
        Text(
            text = "Your unified library",
            style = MaterialTheme.typography.bodyLarge,
            color = PagePortalTextSecondary
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        AnimatedContent(
            targetState = uiState.isServiceSelected,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "Login Flow Transition"
        ) { isSelected ->
            if (!isSelected) {
                // STEP 1: Service Selection
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Select a service to connect:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    ServiceSelectionCard(
                        title = "Storyteller",
                        description = "Connect to your Storyteller instance",
                        onClick = { viewModel.selectService(0) }
                    )
                    ServiceSelectionCard(
                        title = "Audiobookshelf",
                        description = "Connect to your Audiobookshelf server",
                        onClick = { viewModel.selectService(1) }
                    )
                }
            } else {
                // STEP 2: Service Form
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        // Back Button & Title Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = { viewModel.clearServiceSelection() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to selection",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            val serviceName = when (uiState.selectedService) {
                                0 -> "Storyteller"
                                else -> "Audiobookshelf"
                            }
                            Text(
                                text = "Connect to $serviceName",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Server URL with Scheme Toggle
                        OutlinedTextField(
                            value = uiState.serverUrl,
                            onValueChange = { viewModel.updateServerUrl(it) },
                            label = { Text("Server URL") },
                            placeholder = { 
                                Text(
                                    when (uiState.selectedService) {
                                        0 -> "storyteller.example.com"
                                        else -> "abs.example.com"
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            ),
                            leadingIcon = {
                                Surface(
                                    onClick = { viewModel.toggleScheme() },
                                    color = if (uiState.useHttps) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = if (uiState.useHttps) "HTTPS" else "HTTP",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = if (uiState.useHttps) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                        
                        // No-op for Booklore removal
                        
                        AnimatedVisibility(visible = true) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                OutlinedTextField(
                                    value = uiState.username,
                                    onValueChange = { viewModel.updateUsername(it) },
                                    label = { Text("Username") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                var passwordVisible by remember { mutableStateOf(false) }
                                OutlinedTextField(
                                    value = uiState.password,
                                    onValueChange = { viewModel.updatePassword(it) },
                                    label = { Text("Password") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (!uiState.isLoading && uiState.serverUrl.isNotBlank() && uiState.username.isNotBlank() && uiState.password.isNotBlank()) {
                                                viewModel.login(uiState.selectedService)
                                            }
                                        }
                                    ),
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        
                        // Error message
                        AnimatedVisibility(visible = uiState.error != null) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = uiState.error ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Login button
                        Button(
                            onClick = { 
                                viewModel.login(uiState.selectedService)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = !uiState.isLoading && 
                                      uiState.serverUrl.isNotBlank() && 
                                      uiState.username.isNotBlank() && uiState.password.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PagePortalPurple,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(
                                text = "Connect",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }


                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Manage servers link
                TextButton(onClick = onManageServers) {
                    Text("Manage connected servers")
                }
            }
        }
    }
}

@Composable
fun ServiceSelectionCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = PagePortalPurple
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = PagePortalTextSecondary
            )
        }
    }
}
