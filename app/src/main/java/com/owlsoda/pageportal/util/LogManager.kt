package com.owlsoda.pageportal.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Robust centralized logging to a file for diagnostics.
 */
object LogManager {
    private const val LOG_FILE_NAME = "app_debug.log"
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    private lateinit var applicationContext: Context

    /**
     * Initialize LogManager with application context.
     * Must be called in Application.onCreate()
     */
    fun init(context: Context) {
        this.applicationContext = context.applicationContext
    }

    fun log(tag: String, message: String, throwable: Throwable? = null) {
        try {
            if (!::applicationContext.isInitialized) {
                android.util.Log.e("LogManager", "LogManager NOT INITIALIZED. Message: $message")
                return
            }
            val logFile = getLogFile()
            val timestamp = timestampFormat.format(Date())
            val logEntry = StringBuilder()
                .append("[$timestamp] ")
                .append("[$tag] ")
                .append(scrubSensitiveData(message))
                .append("\n")
            
            throwable?.let {
                logEntry.append(android.util.Log.getStackTraceString(it)).append("\n")
            }
            
            logFile.appendText(logEntry.toString())
            
            // Also log to logcat for developer convenience
            android.util.Log.d(tag, message, throwable)
        } catch (e: Exception) {
            android.util.Log.e("LogManager", "Failed to write to log file", e)
        }
    }

    fun getLogFile(): File {
        return File(applicationContext.filesDir, LOG_FILE_NAME).apply {
            if (!exists()) {
                createNewFile()
            }
        }
    }

    fun clearLogs() {
        try {
            val logFile = getLogFile()
            if (logFile.exists()) {
                logFile.writeText("") // Clear content instead of deleting to keep file handle friendly
            }
        } catch (e: Exception) {
            android.util.Log.e("LogManager", "Failed to clear logs", e)
        }
    }

    fun readLogs(maxChars: Int = 100_000): String {
        return try {
            val logFile = getLogFile()
            if (!logFile.exists() || logFile.length() == 0L) return "No logs found."
            
            val content = logFile.readText()
            if (content.length > maxChars) {
                "... [truncated] ...\n" + content.takeLast(maxChars)
            } else {
                content
            }
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    /**
     * Scrubs sensitive data (tokens, passwords, auth headers) from log messages.
     */
    private fun scrubSensitiveData(message: String): String {
        return message
            .replace(Regex("token=[^&\\s]+"), "token=REDACTED")
            .replace(Regex("Bearer\\s+[^\\s,}\"]+"), "Bearer REDACTED")
            .replace(Regex("Basic\\s+[^\\s,}\"]+"), "Basic REDACTED")
            .replace(Regex("Authorization:\\s*[^\\s,}\"]+"), "Authorization: REDACTED")
            .replace(Regex("password[=:]\\s*[^\\s,}&\"]+", RegexOption.IGNORE_CASE), "password=REDACTED")
    }
}
