package com.owlsoda.pageportal.data.preferences

import com.google.gson.annotations.SerializedName

data class AppPreferencesBackup(
    @SerializedName("offline_mode") val offlineMode: Boolean = false,
    @SerializedName("theme_mode") val themeMode: String = "SYSTEM",
    @SerializedName("playback_speed") val playbackSpeed: Float = 1.0f,
    @SerializedName("sleep_timer_minutes") val sleepTimerMinutes: Int = 0,
    @SerializedName("grid_min_width") val gridMinWidth: Int = 120,
    
    // Reader Settings
    @SerializedName("reader_font_family") val readerFontFamily: String = "Serif",
    @SerializedName("reader_font_size") val readerFontSize: Float = 1.0f,
    @SerializedName("reader_line_height") val readerLineHeight: Float = 1.5f,
    @SerializedName("reader_margin") val readerMargin: Int = 1,
    @SerializedName("reader_theme") val readerTheme: String = "Sepia",
    @SerializedName("reader_vertical_scroll") val readerVerticalScroll: Boolean = true,
    @SerializedName("reader_text_alignment") val readerTextAlignment: String = "LEFT",
    @SerializedName("reader_paragraph_spacing") val readerParagraphSpacing: Float = 1.0f,
    @SerializedName("reader_brightness") val readerBrightness: Float = -1.0f,
    @SerializedName("reader_smil_highlight_color") val readerSmilHighlightColor: String = "#FFF176",
    @SerializedName("reader_smil_underline_color") val readerSmilUnderlineColor: String = "#FF6D00",
    
    // Gestures
    @SerializedName("gesture_tap_left") val gestureTapLeft: String = "PREV",
    @SerializedName("gesture_tap_center") val gestureTapCenter: String = "MENU",
    @SerializedName("gesture_tap_right") val gestureTapRight: String = "NEXT",
    
    // Audio Settings
    @SerializedName("rewind_seconds") val rewindSeconds: Int = 10,
    @SerializedName("forward_seconds") val forwardSeconds: Int = 30
)
