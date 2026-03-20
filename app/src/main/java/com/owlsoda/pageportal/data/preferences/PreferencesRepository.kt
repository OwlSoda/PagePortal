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
import kotlinx.coroutines.flow.first
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
        val READER_TEXT_ALIGNMENT = stringPreferencesKey("reader_text_alignment") // "LEFT", "JUSTIFY", "CENTER"
        val READER_PARAGRAPH_SPACING = floatPreferencesKey("reader_paragraph_spacing") // Scale factor
        val READER_BRIGHTNESS = floatPreferencesKey("reader_brightness") // -1.0 = system, 0.0-1.0 = manual
        val READER_SMIL_HIGHLIGHT_COLOR = stringPreferencesKey("reader_smil_highlight_color")
        val READER_SMIL_UNDERLINE_COLOR = stringPreferencesKey("reader_smil_underline_color")
        
        // Gesture Settings
        val GESTURE_TAP_LEFT = stringPreferencesKey("gesture_tap_left") // "PREV", "NEXT", "MENU", "NONE"
        val GESTURE_TAP_CENTER = stringPreferencesKey("gesture_tap_center")
        val GESTURE_TAP_RIGHT = stringPreferencesKey("gesture_tap_right")
        
        // Audio Settings
        val REWIND_SECONDS = intPreferencesKey("rewind_seconds")
        val FORWARD_SECONDS = intPreferencesKey("forward_seconds")
        
        // Accessibility Settings
        val BOLD_TEXT_ENABLED = booleanPreferencesKey("bold_text_enabled")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
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
    
    val readerTextAlignment: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.READER_TEXT_ALIGNMENT] ?: "LEFT" }

    suspend fun setReaderTextAlignment(alignment: String) {
        context.dataStore.edit { it[PreferencesKeys.READER_TEXT_ALIGNMENT] = alignment }
    }
    
    val readerParagraphSpacing: Flow<Float> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.READER_PARAGRAPH_SPACING] ?: 1.0f }

    suspend fun setReaderParagraphSpacing(spacing: Float) {
        context.dataStore.edit { it[PreferencesKeys.READER_PARAGRAPH_SPACING] = spacing }
    }
    
    val readerBrightness: Flow<Float> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.READER_BRIGHTNESS] ?: -1.0f }

    suspend fun setReaderBrightness(brightness: Float) {
        context.dataStore.edit { it[PreferencesKeys.READER_BRIGHTNESS] = brightness }
    }
    
    val readerSmilHighlightColor: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.READER_SMIL_HIGHLIGHT_COLOR] ?: "#FFF176" }

    suspend fun setReaderSmilHighlightColor(color: String) {
        context.dataStore.edit { it[PreferencesKeys.READER_SMIL_HIGHLIGHT_COLOR] = color }
    }

    val readerSmilUnderlineColor: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.READER_SMIL_UNDERLINE_COLOR] ?: "#FF6D00" }

    suspend fun setReaderSmilUnderlineColor(color: String) {
        context.dataStore.edit { it[PreferencesKeys.READER_SMIL_UNDERLINE_COLOR] = color }
    }
    
    // Gesture Accessors
    val gestureTapLeft: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.GESTURE_TAP_LEFT] ?: "PREV" }

    suspend fun setGestureTapLeft(action: String) {
        context.dataStore.edit { it[PreferencesKeys.GESTURE_TAP_LEFT] = action }
    }
    
    val gestureTapCenter: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.GESTURE_TAP_CENTER] ?: "MENU" }

    suspend fun setGestureTapCenter(action: String) {
        context.dataStore.edit { it[PreferencesKeys.GESTURE_TAP_CENTER] = action }
    }
    
    val gestureTapRight: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.GESTURE_TAP_RIGHT] ?: "NEXT" }

    suspend fun setGestureTapRight(action: String) {
        context.dataStore.edit { it[PreferencesKeys.GESTURE_TAP_RIGHT] = action }
    }
    
    // Audio Settings
    val rewindSeconds: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.REWIND_SECONDS] ?: 10 } // Default 10s

    suspend fun setRewindSeconds(seconds: Int) {
        context.dataStore.edit { it[PreferencesKeys.REWIND_SECONDS] = seconds }
    }

    val forwardSeconds: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.FORWARD_SECONDS] ?: 30 } // Default 30s

    suspend fun setForwardSeconds(seconds: Int) {
        context.dataStore.edit { it[PreferencesKeys.FORWARD_SECONDS] = seconds }
    }

    // Accessibility Settings
    val boldTextEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.BOLD_TEXT_ENABLED] ?: false }

    suspend fun setBoldTextEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BOLD_TEXT_ENABLED] = enabled }
    }

    val keepScreenOn: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.KEEP_SCREEN_ON] ?: false }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.KEEP_SCREEN_ON] = enabled }
    }

    val reduceAnimations: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferencesKeys.REDUCE_ANIMATIONS] ?: false }

    suspend fun setReduceAnimations(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.REDUCE_ANIMATIONS] = enabled }
    }

    suspend fun getPreferencesBackup(): AppPreferencesBackup {
        var backup = AppPreferencesBackup()
        context.dataStore.data.collect { prefs ->
            backup = AppPreferencesBackup(
                offlineMode = prefs[PreferencesKeys.OFFLINE_MODE] ?: false,
                themeMode = prefs[PreferencesKeys.THEME_MODE] ?: "SYSTEM",
                playbackSpeed = prefs[PreferencesKeys.PLAYBACK_SPEED] ?: 1.0f,
                sleepTimerMinutes = prefs[PreferencesKeys.SLEEP_TIMER_MINUTES] ?: 0,
                gridMinWidth = prefs[PreferencesKeys.GRID_MIN_WIDTH] ?: 120,
                readerFontFamily = prefs[PreferencesKeys.READER_FONT_FAMILY] ?: "Serif",
                readerFontSize = prefs[PreferencesKeys.READER_FONT_SIZE] ?: 1.0f,
                readerLineHeight = prefs[PreferencesKeys.READER_LINE_HEIGHT] ?: 1.5f,
                readerMargin = prefs[PreferencesKeys.READER_MARGIN] ?: 1,
                readerTheme = prefs[PreferencesKeys.READER_THEME] ?: "Sepia",
                readerVerticalScroll = prefs[PreferencesKeys.READER_VERTICAL_SCROLL] ?: true,
                readerTextAlignment = prefs[PreferencesKeys.READER_TEXT_ALIGNMENT] ?: "LEFT",
                readerParagraphSpacing = prefs[PreferencesKeys.READER_PARAGRAPH_SPACING] ?: 1.0f,
                readerBrightness = prefs[PreferencesKeys.READER_BRIGHTNESS] ?: -1.0f,
                readerSmilHighlightColor = prefs[PreferencesKeys.READER_SMIL_HIGHLIGHT_COLOR] ?: "#FFF176",
                readerSmilUnderlineColor = prefs[PreferencesKeys.READER_SMIL_UNDERLINE_COLOR] ?: "#FF6D00",
                gestureTapLeft = prefs[PreferencesKeys.GESTURE_TAP_LEFT] ?: "PREV",
                gestureTapCenter = prefs[PreferencesKeys.GESTURE_TAP_CENTER] ?: "MENU",
                gestureTapRight = prefs[PreferencesKeys.GESTURE_TAP_RIGHT] ?: "NEXT",
                rewindSeconds = prefs[PreferencesKeys.REWIND_SECONDS] ?: 10,
                forwardSeconds = prefs[PreferencesKeys.FORWARD_SECONDS] ?: 30
            )
            throw kotlinx.coroutines.CancellationException("Backup collected") // Break collection
        }
        return backup
    }

    // Helper to get backup without exception trickery
    suspend fun exportPreferences(): AppPreferencesBackup {
        val prefs = context.dataStore.data.first()
        return AppPreferencesBackup(
            offlineMode = prefs[PreferencesKeys.OFFLINE_MODE] ?: false,
            themeMode = prefs[PreferencesKeys.THEME_MODE] ?: "SYSTEM",
            playbackSpeed = prefs[PreferencesKeys.PLAYBACK_SPEED] ?: 1.0f,
            sleepTimerMinutes = prefs[PreferencesKeys.SLEEP_TIMER_MINUTES] ?: 0,
            gridMinWidth = prefs[PreferencesKeys.GRID_MIN_WIDTH] ?: 120,
            readerFontFamily = prefs[PreferencesKeys.READER_FONT_FAMILY] ?: "Serif",
            readerFontSize = prefs[PreferencesKeys.READER_FONT_SIZE] ?: 1.0f,
            readerLineHeight = prefs[PreferencesKeys.READER_LINE_HEIGHT] ?: 1.5f,
            readerMargin = prefs[PreferencesKeys.READER_MARGIN] ?: 1,
            readerTheme = prefs[PreferencesKeys.READER_THEME] ?: "Sepia",
            readerVerticalScroll = prefs[PreferencesKeys.READER_VERTICAL_SCROLL] ?: true,
            readerTextAlignment = prefs[PreferencesKeys.READER_TEXT_ALIGNMENT] ?: "LEFT",
            readerParagraphSpacing = prefs[PreferencesKeys.READER_PARAGRAPH_SPACING] ?: 1.0f,
            readerBrightness = prefs[PreferencesKeys.READER_BRIGHTNESS] ?: -1.0f,
            readerSmilHighlightColor = prefs[PreferencesKeys.READER_SMIL_HIGHLIGHT_COLOR] ?: "#FFF176",
            readerSmilUnderlineColor = prefs[PreferencesKeys.READER_SMIL_UNDERLINE_COLOR] ?: "#FF6D00",
            gestureTapLeft = prefs[PreferencesKeys.GESTURE_TAP_LEFT] ?: "PREV",
            gestureTapCenter = prefs[PreferencesKeys.GESTURE_TAP_CENTER] ?: "MENU",
            gestureTapRight = prefs[PreferencesKeys.GESTURE_TAP_RIGHT] ?: "NEXT",
            rewindSeconds = prefs[PreferencesKeys.REWIND_SECONDS] ?: 10,
            forwardSeconds = prefs[PreferencesKeys.FORWARD_SECONDS] ?: 30
        )
    }

    suspend fun importPreferences(backup: AppPreferencesBackup) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.OFFLINE_MODE] = backup.offlineMode
            prefs[PreferencesKeys.THEME_MODE] = backup.themeMode
            prefs[PreferencesKeys.PLAYBACK_SPEED] = backup.playbackSpeed
            prefs[PreferencesKeys.SLEEP_TIMER_MINUTES] = backup.sleepTimerMinutes
            prefs[PreferencesKeys.GRID_MIN_WIDTH] = backup.gridMinWidth
            prefs[PreferencesKeys.READER_FONT_FAMILY] = backup.readerFontFamily
            prefs[PreferencesKeys.READER_FONT_SIZE] = backup.readerFontSize
            prefs[PreferencesKeys.READER_LINE_HEIGHT] = backup.readerLineHeight
            prefs[PreferencesKeys.READER_MARGIN] = backup.readerMargin
            prefs[PreferencesKeys.READER_THEME] = backup.readerTheme
            prefs[PreferencesKeys.READER_VERTICAL_SCROLL] = backup.readerVerticalScroll
            prefs[PreferencesKeys.READER_TEXT_ALIGNMENT] = backup.readerTextAlignment
            prefs[PreferencesKeys.READER_PARAGRAPH_SPACING] = backup.readerParagraphSpacing
            prefs[PreferencesKeys.READER_BRIGHTNESS] = backup.readerBrightness
            prefs[PreferencesKeys.READER_SMIL_HIGHLIGHT_COLOR] = backup.readerSmilHighlightColor
            prefs[PreferencesKeys.READER_SMIL_UNDERLINE_COLOR] = backup.readerSmilUnderlineColor
            prefs[PreferencesKeys.GESTURE_TAP_LEFT] = backup.gestureTapLeft
            prefs[PreferencesKeys.GESTURE_TAP_CENTER] = backup.gestureTapCenter
            prefs[PreferencesKeys.GESTURE_TAP_RIGHT] = backup.gestureTapRight
            prefs[PreferencesKeys.REWIND_SECONDS] = backup.rewindSeconds
            prefs[PreferencesKeys.FORWARD_SECONDS] = backup.forwardSeconds
        }
    }
}
