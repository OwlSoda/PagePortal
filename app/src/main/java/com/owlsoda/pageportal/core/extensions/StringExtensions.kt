package com.owlsoda.pageportal.core.extensions

import com.google.gson.Gson

/**
 * Utility extensions for String parsing and metadata cleanup.
 */

private val gson = Gson()

/**
 * Parses an author string that might be a JSON array or a semicolon/comma separated string.
 * Returns a clean list of individual author names.
 */
fun String?.parseAuthors(): List<String> {
    if (this.isNullOrBlank()) return emptyList()
    
    // Try to parse as JSON array first
    return try {
        val parsed = gson.fromJson(this, Array<String>::class.java)
        if (parsed != null) {
            parsed.flatMap { it.split(";", ",").map { name -> name.trim() } }
                .filter { it.isNotEmpty() }
        } else {
            fallbackSplit(this)
        }
    } catch (e: Exception) {
        fallbackSplit(this)
    }
}

/**
 * Parses a tags string that might be a JSON array or a semicolon/comma separated string.
 * Returns a clean list of individual tags.
 */
fun String?.parseTags(): List<String> {
    if (this.isNullOrBlank()) return emptyList()
    
    return try {
        val parsed = gson.fromJson(this, Array<String>::class.java)
        if (parsed != null) {
            parsed.flatMap { it.split(";", ",").map { tag -> tag.trim() } }
                .filter { it.isNotEmpty() }
        } else {
            fallbackSplit(this)
        }
    } catch (e: Exception) {
        fallbackSplit(this)
    }
}

/**
 * Escapes a string for safe interpolation into JavaScript string literals.
 * Prevents JS injection when used with evaluateJavascript().
 */
fun String.escapeForJs(): String {
    return this
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\u0000", "")
        .replace("<", "\\x3c")  // Prevent </script> breakout
        .replace(">", "\\x3e")
}

private fun fallbackSplit(input: String): List<String> {
    // If it starts with [ and ends with ], it might be a malformed/escaped JSON string
    // but we can try to strip them as a last resort if parsing failed
    val clean = if (input.startsWith("[") && input.endsWith("]")) {
        input.substring(1, input.length - 1).replace("\"", "")
    } else {
        input
    }
    
    return clean.split(";", ",").map { it.trim() }.filter { it.isNotEmpty() }
}
