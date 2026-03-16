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
import com.owlsoda.pageportal.services.audiobookshelf.AudiobookshelfService
import com.owlsoda.pageportal.services.storyteller.StorytellerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LoginUiState(
    val serverUrl: String = "",
    val useHttps: Boolean = true,
    val username: String = "",
    val password: String = "",
    val selectedService: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val oidcRequest: AuthorizationRequest? = null,
    val isServiceSelected: Boolean = false,
    val isAbsSsoAvailable: Boolean = false,
    val browserUrl: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val oauthManager: OAuthManager,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    
    private val isAddingAccount: Boolean = savedStateHandle.get<Boolean>("addAccount") ?: false
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    // PKCE State for Audiobookshelf SSO
    private var absPkceVerifier: String? = null
    private var absPkceState: String? = null
    
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
        var cleanUrl = url.trim()
        var useHttps = _uiState.value.useHttps
        
        // If user pastes a full URL, extract the scheme and the rest
        if (cleanUrl.contains("://")) {
            useHttps = cleanUrl.startsWith("https://", ignoreCase = true)
            cleanUrl = cleanUrl.substringAfter("://")
        }
        
        _uiState.value = _uiState.value.copy(
            serverUrl = cleanUrl, 
            useHttps = useHttps,
            error = null
        )
        
        val fullUrl = if (useHttps) "https://$cleanUrl" else "http://$cleanUrl"
        
        // Debounced or direct check for ABS SSO
        if (_uiState.value.selectedService == 1 && cleanUrl.isNotBlank()) {
            viewModelScope.launch {
                val isSsoAvailable = authRepository.checkAbsSso(fullUrl)
                _uiState.value = _uiState.value.copy(isAbsSsoAvailable = isSsoAvailable)
            }
        }
    }

    fun toggleScheme() {
        val newUseHttps = !_uiState.value.useHttps
        _uiState.value = _uiState.value.copy(useHttps = newUseHttps)
        
        // Re-check SSO if needed
        val cleanUrl = _uiState.value.serverUrl
        if (_uiState.value.selectedService == 1 && cleanUrl.isNotBlank()) {
            val fullUrl = if (newUseHttps) "https://$cleanUrl" else "http://$cleanUrl"
            viewModelScope.launch {
                val isSsoAvailable = authRepository.checkAbsSso(fullUrl)
                _uiState.value = _uiState.value.copy(isAbsSsoAvailable = isSsoAvailable)
            }
        }
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
            isAbsSsoAvailable = false, // Reset SSO availability when changing service
            serverUrl = "", // Reset fields when changing service
            username = "",
            password = ""
        )
        
        // Check for SSO if ABS is selected and URL is present
        if (index == 1 && _uiState.value.serverUrl.startsWith("http")) {
            viewModelScope.launch {
                val isSsoAvailable = authRepository.checkAbsSso(_uiState.value.serverUrl)
                _uiState.value = _uiState.value.copy(isAbsSsoAvailable = isSsoAvailable)
            }
        }
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

    fun clearBrowserUrl() {
        _uiState.value = _uiState.value.copy(browserUrl = null)
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
                val scheme = state.useHttps.let { if (it) "https" else "http" }
                val normalizedUrl = "${scheme}://${state.serverUrl.trim().trimEnd('/')}"
                
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
