package com.owlsoda.pageportal.core.matching

import kotlin.math.max
import kotlin.math.min

object TitleMatcher {

    /**
     * Calculates a similarity score between 0.0 (no match) and 1.0 (perfect match).
     * Compares normalized titles and checks for author intersection.
     */
    fun calculateMatchScore(
        title1: String, 
        authors1: List<String>, 
        title2: String, 
        authors2: List<String>
    ): Float {
        val normTitle1 = normalize(title1)
        val normTitle2 = normalize(title2)
        
        // Exact normalized match is a strong signal
        if (normTitle1 == normTitle2) {
            return if (areAuthorsSimilar(authors1, authors2)) 1.0f else 0.8f
        }
        
        val titleScore = calculateSimilarity(normTitle1, normTitle2)
        
        // If titles are very different, don't bother checking authors
        if (titleScore < 0.5f) return 0.0f
        
        val authorScore = if (areAuthorsSimilar(authors1, authors2)) 1.0f else 0.0f
        
        // Weight titles heavily, but require author match for high confidence
        return (titleScore * 0.8f) + (authorScore * 0.2f)
    }
    
    fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "") // Remove punctuation
            .replace(Regex("\\s+"), " ") // Collapse spaces
            .trim()
            .removePrefix("the ")
            .removePrefix("a ")
            .trim()
    }
    
    private fun areAuthorsSimilar(authors1: List<String>, authors2: List<String>): Boolean {
        if (authors1.isEmpty() && authors2.isEmpty()) return true // Ambiguous
        if (authors1.isEmpty() || authors2.isEmpty()) return false
        
        // Check fuzzy intersection
        // e.g. "J.K. Rowling" vs "JK Rowling"
        for (a1 in authors1) {
            val norm1 = normalize(a1)
            for (a2 in authors2) {
                val norm2 = normalize(a2)
                if (norm1 == norm2 || calculateSimilarity(norm1, norm2) > 0.9f) {
                    return true
                }
                // Check if one contains the other (last name match)?
                // Too risky. Strict fuzzy match is safer.
            }
        }
        return false
    }

    private fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        if (longer.length == 0) return 1.0f
        
        val dist = levenshteinDistance(longer, shorter)
        return (longer.length - dist).toFloat() / longer.length.toFloat()
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (j in 0..s2.length) costs[j] = j
        
        for (i in 1..s1.length) {
            costs[0] = i
            var nw = i - 1
            for (j in 1..s2.length) {
                val cj = min(1 + min(costs[j], costs[j - 1]), if (s1[i - 1] == s2[j - 1]) nw else nw + 1)
                nw = costs[j]
                costs[j] = cj
            }
        }
        return costs[s2.length]
    }
}
