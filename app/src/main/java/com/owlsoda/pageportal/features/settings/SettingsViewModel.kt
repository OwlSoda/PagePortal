package com.owlsoda.pageportal.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.BookDao
import com.owlsoda.pageportal.data.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val isOfflineMode: Boolean = false,
    val cacheSize: String = "Calculating..."
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val bookDao: BookDao
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.isOfflineModeEnabled.collectLatest { enabled ->
                _state.value = _state.value.copy(isOfflineMode = enabled)
            }
        }
        calculateCacheSize()
    }

    fun toggleOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOfflineMode(enabled)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            // Placeholder: In a real app, this would delete cached images/files
            // For now, we just simulate recalculating
            _state.value = _state.value.copy(cacheSize = "0 MB")
        }
    }
    
    private fun calculateCacheSize() {
        // Placeholder
        _state.value = _state.value.copy(cacheSize = "124 MB")
    }
}
