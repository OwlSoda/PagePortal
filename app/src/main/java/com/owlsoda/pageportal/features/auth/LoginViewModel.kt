package com.owlsoda.pageportal.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.repository.AuthRepository
import com.owlsoda.pageportal.services.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.AuthorizationException
import android.net.Uri

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val selectedService: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val oidcRequest: AuthorizationRequest? = null,
    val isServiceSelected: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    
    private val isAddingAccount: Boolean = savedStateHandle.get<Boolean>("addAccount") ?: false
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    init {
        // Check if user already has connected servers, ONLY if not adding a new account
        if (!isAddingAccount) {
            viewModelScope.launch {
                if (authRepository.hasConnectedServers()) {
                    _uiState.value = _uiState.value.copy(isLoggedIn = true)
                }
            }
        }
    }
    
    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, error = null)
    }
    
    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username, error = null)
    }
    
    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }
    
    fun selectService(index: Int) {
        _uiState.value = _uiState.value.copy(
            selectedService = index, 
            isServiceSelected = true,
            error = null,
            serverUrl = "", // Reset fields when changing service
            username = "",
            password = ""
        )
    }
    
    fun clearServiceSelection() {
        _uiState.value = _uiState.value.copy(
            isServiceSelected = false,
            error = null
        )
    }
    
    fun updateError(message: String) {
        _uiState.value = _uiState.value.copy(error = message, isLoading = false)
    }
    
    fun clearOidcRequest() {
        _uiState.value = _uiState.value.copy(oidcRequest = null)
    }
    
    fun startOidcLogin() {
        viewModelScope.launch {
            val state = _uiState.value
            val normalizedUrl = state.serverUrl.trim().trimEnd('/')
            if (normalizedUrl.isBlank()) {
                _uiState.value = state.copy(error = "Please enter a server URL")
                return@launch
            }
            
            _uiState.value = state.copy(isLoading = true, error = null)
            
            try {
                // Fetch config from the issuer
                val config = OidcHelper.fetchConfiguration(Uri.parse(normalizedUrl))
                
                // Build the authorization request
                val request = AuthorizationRequest.Builder(
                    config,
                    "pageportal",
                    ResponseTypeValues.CODE,
                    Uri.parse("pageportal://oauth2redirect")
                )
                .setScope("openid profile offline_access")
                .build()
                
                _uiState.value = state.copy(oidcRequest = request, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    error = "Failed to discover OIDC provider: ${e.message}"
                )
                e.printStackTrace()
            }
        }
    }
    
    fun exchangeToken(authService: AuthorizationService, resp: AuthorizationResponse) {
        viewModelScope.launch {
            val state = _uiState.value
            val normalizedUrl = state.serverUrl.trim().trimEnd('/')
            _uiState.value = state.copy(isLoading = true, error = null)
            
            authService.performTokenRequest(resp.createTokenExchangeRequest()) { response, ex ->
                viewModelScope.launch {
                    if (response != null && response.accessToken != null) {
                        try {
                            val result = authRepository.login(
                                serviceType = ServiceType.BOOKLORE,
                                serverUrl = normalizedUrl,
                                username = "OIDC User", // or extract from ID token
                                password = response.accessToken!!
                            )
                            
                            result.fold(
                                onSuccess = {
                                    _uiState.value = _uiState.value.copy(
                                        isLoading = false,
                                        isLoggedIn = true
                                    )
                                },
                                onFailure = { error ->
                                    _uiState.value = _uiState.value.copy(
                                        isLoading = false,
                                        error = error.message ?: "Authentication failed"
                                    )
                                }
                            )
                        } catch (e: Exception) {
                            _uiState.value = _uiState.value.copy(isLoading = false, error = "Login failed: ${e.message}")
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = ex?.message ?: "Token exchange failed"
                        )
                    }
                }
            }
        }
    }
    
    fun login(serviceIndex: Int) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                
                if (state.serverUrl.isBlank()) {
                    _uiState.value = state.copy(error = "Please enter a server URL")
                    return@launch
                }
                if (state.username.isBlank()) {
                    _uiState.value = state.copy(error = "Please enter a username")
                    return@launch
                }
                if (state.password.isBlank()) {
                    _uiState.value = state.copy(error = "Please enter a password")
                    return@launch
                }
                
                val serviceType = when (serviceIndex) {
                    0 -> ServiceType.STORYTELLER
                    1 -> ServiceType.AUDIOBOOKSHELF
                    2 -> ServiceType.BOOKLORE
                    else -> ServiceType.STORYTELLER
                }
                
                // Normalize URL (remove trailing slashes)
                val normalizedUrl = state.serverUrl.trim().trimEnd('/')
                
                _uiState.value = state.copy(isLoading = true, error = null)
                
                try {
                    val result = authRepository.login(
                        serviceType = serviceType,
                        serverUrl = normalizedUrl,
                        username = state.username,
                        password = state.password
                    )
                    
                    result.fold(
                        onSuccess = {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isLoggedIn = true
                            )
                        },
                        onFailure = { error ->
                            val errorMessage = when {
                                error.message?.contains("timeout", ignoreCase = true) == true ->
                                    "Connection timeout. Please check your server URL and network connection."
                                error.message?.contains("refused", ignoreCase = true) == true ->
                                    "Connection refused. Is the server running?"
                                error.message?.contains("host", ignoreCase = true) == true ->
                                    "Cannot reach server. Please check the URL."
                                else -> error.message ?: "Login failed. Please check your credentials."
                            }
                            
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = errorMessage
                            )
                        }
                    )
                } catch (e: Exception) {
                    // Catch any unexpected exceptions to prevent crashes
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Unexpected error: ${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}"
                    )
                    e.printStackTrace()
                }
            } catch (e: Throwable) {
                // Ultimate catch-all to prevent ANY crash
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "CRITICAL ERROR: ${e.javaClass.name}: ${e.message}\nStack: ${e.stackTraceToString().take(200)}"
                )
                e.printStackTrace()
            }
        }
    }
}
