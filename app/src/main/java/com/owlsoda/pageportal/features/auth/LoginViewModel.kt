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
        // Observe OAuth redirects
        viewModelScope.launch {
            oauthManager.redirectUri.collect { uri ->
                val code = uri.getQueryParameter("code")
                val state = uri.getQueryParameter("state")
                val token = uri.getQueryParameter("token") ?: uri.fragment?.split("=").let { if (it?.size == 2 && it[0] == "access_token") it[1] else null }

                if (code != null && state != null) {
                    handleAbsSsoCallback(code, state)
                } else if (token != null) {
                    handleStorytellerCallback(token)
                }
            }
        }

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
        
        // Debounced or direct check for ABS SSO
        if (_uiState.value.selectedService == 1 && url.startsWith("http")) {
            viewModelScope.launch {
                val isSsoAvailable = authRepository.checkAbsSso(url)
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

    fun startAbsSsoLogin() {
        viewModelScope.launch {
            val state = _uiState.value
            val normalizedUrl = state.serverUrl.trim().trimEnd('/')
            if (normalizedUrl.isBlank()) {
                _uiState.value = state.copy(error = "Please enter a server URL")
                return@launch
            }

            _uiState.value = state.copy(isLoading = true, error = null)

            try {
                // Prepare PKCE
                val verifier = PkceHelper.generateVerifier()
                val challenge = PkceHelper.generateChallenge(verifier)
                val oauthState = PkceHelper.generateState()

                absPkceVerifier = verifier
                absPkceState = oauthState

                // Build redirect URL
                // We use a dummy redirect URI because ABS handles the internal redirect to our custom scheme
                val redirectUri = "pageportal://oauth2redirect"
                val authUrl = "$normalizedUrl/auth/openid?code_challenge=$challenge&code_challenge_method=S256&redirect_uri=${Uri.encode(redirectUri)}&client_id=Audiobookshelf-App&response_type=code&state=$oauthState"

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    browserUrl = authUrl
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to start SSO login: ${e.message}"
                )
                e.printStackTrace()
            }
        }
    }

    fun startStorytellerBrowserLogin() {
        viewModelScope.launch {
            val state = _uiState.value
            val normalizedUrl = state.serverUrl.trim().trimEnd('/')
            if (normalizedUrl.isBlank()) {
                _uiState.value = state.copy(error = "Please enter a server URL")
                return@launch
            }

            // We open the web login page. Storyteller v2 handles the rest.
            // We append a redirect_uri so the web app knows where to go after login,
            // though Storyteller might not support this directly in its login form yet.
            // Alternatively, we just open the login page and let the user log in.
            // The user will have to manually return or the app detects session cookie.
            // Actually, for Storyteller, it's simpler to just let them log in via the web UI.
            val authUrl = "$normalizedUrl/login?redirect_uri=${Uri.encode("pageportal://oauth2redirect")}"

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                browserUrl = authUrl
            )
        }
    }

    fun handleStorytellerCallback(token: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val normalizedUrl = state.serverUrl.trim().trimEnd('/')
            _uiState.value = state.copy(isLoading = true, error = null)

            try {
                val service = StorytellerService(authRepository.serviceManager.okHttpClient)
                val authResult = service.authenticateWithToken(normalizedUrl, token)

                if (authResult.success) {
                    val loginResult = authRepository.loginWithToken(
                        serviceType = ServiceType.STORYTELLER,
                        serverUrl = normalizedUrl,
                        token = token,
                        username = authResult.username ?: "Browser User",
                        userId = authResult.userId
                    )

                    loginResult.fold(
                        onSuccess = {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isLoggedIn = true
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to save server session"
                            )
                        }
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = authResult.errorMessage ?: "Token validation failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to handle storyteller login: ${e.message}"
                )
            }
        }
    }

    fun handleAbsSsoCallback(code: String, state: String) {
        viewModelScope.launch {
            val uiStateValue = _uiState.value
            val normalizedUrl = uiStateValue.serverUrl.trim().trimEnd('/')
            
            if (state != absPkceState) {
                _uiState.value = uiStateValue.copy(error = "Invalid OAuth state - possible CSRF attack")
                return@launch
            }

            val verifier = absPkceVerifier ?: run {
                _uiState.value = uiStateValue.copy(error = "Missing PKCE verifier")
                return@launch
            }

            _uiState.value = uiStateValue.copy(isLoading = true, error = null)

            try {
                val service = AudiobookshelfService(
                    normalizedUrl,
                    authRepository.serviceManager.okHttpClient
                )
                
                val result = service.oauthCallback(state, code, verifier)
                
                if (result.success && result.token != null) {
                    val loginResult = authRepository.loginWithToken(
                        serviceType = ServiceType.AUDIOBOOKSHELF,
                        serverUrl = normalizedUrl,
                        token = result.token,
                        username = result.username ?: "SSO User",
                        userId = result.userId
                    )

                    loginResult.fold(
                        onSuccess = {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isLoggedIn = true
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to save server session"
                            )
                        }
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.errorMessage ?: "SSO authentication failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "SSO callback failed: ${e.message}"
                )
                e.printStackTrace()
            }
        }
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
                val config = try {
                    OidcHelper.fetchConfiguration(Uri.parse(normalizedUrl))
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "OIDC discovery failed for '$normalizedUrl'. Make sure the server URL is correct and the server supports OIDC.\n\nDetails: ${e.message}"
                    )
                    e.printStackTrace()
                    return@launch
                }
                
                // Build the authorization request
                val request = AuthorizationRequest.Builder(
                    config,
                    "pageportal",
                    ResponseTypeValues.CODE,
                    Uri.parse("pageportal://oauth2redirect")
                )
                .setScope("openid profile offline_access")
                .build()
                
                _uiState.value = _uiState.value.copy(oidcRequest = request, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to start OIDC login: ${e.message}"
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
