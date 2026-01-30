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
    val cacheSize: String = "Calculating...",
    val themeMode: String = "SYSTEM",
    val playbackSpeed: Float = 1.0f,
    val sleepTimerMinutes: Int = 0,
    val gridMinWidth: Int = 120
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
        viewModelScope.launch {
            preferencesRepository.themeMode.collectLatest { mode ->
                _state.value = _state.value.copy(themeMode = mode)
            }
        }
        viewModelScope.launch {
            preferencesRepository.playbackSpeed.collectLatest { speed ->
                _state.value = _state.value.copy(playbackSpeed = speed)
            }
        }
        viewModelScope.launch {
            preferencesRepository.sleepTimerMinutes.collectLatest { minutes ->
                _state.value = _state.value.copy(sleepTimerMinutes = minutes)
            }
        }
        viewModelScope.launch {
            preferencesRepository.gridMinWidth.collectLatest { width ->
                _state.value = _state.value.copy(gridMinWidth = width)
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
    
    fun updateTheme(mode: String) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }
    
    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            preferencesRepository.setPlaybackSpeed(speed)
        }
    }
    
    fun setSleepTimerMinutes(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.setSleepTimerMinutes(minutes)
        }
    }

    fun setGridMinWidth(width: Int) {
        viewModelScope.launch {
            preferencesRepository.setGridMinWidth(width)
        }
    }
    
    private fun calculateCacheSize() {
        // Placeholder
        _state.value = _state.value.copy(cacheSize = "124 MB")
    }
}
