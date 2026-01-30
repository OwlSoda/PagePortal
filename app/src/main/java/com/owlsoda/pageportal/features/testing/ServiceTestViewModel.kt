package com.owlsoda.pageportal.features.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.services.ServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServiceTestState(
    val results: Map<String, ValidationResult> = emptyMap(),
    val isRunning: Boolean = false,
    val serverName: String? = null
)

@HiltViewModel
class ServiceTestViewModel @Inject constructor(
    private val serviceManager: ServiceManager,
    private val serverDao: ServerDao,
    private val serviceValidator: ServiceValidator
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiceTestState())
    val uiState: StateFlow<ServiceTestState> = _uiState.asStateFlow()

    fun runTests(serverId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true, results = emptyMap())
            
            val server = serverDao.getServerById(serverId)
            if (server == null) {
                _uiState.value = _uiState.value.copy(
                    isRunning = false, 
                    serverName = "Unknown Server"
                )
                return@launch
            }
            
            _uiState.value = _uiState.value.copy(serverName = server.displayName ?: server.serviceType)
            
            val service = serviceManager.getService(serverId)
            if (service == null) {
                  addResult("Initialization", ValidationResult.Failure("Could not load service"))
                  _uiState.value = _uiState.value.copy(isRunning = false)
                  return@launch
            }
            
            // Run tests sequentially
            addResult("Connection", serviceValidator.validateConnection(service))
            addResult("Listing", serviceValidator.validateListing(service))
            addResult("Details", serviceValidator.validateDetails(service))
            addResult("Progress Sync", serviceValidator.validateSyncCapabilities(service))
            // Download headers check skipped for now until better implemented
            
            _uiState.value = _uiState.value.copy(isRunning = false)
        }
    }
    
    private fun addResult(testName: String, result: ValidationResult) {
        val current = _uiState.value.results.toMutableMap()
        current[testName] = result
        _uiState.value = _uiState.value.copy(results = current)
    }
}
