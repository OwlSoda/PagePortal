package com.owlsoda.pageportal.reader.audio

import android.media.audiofx.Equalizer

/**
 * Manages Android's Equalizer audio effect for ReadAloud playback
 */
class AudioEqualizerManager(private val audioSessionId: Int) {
    
    private var equalizer: Equalizer? = null
    
    // Standard audiobook/reading presets
    val presets = listOf(
        "Normal",
        "Classical", 
        "Jazz",
        "Pop",
        "Rock",
        "Spoken Word"
    )
    
    /**
     * Initialize the equalizer with audio session
     */
    fun initialize() {
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            // Equalizer not supported on this device
            android.util.Log.e("AudioEqualizer", "Failed to initialize equalizer", e)
        }
    }
    
    /**
     * Get number of bands available (usually 5)
     */
    fun getNumberOfBands(): Short {
        return equalizer?.numberOfBands ?: 0
    }
    
    /**
     * Get frequency range for a band
     * @param band Band index (0-4 for 5-band EQ)
     * @return Frequency in Hz
     */
    fun getCenterFrequency(band: Short): Int {
        return equalizer?.getCenterFreq(band)?.div(1000) ?: 0 // Convert to Hz
    }
    
    /**
     * Get current level for a band
     * @param band Band index
     * @return Level in millibels (-1500 to +1500 typically)
     */
    fun getBandLevel(band: Short): Short {
        return equalizer?.getBandLevel(band) ?: 0
    }
    
    /**
     * Set level for a band
     * @param band Band index
     * @param level Level in millibels
     */
    fun setBandLevel(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
    }
    
    /**
     * Get min/max levels in millibels
     */
    fun getBandLevelRange(): Pair<Short, Short> {
        val eq = equalizer ?: return Pair(0, 0)
        return Pair(eq.bandLevelRange[0], eq.bandLevelRange[1])
    }
    
    /**
     * Apply a preset by name
     */
    fun applyPreset(presetName: String) {
        val eq = equalizer ?: return
        
        when (presetName) {
            "Normal" -> resetToFlat()
            "Classical" -> {
                // Boost highs and lows, slight mid scoop
                applyCustomLevels(listOf(400, 200, 0, 300, 500))
            }
            "Jazz" -> {
                // Boost mids and highs
                applyCustomLevels(listOf(200, 300, 400, 300, 200))
            }
            "Pop" -> {
                // Boost bass and highs, reduce mids
                applyCustomLevels(listOf(500, 200, -100, 200, 400))
            }
            "Rock" -> {
                // V-shape: boost bass and treble
                applyCustomLevels(listOf(600, 300, 0, 300, 500))
            }
            "Spoken Word" -> {
                // Boost mids (voice frequencies), reduce bass/treble
                applyCustomLevels(listOf(-200, 300, 500, 400, -100))
            }
        }
    }
    
    /**
     * Reset all bands to 0 (flat)
     */
    private fun resetToFlat() {
        val numBands = getNumberOfBands()
        for (i in 0 until numBands) {
            setBandLevel(i.toShort(), 0)
        }
    }
    
    /**
     * Apply custom levels to all bands
     * @param levels List of millibel values (should match number of bands)
     */
    private fun applyCustomLevels(levels: List<Int>) {
        val numBands = getNumberOfBands()
        levels.take(numBands.toInt()).forEachIndexed { index, level ->
            setBandLevel(index.toShort(), level.toShort())
        }
    }
    
    /**
     * Enable/disable equalizer
     */
    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
    }
    
    /**
     * Check if equalizer is enabled
     */
    fun isEnabled(): Boolean {
        return equalizer?.enabled ?: false
    }
    
    /**
     * Release equalizer resources
     */
    fun release() {
        equalizer?.release()
        equalizer = null
    }
}
