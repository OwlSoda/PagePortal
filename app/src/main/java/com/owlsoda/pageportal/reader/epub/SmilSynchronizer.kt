package com.owlsoda.pageportal.reader.epub

/**
 * Manages synchronization between audio playback position and text fragments.
 * Tracks the current SMIL fragment and triggers highlight/page navigation events.
 */
class SmilSynchronizer(
    private val smilData: SmilData,
    private val onHighlight: (fragmentId: String, chapterHref: String) -> Unit,
    private val onChapterChange: (newChapterHref: String, fragmentId: String) -> Unit
) {
    
    private var currentHighlightedPar: SmilPar? = null
    private var currentParIndex: Int = -1
    
    /**
     * Update based on current playback position in milliseconds.
     * Finds the matching SMIL fragment and triggers highlighting if changed.
     */
    fun updatePlaybackPosition(currentTimeMs: Long) {
        val currentTimeSec = currentTimeMs / 1000f
        val newPar = findParAtTime(currentTimeSec)
        
        if (newPar != null && newPar != currentHighlightedPar) {
            currentHighlightedPar = newPar
            
            // Parse textSrc: "chapter1.html#para1" or "OEBPS/Text/chapter1.html#para1"
            val (chapterHref, fragmentId) = parseTextSrc(newPar.textSrc)
            
            if (fragmentId != null) {
                onHighlight(fragmentId, chapterHref)
            }
        }
    }
    
    /**
     * Find the SmilPar that matches the current time.
     * Uses binary search for efficiency with large SMIL files.
     */
    private fun findParAtTime(timeSec: Float): SmilPar? {
        if (smilData.parList.isEmpty()) return null
        
        // Binary search for efficiency
        var left = 0
        var right = smilData.parList.size - 1
        
        while (left <= right) {
            val mid = (left + right) / 2
            val par = smilData.parList[mid]
            
            when {
                timeSec < par.clipBegin -> right = mid - 1
                timeSec > par.clipEnd -> left = mid + 1
                else -> {
                    currentParIndex = mid
                    return par // Found the matching fragment
                }
            }
        }
        
        // If we didn't find exact match, return the closest one
        if (currentParIndex >= 0 && currentParIndex < smilData.parList.size) {
            return smilData.parList[currentParIndex]
        }
        
        return null
    }
    
    /**
     * Parse textSrc into (chapterHref, fragmentId).
     * Examples:
     * - "chapter1.html#para1" -> ("chapter1.html", "para1")
     * - "OEBPS/Text/chapter1.html#para1" -> ("OEBPS/Text/chapter1.html", "para1")
     * - "chapter1.html" -> ("chapter1.html", null)
     */
    private fun parseTextSrc(textSrc: String): Pair<String, String?> {
        val parts = textSrc.split("#", limit = 2)
        val href = parts[0]
        val fragmentId = if (parts.size > 1) parts[1] else null
        return Pair(href, fragmentId)
    }
    
    /**
     * Get the current highlighted fragment ID, if any.
     */
    fun getCurrentFragmentId(): String? {
        return currentHighlightedPar?.let { parseTextSrc(it.textSrc).second }
    }
    
    /**
     * Get the current chapter href.
     */
    fun getCurrentChapterHref(): String? {
        return currentHighlightedPar?.let { parseTextSrc(it.textSrc).first }
    }
    
    /**
     * Reset synchronization state (e.g., when changing chapters manually).
     */
    fun reset() {
        currentHighlightedPar = null
        currentParIndex = -1
    }
    
    /**
     * Get total duration covered by this SMIL file (in seconds).
     */
    fun getTotalDuration(): Float {
        return smilData.parList.lastOrNull()?.clipEnd ?: 0f
    }
    
    /**
     * Get the time range for a specific fragment ID.
     * Returns (clipBegin, clipEnd) in seconds, or null if not found.
     */
    fun getTimeForFragment(fragmentId: String): Pair<Float, Float>? {
        return smilData.parList.find { 
            parseTextSrc(it.textSrc).second == fragmentId 
        }?.let { Pair(it.clipBegin, it.clipEnd) }
    }
}
