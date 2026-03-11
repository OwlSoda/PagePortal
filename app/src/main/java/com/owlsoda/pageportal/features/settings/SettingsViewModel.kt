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
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import com.owlsoda.pageportal.data.preferences.AppPreferencesBackup

data class SettingsState(
    val isOfflineMode: Boolean = false,
    val cacheSize: String = "Calculating...",
    val themeMode: String = "SYSTEM",
    val playbackSpeed: Float = 1.0f,
    val sleepTimerMinutes: Int = 0,
    val gridMinWidth: Int = 120,
    
    // Reader Settings
    val readerFontFamily: String = "Serif",
    val readerFontSize: Float = 1.0f,
    val readerLineHeight: Float = 1.5f,
    val readerMargin: Int = 1,
    val readerTheme: String = "Sepia",
    val readerVerticalScroll: Boolean = true,
    val readerTextAlignment: String = "LEFT",
    val readerParagraphSpacing: Float = 1.0f,
    val readerBrightness: Float = -1.0f,
    
    val gestureTapLeft: String = "PREV",
    val gestureTapCenter: String = "MENU",
    val gestureTapRight: String = "NEXT",
    
    // Accessibility
    val boldTextEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val reduceAnimations: Boolean = false,
    
    val toastMessage: String? = null
)


@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
        
        // Collect Reader Preferences
        viewModelScope.launch {
            preferencesRepository.readerFontFamily.collectLatest { v -> _state.update { it.copy(readerFontFamily = v) } }
        }
        viewModelScope.launch {
            preferencesRepository.readerFontSize.collectLatest { v -> _state.update { it.copy(readerFontSize = v) } }
        }
        viewModelScope.launch {
            preferencesRepository.readerLineHeight.collectLatest { v -> _state.update { it.copy(readerLineHeight = v) } }
        }
        viewModelScope.launch {
            preferencesRepository.readerMargin.collectLatest { v -> _state.update { it.copy(readerMargin = v) } }
        }
        viewModelScope.launch {
            preferencesRepository.readerTheme.collectLatest { v -> _state.update { it.copy(readerTheme = v) } }
        }
        viewModelScope.launch {
            preferencesRepository.readerVerticalScroll.collectLatest { v -> _state.update { it.copy(readerVerticalScroll = v) } }
        }
        viewModelScope.launch {
            preferencesRepository.readerTextAlignment.collectLatest { v -> _state.update { it.copy(readerTextAlignment = v) } }
        }
        viewModelScope.launch {
            preferencesRepository.readerParagraphSpacing.collectLatest { v -> _state.update { it.copy(readerParagraphSpacing = v) } }
        }
        viewModelScope.launch {
            preferencesRepository.readerBrightness.collectLatest { v -> _state.update { it.copy(readerBrightness = v) } }
        }
        
        // Gesture Settings
        viewModelScope.launch {
            preferencesRepository.gestureTapLeft.collectLatest { v -> _state.update { it.copy(gestureTapLeft = v) } }
        }
        viewModelScope.launch {
            preferencesRepository.gestureTapCenter.collectLatest { v -> _state.update { it.copy(gestureTapCenter = v) } }
        }
        viewModelScope.launch {
            preferencesRepository.gestureTapRight.collectLatest { v -> _state.update { it.copy(gestureTapRight = v) } }
        }
        
        // Accessibility Settings
        viewModelScope.launch {
            preferencesRepository.boldTextEnabled.collectLatest { v -> _state.update { it.copy(boldTextEnabled = v) } }
        }
        viewModelScope.launch {
            preferencesRepository.keepScreenOn.collectLatest { v -> _state.update { it.copy(keepScreenOn = v) } }
        }
        viewModelScope.launch {
            preferencesRepository.reduceAnimations.collectLatest { v -> _state.update { it.copy(reduceAnimations = v) } }
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
            try {
                val cacheDir = context.cacheDir
                cacheDir?.deleteRecursively()
                cacheDir?.mkdirs()
                calculateCacheSize()
                _state.update { it.copy(toastMessage = "Cache cleared") }
            } catch (e: Exception) {
                _state.update { it.copy(toastMessage = "Failed to clear cache: ${e.message}") }
            }
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
    
    // Reader Settings Setters
    fun setReaderFontFamily(family: String) = viewModelScope.launch { preferencesRepository.setReaderFontFamily(family) }
    fun setReaderFontSize(size: Float) = viewModelScope.launch { preferencesRepository.setReaderFontSize(size) }
    fun setReaderLineHeight(height: Float) = viewModelScope.launch { preferencesRepository.setReaderLineHeight(height) }
    fun setReaderMargin(margin: Int) = viewModelScope.launch { preferencesRepository.setReaderMargin(margin) }
    fun setReaderTheme(theme: String) = viewModelScope.launch { preferencesRepository.setReaderTheme(theme) }
    fun setReaderVerticalScroll(isVertical: Boolean) = viewModelScope.launch { preferencesRepository.setReaderVerticalScroll(isVertical) }
    fun setReaderTextAlignment(alignment: String) = viewModelScope.launch { preferencesRepository.setReaderTextAlignment(alignment) }
    fun setReaderParagraphSpacing(spacing: Float) = viewModelScope.launch { preferencesRepository.setReaderParagraphSpacing(spacing) }
    fun setReaderBrightness(brightness: Float) = viewModelScope.launch { preferencesRepository.setReaderBrightness(brightness) }
    
    // Gesture Setters
    fun setGestureTapLeft(action: String) = viewModelScope.launch { preferencesRepository.setGestureTapLeft(action) }
    fun setGestureTapCenter(action: String) = viewModelScope.launch { preferencesRepository.setGestureTapCenter(action) }
    fun setGestureTapRight(action: String) = viewModelScope.launch { preferencesRepository.setGestureTapRight(action) }
    
    // Accessibility Setters
    fun setBoldTextEnabled(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setBoldTextEnabled(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setKeepScreenOn(enabled) }
    fun setReduceAnimations(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setReduceAnimations(enabled) }
    
    
    fun exportSettings(uri: Uri) {
        viewModelScope.launch {
            try {
                val backup = preferencesRepository.exportPreferences()
                val json = Gson().toJson(backup)
                context.contentResolver.openOutputStream(uri)?.use { 
                    it.write(json.toByteArray()) 
                }
                _state.update { it.copy(toastMessage = "Settings exported successfully") }
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update { it.copy(toastMessage = "Failed to export settings: ${e.message}") }
            }
        }
    }
    
    fun importSettings(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { 
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw Exception("Could not read file")
                
                val backup = Gson().fromJson(json, AppPreferencesBackup::class.java)
                preferencesRepository.importPreferences(backup)
                _state.update { it.copy(toastMessage = "Settings imported successfully") }
            } catch (e: Exception) {
                e.printStackTrace()
                _state.update { it.copy(toastMessage = "Failed to import settings: ${e.message}") }
            }
        }
    }
    
    fun clearToastMessage() {
        _state.update { it.copy(toastMessage = null) }
    }

    private fun calculateCacheSize() {
        viewModelScope.launch {
            val size = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val cacheDir = context.cacheDir
                val bytes = cacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
                formatBytes(bytes)
            }
            _state.update { it.copy(cacheSize = size) }
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
