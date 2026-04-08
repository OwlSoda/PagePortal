package com.owlsoda.pageportal.features.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.services.ServiceManager
import com.owlsoda.pageportal.services.storyteller.StorytellerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WebReaderState(
    val url: String = "",
    val authToken: String? = null,
    val domain: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class WebReaderViewModel @Inject constructor(
    private val serviceManager: ServiceManager
) : ViewModel() {

    private val _state = MutableStateFlow(WebReaderState())
    val state: StateFlow<WebReaderState> = _state.asStateFlow()

    fun initialize(url: String) {
        viewModelScope.launch {
            val domain = android.net.Uri.parse(url).host
            val service = serviceManager.getServiceByUrl(url)
            
            var token: String? = null
            if (service is StorytellerService) {
                token = service.authToken
            }
            
            _state.value = WebReaderState(
                url = url,
                authToken = token,
                domain = domain,
                isLoading = false
            )
        }
    }
}
