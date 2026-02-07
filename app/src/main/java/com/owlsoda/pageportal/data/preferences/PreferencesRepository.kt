package com.owlsoda.pageportal.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object PreferencesKeys {
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SLEEP_TIMER_MINUTES = intPreferencesKey("sleep_timer_minutes")
        val GRID_MIN_WIDTH = intPreferencesKey("grid_min_width")
        
        // Reader Settings
        val READER_FONT_FAMILY = stringPreferencesKey("reader_font_family") // Serif, Sans, Monospace, etc.
        val READER_FONT_SIZE = floatPreferencesKey("reader_font_size") // Scale factor e.g. 1.0, 1.2
        val READER_LINE_HEIGHT = floatPreferencesKey("reader_line_height") // e.g. 1.5
        val READER_MARGIN = intPreferencesKey("reader_margin") // e.g. 2 (rem or %)
        val READER_THEME = stringPreferencesKey("reader_theme") // Light, Sepia, Dark, Black
        val READER_VERTICAL_SCROLL = booleanPreferencesKey("reader_vertical_scroll") // true = vertical, false = horizontal
    }

    val isOfflineModeEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.OFFLINE_MODE] ?: false
        }

    suspend fun setOfflineMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.OFFLINE_MODE] = enabled
        }
    }
    
    val themeMode: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.THEME_MODE] ?: "SYSTEM"
        }
    
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode
        }
    }

    // Playback Speed (default 1.0x)
    val playbackSpeed: Flow<Float> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.PLAYBACK_SPEED] ?: 1.0f }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[PreferencesKeys.PLAYBACK_SPEED] = speed }
    }

    // Sleep Timer Duration (default 0 = disabled)
    val sleepTimerMinutes: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.SLEEP_TIMER_MINUTES] ?: 0 }
    
    suspend fun setSleepTimerMinutes(minutes: Int) {
        context.dataStore.edit { it[PreferencesKeys.SLEEP_TIMER_MINUTES] = minutes }
    }

    // Grid Min Width (default 120dp)
    val gridMinWidth: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.GRID_MIN_WIDTH] ?: 120 }

    suspend fun setGridMinWidth(width: Int) {
        context.dataStore.edit { it[PreferencesKeys.GRID_MIN_WIDTH] = width }
    }

    // Reader Settings Accessors
    
    val readerFontFamily: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.READER_FONT_FAMILY] ?: "Serif" }

    suspend fun setReaderFontFamily(family: String) {
        context.dataStore.edit { it[PreferencesKeys.READER_FONT_FAMILY] = family }
    }

    val readerFontSize: Flow<Float> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.READER_FONT_SIZE] ?: 1.0f }

    suspend fun setReaderFontSize(size: Float) {
        context.dataStore.edit { it[PreferencesKeys.READER_FONT_SIZE] = size }
    }
    
    val readerLineHeight: Flow<Float> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.READER_LINE_HEIGHT] ?: 1.5f }

    suspend fun setReaderLineHeight(height: Float) {
        context.dataStore.edit { it[PreferencesKeys.READER_LINE_HEIGHT] = height }
    }
    
    // Margin level (0=Tight, 1=Normal, 2=Wide, etc.) or actually just Int value
    val readerMargin: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.READER_MARGIN] ?: 1 } // Default 1 (Normal)

    suspend fun setReaderMargin(margin: Int) {
        context.dataStore.edit { it[PreferencesKeys.READER_MARGIN] = margin }
    }
    
    val readerTheme: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.READER_THEME] ?: "Sepia" } 

    suspend fun setReaderTheme(theme: String) {
        context.dataStore.edit { it[PreferencesKeys.READER_THEME] = theme }
    }

    val readerVerticalScroll: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.READER_VERTICAL_SCROLL] ?: true } // Default to Vertical (true)

    suspend fun setReaderVerticalScroll(isVertical: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.READER_VERTICAL_SCROLL] = isVertical }
    }
}
