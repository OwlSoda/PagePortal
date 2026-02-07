package com.owlsoda.pageportal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.services.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val connectedServiceTypes: Set<ServiceType> = emptySet(),
    val isLoading: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val serverDao: ServerDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            serverDao.getAllServers().collect { servers ->
                val types = servers.mapNotNull { 
                    try {
                        ServiceType.valueOf(it.serviceType)
                    } catch (e: Exception) { null }
                }.toSet()
                
                _uiState.update { it.copy(connectedServiceTypes = types) }
            }
        }
    }
}
